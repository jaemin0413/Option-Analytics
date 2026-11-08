package com.option.domain.user.service

import com.option.domain.user.dto.request.LoginRequest
import com.option.domain.user.dto.request.ReissueRequest
import com.option.domain.user.dto.request.SignUpRequest
import com.option.domain.user.dto.response.TokenResponse
import com.option.domain.user.entity.User
import com.option.domain.user.repository.UserRepository
import com.option.global.apiPayLoad.error.AuthErrorCode
import com.option.global.apiPayLoad.exception.BusinessException
import com.option.global.security.jwt.JwtTokenProvider
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenProvider: JwtTokenProvider,
) {
    // 회원가입
    @Transactional
    fun signUp(request: SignUpRequest) {
        if (userRepository.existsByEmail(request.email)) {
            throw BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS)
        }
        userRepository.save(User(email = request.email, password = passwordEncoder.encode(request.password)))
    }

    // 로그인
    @Transactional
    fun login(request: LoginRequest): TokenResponse {
        val user = userRepository.findByEmail(request.email) ?: throw BusinessException(AuthErrorCode.LOGIN_FAILED)
        if (!passwordEncoder.matches(request.password, user.password)) {
            throw BusinessException(AuthErrorCode.LOGIN_FAILED)
        }
        return issueTokens(user)
    }

    // 로그아웃
    @Transactional
    fun logout(userId: Long) {
        val user = userRepository.findByIdOrNull(userId) ?: throw BusinessException(AuthErrorCode.USER_NOT_FOUND)
        user.refreshToken = null
        userRepository.save(user)
    }

    // 회원탈퇴
    @Transactional
    fun withdraw(userId: Long) {
        val user = userRepository.findByIdOrNull(userId) ?: throw BusinessException(AuthErrorCode.USER_NOT_FOUND)
        userRepository.delete(user)
    }

    // 토큰 재발급
    @Transactional
    fun reissue(request: ReissueRequest): TokenResponse {
        val userId = jwtTokenProvider.getUserIdFromRefreshToken(request.refreshToken)
        val user = userRepository.findByIdOrNull(userId) ?: throw BusinessException(AuthErrorCode.INVALID_TOKEN)
        if (user.refreshToken != request.refreshToken) {
            throw BusinessException(AuthErrorCode.INVALID_TOKEN)
        }
        return issueTokens(user)
    }

    // 토큰 생성
    private fun issueTokens(user: User): TokenResponse {
        val userId = user.id!!
        val accessToken = jwtTokenProvider.createAccessToken(userId)
        val refreshToken = jwtTokenProvider.createRefreshToken(userId)
        user.refreshToken = refreshToken
        userRepository.save(user)
        return TokenResponse(accessToken, refreshToken)
    }
}
