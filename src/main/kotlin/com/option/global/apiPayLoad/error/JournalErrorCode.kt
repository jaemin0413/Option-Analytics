package com.option.global.apiPayLoad.error

import org.springframework.http.HttpStatus

enum class JournalErrorCode(
    override val httpStatus: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    JOURNAL_CLOSED(HttpStatus.CONFLICT, "R001", "정규장이 시작되어 저널을 수정할 수 없습니다."),
    JOURNAL_NOT_TRADING_SESSION(HttpStatus.BAD_REQUEST, "R002", "대상 세션이 거래일이 아닙니다."),
}
