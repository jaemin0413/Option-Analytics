package com.option.domain.model.calculator

import com.option.domain.market.vo.OptionType
import java.time.LocalDate

// 계산에 쓰이는 계약
data class OptionRow(
    val expiry: LocalDate,
    val strike: Double,
    val type: OptionType,
    val iv: Double? = null,
    val gamma: Double? = null,
    val oi: Long? = null,
    val volume: Long? = null,
    val close: Double? = null,
)
