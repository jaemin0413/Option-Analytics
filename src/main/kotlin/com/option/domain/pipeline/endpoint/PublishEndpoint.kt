package com.option.domain.pipeline.endpoint

import com.option.domain.pipeline.service.PipelineService
import com.option.global.apiPayLoad.exception.BusinessException
import org.springframework.boot.actuate.endpoint.InvalidEndpointRequestException
import org.springframework.boot.actuate.endpoint.annotation.Endpoint
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation
import org.springframework.stereotype.Component
import java.time.LocalDate

// 발표확정 수작업으로
@Component
@Endpoint(id = "publish")
class PublishEndpoint(
    private val pipelineService: PipelineService,
) {
    @WriteOperation
    fun publish(session: String): String {
        try {
            pipelineService.publish(LocalDate.parse(session))
        } catch (e: BusinessException) {
            throw InvalidEndpointRequestException(e.message ?: "발표할 수 없습니다", e.errorCode.code)
        }
        return "$session 발표"
    }
}
