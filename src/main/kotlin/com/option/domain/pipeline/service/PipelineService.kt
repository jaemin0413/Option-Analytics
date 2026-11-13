package com.option.domain.pipeline.service

import com.option.domain.collection.service.JobRunner
import com.option.domain.market.service.TradingCalendarService
import com.option.domain.market.util.MarketTime
import com.option.domain.model.service.PublishService
import com.option.domain.collection.job.Job
import com.option.domain.pipeline.job.SnapshotJob
import com.option.domain.pipeline.job.StockJob
import com.option.global.apiPayLoad.error.CollectionErrorCode
import com.option.global.apiPayLoad.exception.BusinessException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.TaskExecutor
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate

// 작업을 어느 스레드에서 돌릴지 결정하는 코드
@Service
class PipelineService(
    private val jobRunner: JobRunner,
    private val publishService: PublishService,
    private val tradingCalendarService: TradingCalendarService,
    @Qualifier("backgroundExecutor") private val backgroundExecutor: TaskExecutor,
    @Qualifier("manualExecutor") private val manualExecutor: TaskExecutor,
    private val clock: Clock,
) {
    // 예약실행
    fun runNow(job: Job, session: LocalDate) {
        jobRunner.execute(jobRunner.start(job, session))
    }

    // 수동실행
    fun runManual(job: Job, requested: LocalDate?) {
        val session = requested ?: defaultSession(job)
        if (!job.background && !tradingCalendarService.calendar().isSession(session)) {
            throw BusinessException(CollectionErrorCode.NOT_TRADING_SESSION, mapOf("session" to session))
        }
        val context = jobRunner.start(job, session)
        val executor = if (job.background) backgroundExecutor else manualExecutor
        executor.execute { jobRunner.execute(context) }
    }

    fun publish(session: LocalDate) = publishService.publish(session)

    private fun defaultSession(job: Job): LocalDate {
        val today = clock.instant().atZone(MarketTime.ET).toLocalDate()
        if (job.background) return today
        val closed = tradingCalendarService.closeAt(today)?.let { clock.instant().isAfter(it) } == true
        if (closed && (job is StockJob || job is SnapshotJob)) return today
        return tradingCalendarService.calendar().prev(today) ?: today
    }
}
