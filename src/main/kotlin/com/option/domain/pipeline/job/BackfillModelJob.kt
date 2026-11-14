package com.option.domain.pipeline.job

import com.option.domain.collection.service.RunContext
import com.option.domain.collection.vo.DiskRule
import com.option.domain.model.service.BackfillModelService
import com.option.domain.collection.job.Job
import org.springframework.stereotype.Component

// 한 번만 쓰는 작업이다. 백필로 채운 과거 세션을 계산하고 채점한다.
@Component
class BackfillModelJob(
    private val backfillModelService: BackfillModelService,
) : Job {
    override val background = true
    override val diskRule = DiskRule.NONE

    override fun run(context: RunContext) = backfillModelService.compute(context)
}
