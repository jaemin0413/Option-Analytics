package com.option.global.security.annotation

import io.swagger.v3.oas.annotations.Parameter

// userId 파싱용 어노테이션
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Parameter(hidden = true)
annotation class UserId
