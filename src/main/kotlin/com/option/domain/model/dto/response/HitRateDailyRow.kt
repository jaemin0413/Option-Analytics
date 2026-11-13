package com.option.domain.model.dto.response

import java.time.LocalDate

data class HitRateDailyRow(
    val targetSession: LocalDate,
    val horizon: String,
    val scored: Long,
    val hits: Long,
)
