package com.option.domain.pipeline.job

import com.option.domain.collection.service.JobRunner
import com.option.domain.collection.service.RunContext
import com.option.domain.collection.service.StockBarCollector
import com.option.domain.collection.vo.DiskRule
import com.option.domain.market.service.TradingCalendarService
import com.option.domain.model.service.ScoringService
import com.option.domain.collection.job.Job
import org.springframework.stereotype.Component

// 주식 일봉
@Component
class StockJob(
    private val stockBarCollector: StockBarCollector,
    private val scoringService: ScoringService,
    private val tradingCalendarService: TradingCalendarService,
    private val jobRunner: JobRunner,
) : Job {
    override val background = false
    override val diskRule = DiskRule.NONE

    override fun run(context: RunContext) {
        val calendar = tradingCalendarService.calendar()
        val previous = calendar.prev(context.session) ?: context.session.minusDays(1)
        stockBarCollector.collectSession(context, previous)
        jobRunner.isolated(context, "SCORING") { scoringService.scoreSession(context.session) }
    }
}
