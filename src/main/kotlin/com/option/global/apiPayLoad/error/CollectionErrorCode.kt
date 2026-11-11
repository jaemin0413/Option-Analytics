package com.option.global.apiPayLoad.error

import org.springframework.http.HttpStatus

enum class CollectionErrorCode(
    override val httpStatus: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    NOT_TRADING_SESSION(HttpStatus.BAD_REQUEST, "J001", "거래일이 아닙니다."),
    HOLDINGS_TOO_FEW(HttpStatus.BAD_GATEWAY, "J002", "SPDR 보유종목이 너무 적게 읽혔습니다."),
}
