package com.option.domain.user.dto.request

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SignUpRequest(
    @field:NotBlank
    @field:Email
    @field:Size(max = 255)
    val email: String,
    // BCrypt는 72바이트까지만 보니까 그보다 길게 받지 않는다.
    @field:Size(min = 8, max = 64)
    val password: String,
)
