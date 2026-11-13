package com.option.domain.pipeline.job

import com.option.domain.collection.service.RunContext
import com.option.domain.collection.vo.DiskRule
import com.option.domain.model.service.PublishService
import com.option.domain.collection.job.Job
import org.springframework.stereotype.Component

// 지표 계산
@Component
class PublishJob(
    private val publishService: PublishService,
) : Job {
    override val background = false
    override val diskRule = DiskRule.NONE

    override fun run(context: RunContext) = publishService.compute(context)
}
