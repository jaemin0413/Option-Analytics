package com.option.domain.collection.batch

import com.option.domain.collection.service.RunContext
import com.option.domain.market.service.TradingCalendarService
import com.option.domain.market.util.MarketTime
import com.option.global.apiPayLoad.error.CollectionErrorCode
import com.option.global.apiPayLoad.exception.BusinessException
import com.option.global.properties.JobProperties
import org.slf4j.LoggerFactory
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParameters
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.explore.JobExplorer
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.core.repository.JobRepository
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDateTime

// 백필용 잡을 띄우고, 실패한 게 있다면 거기서부터 다시 띄움
@Service
class BackfillLauncher(
    private val jobLauncher: JobLauncher,
    private val jobExplorer: JobExplorer,
    private val jobRepository: JobRepository,
    private val backfillBatchJob: Job,
    private val backfillRun: BackfillRun,
    private val tradingCalendarService: TradingCalendarService,
    private val jobProperties: JobProperties,
    private val clock: Clock,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun launch(context: RunContext) {
        markKilledRunsFailed()
        val parameters = unfinishedParameters() ?: newParameters()
        backfillRun.begin(context, parameters)

        repeat(RETRIES + 1) { attempt ->
            if (attempt > 0) {
                logger.warn("백필 실패, {} 뒤 이어서 다시 돈다 ({}/{})", jobProperties.backfillRetryWait, attempt, RETRIES)
                Thread.sleep(jobProperties.backfillRetryWait.toMillis())
            }
            // 같은 파라미터로 다시 부르면 Spring Batch가 마지막으로 커밋된 청크 다음부터 이어 간다
            val execution = jobLauncher.run(backfillBatchJob, parameters)
            if (execution.status == BatchStatus.COMPLETED) return
            execution.allFailureExceptions.forEach { logger.error("백필 실패 attempt={}", attempt, it) }
        }
        throw BusinessException(CollectionErrorCode.BACKFILL_FAILED)
    }

    // 앱 죽으면 실행기록이 started로 남아서 재시작이 막힘. 새로 띄우는데 started면 장애가 발생했던 실행미
    private fun markKilledRunsFailed() {
        val now = LocalDateTime.now(clock)
        jobExplorer.findRunningJobExecutions(BackfillBatchConfig.JOB_NAME).forEach { execution ->
            execution.stepExecutions.filter { it.status.isRunning }.forEach { step ->
                step.status = BatchStatus.FAILED
                step.setExitStatus(ExitStatus.FAILED)
                step.setEndTime(now)
                jobRepository.update(step)
            }
            execution.status = BatchStatus.FAILED
            execution.setExitStatus(ExitStatus.FAILED)
            execution.setEndTime(now)
            jobRepository.update(execution)
            logger.warn("강제 종료된 백필 실행 {}를 FAILED로 바꿈", execution.id)
        }
    }

    // 안 끝난 백필 있으면 파라미터 재사용, 끝난 백필을 다시 돌리는 행위는 막음
    private fun unfinishedParameters(): JobParameters? {
        val instance = jobExplorer.getLastJobInstance(BackfillBatchConfig.JOB_NAME) ?: return null
        val last = jobExplorer.getLastJobExecution(instance) ?: return null
        if (last.status == BatchStatus.COMPLETED) throw BusinessException(CollectionErrorCode.BACKFILL_ALREADY_COMPLETED)
        return last.jobParameters
    }

    private fun newParameters(): JobParameters {
        val today = clock.instant().atZone(MarketTime.ET).toLocalDate()
        val end = tradingCalendarService.calendar().prev(today) ?: today.minusDays(1)
        return JobParametersBuilder()
            .addLocalDate(BackfillRun.AS_OF, today)
            .addLocalDate(BackfillRun.END, end)
            .toJobParameters()
    }

    private companion object {
        const val RETRIES = 3
    }
}
