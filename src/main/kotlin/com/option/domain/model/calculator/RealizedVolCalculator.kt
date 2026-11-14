package com.option.domain.model.calculator

import kotlin.math.ln
import kotlin.math.sqrt

// 기준선
object RealizedVolCalculator {
    private const val WINDOW = 20

    fun dailyStdev(closes: List<Double>): Double? {
        if (closes.size < WINDOW + 1) return null
        val returns = closes.takeLast(WINDOW + 1).zipWithNext { a, b -> ln(b / a) }
        val mean = returns.average()
        return sqrt(returns.sumOf { (it - mean) * (it - mean) } / (returns.size - 1))
    }

    fun width(spot: Double, dailyStdev: Double, tradingDays: Int): Double = spot * dailyStdev * sqrt(tradingDays.toDouble())
}
