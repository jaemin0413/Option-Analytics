package com.option.domain.model.vo

import java.time.LocalDate

// 시장심리지수 지표 그냥 보관용이라 jsonb로
data class SentimentComponent(
    val name: String,
    val value: Double?,
    val percentile: Double?,
    val asOf: LocalDate?,
    val used: Boolean,
)
