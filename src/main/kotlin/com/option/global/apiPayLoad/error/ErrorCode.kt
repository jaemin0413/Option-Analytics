package com.option.global.apiPayLoad.error

import org.springframework.http.HttpStatus

// 기본 에러코드
interface ErrorCode {
    val httpStatus: HttpStatus
    val code: String
    val message: String
}
