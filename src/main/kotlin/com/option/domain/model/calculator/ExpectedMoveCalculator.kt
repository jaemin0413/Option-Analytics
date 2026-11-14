package com.option.domain.model.calculator

import kotlin.math.abs
import kotlin.math.sqrt

data class AtmIv(val iv: Double, val strike: Double, val fallback: Boolean)

// 예상 변동범위
object ExpectedMoveCalculator {
    private const val TRADING_DAYS = 252.0
    private const val FALLBACK_STRIKES = 2

    // ATM 행사가의 콜과 풋 IV를 평균내어 계산, 없으면 그 위아래 값을 사용한다.
    fun atmIv(rows: List<OptionRow>, spot: Double): AtmIv? {
        val strikes = rows.map { it.strike }.distinct().sorted()
        if (strikes.isEmpty()) return null
        val atm = strikes.indices.minBy { abs(strikes[it] - spot) }
        val candidates = strikes
            .subList((atm - FALLBACK_STRIKES).coerceAtLeast(0), (atm + FALLBACK_STRIKES + 1).coerceAtMost(strikes.size))
            .sortedBy { abs(it - spot) }
        candidates.forEach { strike ->
            val ivs = rows.filter { it.strike == strike }.mapNotNull { it.iv }.filter { it > 0 }
            if (ivs.isNotEmpty()) {
                return AtmIv(ivs.average(), strike, strike != strikes[atm] || ivs.size < 2)
            }
        }
        return null
    }

    fun width(spot: Double, iv: Double, tradingDays: Int): Double = spot * iv * sqrt(tradingDays / TRADING_DAYS)
}
