package com.option.domain.pipeline.job

import com.option.domain.collection.service.BootstrapCollector
import com.option.domain.collection.service.RunContext
import com.option.domain.collection.vo.DiskRule
import com.option.domain.market.util.MarketTime
import com.option.domain.collection.job.Job
import org.springframework.stereotype.Component
import java.time.Clock

// 초기부팅용
@Component
class BootstrapJob(
    private val bootstrapCollector: BootstrapCollector,
    private val clock: Clock,
) : Job {
    override val background = true
    override val diskRule = DiskRule.BACKGROUND

    override fun run(context: RunContext) =
        bootstrapCollector.collect(context, clock.instant().atZone(MarketTime.ET).toLocalDate())
}
