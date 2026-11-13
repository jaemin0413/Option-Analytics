package com.option.domain.pipeline.scheduler

import com.option.domain.market.service.TradingCalendarService
import com.option.domain.market.util.MarketTime
import com.option.domain.pipeline.job.ContractJob
import com.option.domain.collection.job.Job
import com.option.domain.pipeline.job.PublishJob
import com.option.domain.pipeline.job.SnapshotJob
import com.option.domain.pipeline.job.StockJob
import com.option.domain.pipeline.service.PipelineService
import com.option.global.apiPayLoad.exception.BusinessException
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate

// 하루 시간표
@Component
@ConditionalOnProperty("scheduling.enabled", havingValue = "true", matchIfMissing = true)
class PipelineScheduler(
    private val pipelineService: PipelineService,
    private val stockJob: StockJob,
    private val snapshotJob: SnapshotJob,
    private val contractJob: ContractJob,
    private val publishJob: PublishJob,
    private val tradingCalendarService: TradingCalendarService,
    private val clock: Clock,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    // 미국 4시반에 주식 일봉 받아오기
    @Scheduled(cron = "0 30 16 * * MON-FRI", zone = "America/New_York")
    fun runStockBars() = runForEtSession(stockJob)

    // 미국 5시에 옵션 스냅샷 받아오기
    @Scheduled(cron = "0 0 17 * * MON-FRI", zone = "America/New_York")
    fun runSnapshots() = runForEtSession(snapshotJob)

    // 한국 5시에 미결제약정 받아오기
    @Scheduled(cron = "0 0 17 * * MON-FRI", zone = "Asia/Seoul")
    fun runContracts() = runForBaseSession(contractJob)

    // 한국 7시반에 입력확정
    @Scheduled(cron = "0 30 19 * * MON-FRI", zone = "Asia/Seoul")
    fun runPublishCompute() = runForBaseSession(publishJob)

    @Scheduled(cron = "0 45 20 * * MON-FRI", zone = "Asia/Seoul")
    fun runPublish() {
        val base = baseSessionForKstToday() ?: return
        guarded("publish") { pipelineService.publish(base) }
    }

    private fun runForEtSession(job: Job) {
        val session = etSessionToday() ?: return
        guarded(job.name) { pipelineService.runNow(job, session) }
    }

    private fun runForBaseSession(job: Job) {
        val base = baseSessionForKstToday() ?: return
        guarded(job.name) { pipelineService.runNow(job, base) }
    }

    private fun etSessionToday(): LocalDate? {
        val today = clock.instant().atZone(MarketTime.ET).toLocalDate()
        return if (tradingCalendarService.calendar().isSession(today)) today else null
    }

    private fun baseSessionForKstToday(): LocalDate? {
        val today = clock.instant().atZone(MarketTime.KST).toLocalDate()
        val calendar = tradingCalendarService.calendar()
        return if (calendar.isSession(today)) calendar.prev(today) else null
    }

    private fun guarded(job: String, body: () -> Unit) {
        try {
            body()
        } catch (e: BusinessException) {
            logger.warn("{} 실행 안 함: {}", job, e.message)
        }
    }
}
