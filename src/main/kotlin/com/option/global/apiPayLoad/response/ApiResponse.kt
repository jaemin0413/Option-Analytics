package com.option.global.apiPayLoad.response

import com.fasterxml.jackson.annotation.JsonInclude
import com.option.global.apiPayLoad.error.ErrorCode

// 공통응답
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiResponse<T>(
    val data: T? = null,
    val error: ErrorResponse? = null,
) {
    companion object {
        fun <T> ok(data: T): ApiResponse<T> = ApiResponse(data = data)

        fun ok(): ApiResponse<Unit> = ApiResponse(data = Unit)

        fun error(errorCode: ErrorCode, detail: Map<String, Any?>? = null): ApiResponse<Unit> =
            ApiResponse(error = ErrorResponse(errorCode.code, errorCode.message, detail))
    }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorResponse(
    val code: String,
    val message: String,
    val detail: Map<String, Any?>? = null,
)
