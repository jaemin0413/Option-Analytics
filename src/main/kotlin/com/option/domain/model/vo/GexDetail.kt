package com.option.domain.model.vo

data class ExpiryQuality(val contracts: Int, val missingContracts: Int, val oiMissingRate: Double)

// 화면 차트와 결측 분석에 쓰는 값, 행사가가 너무 많아 그냥 josnb로 저장
data class GexDetail(
    val byStrike: Map<String, Double> = emptyMap(),
    val byExpiry: Map<String, ExpiryQuality> = emptyMap(),
    val callOiMissingRate: Double? = null,
    val putOiMissingRate: Double? = null,
)
