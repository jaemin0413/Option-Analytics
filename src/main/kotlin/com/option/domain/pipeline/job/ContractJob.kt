package com.option.domain.pipeline.job

import com.option.domain.collection.service.BootstrapCollector
import com.option.domain.collection.service.ContractCollector
import com.option.domain.collection.service.ExternalSourceCollector
import com.option.domain.collection.service.JobRunner
import com.option.domain.collection.service.RunContext
import com.option.domain.collection.vo.DiskRule
import com.option.domain.market.util.MarketTime
import com.option.domain.collection.job.Job
import org.springframework.stereotype.Component
import java.time.Clock

// 거래일 달력 맞추고 계약 받아옴
@Component
class ContractJob(
    private val bootstrapCollector: BootstrapCollector,
    private val contractCollector: ContractCollector,
    private val externalSourceCollector: ExternalSourceCollector,
    private val jobRunner: JobRunner,
    private val clock: Clock,
) : Job {
    override val background = false
    override val diskRule = DiskRule.COLLECTION

    override fun run(context: RunContext) {
        val today = clock.instant().atZone(MarketTime.KST).toLocalDate()
        jobRunner.isolated(context, "CALENDAR") { bootstrapCollector.syncCalendar(context, today) }
        contractCollector.collect(context)
        externalSourceCollector.collect(context, today)
    }
}
