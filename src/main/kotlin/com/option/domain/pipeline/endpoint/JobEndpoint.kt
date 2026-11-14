package com.option.domain.pipeline.endpoint

import com.option.domain.collection.job.Job
import com.option.domain.pipeline.service.PipelineService
import com.option.global.apiPayLoad.exception.BusinessException
import org.springframework.boot.actuate.endpoint.InvalidEndpointRequestException
import org.springframework.boot.actuate.endpoint.annotation.Endpoint
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation
import org.springframework.boot.actuate.endpoint.annotation.Selector
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation
import org.springframework.lang.Nullable
import org.springframework.stereotype.Component
import java.time.LocalDate

// 한번쓰고버릴거 대비용
@Component
@Endpoint(id = "jobs")
class JobEndpoint(
    jobs: List<Job>,
    private val pipelineService: PipelineService,
) {
    private val byName = jobs.associateBy { it.name }

    @ReadOperation
    fun list(): Set<String> = byName.keys.toSortedSet()

    // 이름으로 작업 찾아서 돌리기
    @WriteOperation
    fun run(@Selector name: String, @Nullable session: String?): String {
        val job = byName[name] ?: throw InvalidEndpointRequestException("없는 작업입니다: $name", "unknown job")
        val date = if (session != null) LocalDate.parse(session) else null
        try {
            pipelineService.runManual(job, date)
        } catch (e: BusinessException) {
            throw InvalidEndpointRequestException(e.message ?: "실행할 수 없습니다", e.errorCode.code)
        }
        return "$name 시작"
    }
}
