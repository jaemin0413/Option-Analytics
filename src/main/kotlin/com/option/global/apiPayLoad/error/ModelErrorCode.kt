package com.option.global.apiPayLoad.error

import org.springframework.http.HttpStatus

enum class ModelErrorCode(
    override val httpStatus: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    PUBLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "M001", "발표할 결과가 없습니다."),
    ALREADY_PUBLISHED(HttpStatus.CONFLICT, "M002", "이미 발표된 세션입니다."),
    TICKER_NOT_FOUND(HttpStatus.NOT_FOUND, "M003", "종목을 찾을 수 없습니다."),
    CALENDAR_SESSION_MISSING(HttpStatus.INTERNAL_SERVER_ERROR, "M004", "거래일 캘린더에 필요한 세션이 없습니다."),
}
