package com.option.domain.pipeline.job

import com.option.domain.collection.batch.BackfillLauncher
import com.option.domain.collection.service.RunContext
import com.option.domain.collection.vo.DiskRule
import com.option.domain.collection.job.Job
import org.springframework.stereotype.Component

// 백필용
@Component
class BackfillJob(
    private val backfillLauncher: BackfillLauncher,
) : Job {
    override val background = true
    override val diskRule = DiskRule.BACKGROUND

    override fun run(context: RunContext) = backfillLauncher.launch(context)
}
