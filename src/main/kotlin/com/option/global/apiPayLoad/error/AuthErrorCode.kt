package com.option.global.apiPayLoad.error

import org.springframework.http.HttpStatus

enum class AuthErrorCode(
    override val httpStatus: HttpStatus,
    override val code: String,
    override val message: String,
) : ErrorCode {
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A001", "로그인이 필요합니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "A002", "이메일 또는 비밀번호가 올바르지 않습니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "A003", "이미 가입한 이메일입니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "A004", "토큰이 올바르지 않습니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "A005", "토큰이 만료되었습니다."),
    USER_NOT_FOUND(HttpStatus.UNAUTHORIZED, "A006", "계정을 찾을 수 없습니다."),
    JWT_SECRET_MISSING(HttpStatus.INTERNAL_SERVER_ERROR, "A007", "JWT 시크릿이 설정되지 않았습니다."),
}
