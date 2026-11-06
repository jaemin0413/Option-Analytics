package com.option.global.apiPayLoad.exception

import com.option.global.apiPayLoad.error.ErrorCode

class BusinessException(
    val errorCode: ErrorCode,
    val detail: Map<String, Any?>? = null,
) : RuntimeException(errorCode.message)
