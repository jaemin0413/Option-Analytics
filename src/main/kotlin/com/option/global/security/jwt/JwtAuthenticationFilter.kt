package com.option.global.security.jwt

import com.fasterxml.jackson.databind.ObjectMapper
import com.option.global.apiPayLoad.error.ErrorCode
import com.option.global.apiPayLoad.exception.BusinessException
import com.option.global.apiPayLoad.response.ApiResponse
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean = request.requestURI in PUBLIC_PATHS

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION)
        // 토큰 없어도 일단 넘김
        if (header == null || !header.startsWith(BEARER)) {
            filterChain.doFilter(request, response)
            return
        }
        try {
            // 헤더에서 id 추출
            val userId = jwtTokenProvider.getUserIdFromAccessToken(header.substring(BEARER.length))
            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(userId, null, emptyList())
        } catch (e: BusinessException) {
            writeError(response, e.errorCode)
            return
        }

        filterChain.doFilter(request, response)
    }

    // 필터에서는 글로벌익셉션핸들러가 안 돌기 때문에 예외 직접 작성
    fun writeError(response: HttpServletResponse, errorCode: ErrorCode) {
        response.status = errorCode.httpStatus.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        objectMapper.writeValue(response.writer, ApiResponse.error(errorCode))
    }

    companion object {
        private const val BEARER = "Bearer "

        val PUBLIC_PATHS = arrayOf("/api/auth/sign-up", "/api/auth/login", "/api/auth/reissue")
    }
}
