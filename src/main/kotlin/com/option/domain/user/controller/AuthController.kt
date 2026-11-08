package com.option.domain.user.controller

import com.option.domain.user.dto.request.LoginRequest
import com.option.domain.user.dto.request.ReissueRequest
import com.option.domain.user.dto.request.SignUpRequest
import com.option.domain.user.dto.response.TokenResponse
import com.option.domain.user.service.AuthService
import com.option.global.apiPayLoad.response.ApiResponse
import com.option.global.security.annotation.UserId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "로그인 API")
class AuthController(
    private val authService: AuthService,
) {
    @PostMapping("/sign-up")
    @Operation(summary = "회원가입", description = "새로운 회원 등록")
    fun signUp(@Valid @RequestBody request: SignUpRequest): ApiResponse<Unit> {
        authService.signUp(request)
        return ApiResponse.ok()
    }

    @PostMapping("/login")
    @Operation(summary = "로그인", description = "로그인시 엑세스 토큰과 리프레쉬 토큰을 발급")
    fun login(@Valid @RequestBody request: LoginRequest): ApiResponse<TokenResponse> =
        ApiResponse.ok(authService.login(request))

    @PostMapping("/reissue")
    @Operation(summary = "토큰 재발급", description = "리프레쉬토큰으로 새로운 토큰들 발급")
    fun reissue(@Valid @RequestBody request: ReissueRequest): ApiResponse<TokenResponse> =
        ApiResponse.ok(authService.reissue(request))

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "db의 리프레쉬 토큰을 무효화")
    fun logout(@UserId userId: Long): ApiResponse<Unit> {
        authService.logout(userId)
        return ApiResponse.ok()
    }

    @DeleteMapping("/withdraw")
    @Operation(summary = "회원탈퇴", description = "회원 삭제")
    fun withdraw(@UserId userId: Long): ApiResponse<Unit> {
        authService.withdraw(userId)
        return ApiResponse.ok()
    }
}
