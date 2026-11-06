package com.option.global.apiPayLoad.exception

import com.option.global.apiPayLoad.error.CommonErrorCode
import com.option.global.apiPayLoad.response.ApiResponse
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.ErrorResponse
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    // 서비스가 던진 BusinessException, 에러코드에 적힌 상태와 메시지로 응답
    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(e: BusinessException): ResponseEntity<ApiResponse<Unit>> {
        logger.warn("Business exception: [{}] {}", e.errorCode.code, e.message)
        return ResponseEntity.status(e.errorCode.httpStatus).body(ApiResponse.error(e.errorCode, e.detail))
    }

    // @Valid 검증 실패
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Unit>> {
        val detail = e.bindingResult.fieldErrors.associate { it.field to it.defaultMessage }
        return ResponseEntity.badRequest().body(ApiResponse.error(CommonErrorCode.INVALID_REQUEST, detail))
    }

    // 요청 자체가 잘못된 경우
    @ExceptionHandler(
        HttpMessageNotReadableException::class,
        MissingServletRequestParameterException::class,
        MethodArgumentTypeMismatchException::class,
    )
    fun handleBadRequest(e: Exception): ResponseEntity<ApiResponse<Unit>> {
        logger.warn("Bad request: {}", e.message)
        return ResponseEntity.badRequest().body(ApiResponse.error(CommonErrorCode.INVALID_REQUEST))
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<ApiResponse<Unit>> {
        if (e is ErrorResponse && e.statusCode.is4xxClientError) {
            logger.warn("Client error: {} {}", e.statusCode.value(), e.message)
            val errorCode = when (e.statusCode.value()) {
                404 -> CommonErrorCode.NOT_FOUND
                405 -> CommonErrorCode.METHOD_NOT_ALLOWED
                else -> CommonErrorCode.INVALID_REQUEST
            }
            return ResponseEntity.status(e.statusCode).body(ApiResponse.error(errorCode))
        }
        logger.error("Unexpected error", e)
        return ResponseEntity.internalServerError().body(ApiResponse.error(CommonErrorCode.INTERNAL_ERROR))
    }
}
