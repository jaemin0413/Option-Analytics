package com.option.global.apiPayLoad.error

import org.springframework.http.HttpStatus

enum class CollectionErrorCode(
    override val httpStatus: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    NOT_TRADING_SESSION(HttpStatus.BAD_REQUEST, "J001", "거래일이 아닙니다."),
    HOLDINGS_TOO_FEW(HttpStatus.BAD_GATEWAY, "J002", "SPDR 보유종목이 너무 적게 읽혔습니다."),
    BACKFILL_ALREADY_COMPLETED(HttpStatus.CONFLICT, "J003", "백필이 이미 끝났습니다. 다시 돌리면 행이 중복됩니다."),
    BACKFILL_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "J004", "백필이 재시도 후에도 실패했습니다. 다시 실행하면 실패한 청크부터 이어서 돕니다."),
    BACKFILL_NO_SYMBOLS(HttpStatus.CONFLICT, "J005", "백필할 종목이 없습니다. bootstrap을 먼저 돌리세요."),
}
