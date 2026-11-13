package com.option.domain.pipeline.job

import com.option.domain.collection.service.RunContext
import com.option.domain.collection.service.SnapshotCollector
import com.option.domain.collection.vo.DiskRule
import com.option.domain.collection.job.Job
import org.springframework.stereotype.Component

// 전 종목 스냅샷
@Component
class SnapshotJob(
    private val snapshotCollector: SnapshotCollector,
) : Job {
    override val background = false
    override val diskRule = DiskRule.COLLECTION

    override fun run(context: RunContext) = snapshotCollector.collect(context)
}
