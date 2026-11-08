package com.option.domain.user.dto.request

import jakarta.validation.constraints.NotBlank

data class ReissueRequest(
    @field:NotBlank
    val refreshToken: String,
)
