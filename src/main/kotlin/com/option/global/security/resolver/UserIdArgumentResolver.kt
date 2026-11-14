package com.option.global.security.resolver

import com.option.global.apiPayLoad.error.AuthErrorCode
import com.option.global.apiPayLoad.exception.BusinessException
import com.option.global.security.annotation.UserId
import org.springframework.core.MethodParameter
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

// 필터에서 시큐리티컨텍스트의 userId 파싱
@Component
class UserIdArgumentResolver : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.hasParameterAnnotation(UserId::class.java) && parameter.parameterType == Long::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Any {
        return SecurityContextHolder.getContext().authentication?.principal as? Long
            ?: throw BusinessException(AuthErrorCode.UNAUTHORIZED)
    }
}
