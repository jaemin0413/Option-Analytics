package com.option.domain.model.calculator

import com.option.domain.market.vo.OptionType
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

// 지난 구간은 IV가 없어서 옵션 종가로 역산
object BlackScholes {
    private const val MIN_VOL = 0.0001
    private const val MAX_VOL = 5.0
    private const val ITERATIONS = 60

    fun price(type: OptionType, spot: Double, strike: Double, years: Double, rate: Double, vol: Double): Double {
        val d1 = (ln(spot / strike) + (rate + vol * vol / 2) * years) / (vol * sqrt(years))
        val d2 = d1 - vol * sqrt(years)
        val discount = exp(-rate * years)
        return when (type) {
            OptionType.CALL -> spot * normalCdf(d1) - strike * discount * normalCdf(d2)
            OptionType.PUT -> strike * discount * normalCdf(-d2) - spot * normalCdf(-d1)
        }
    }

    fun impliedVol(type: OptionType, target: Double, spot: Double, strike: Double, years: Double, rate: Double): Double? {
        if (years <= 0 || target <= 0) return null
        var low = MIN_VOL
        var high = MAX_VOL
        val discounted = strike * exp(-rate * years)
        val intrinsic = if (type == OptionType.CALL) max(0.0, spot - discounted) else max(0.0, discounted - spot)
        if (target < intrinsic || target > price(type, spot, strike, years, rate, high)) return null
        repeat(ITERATIONS) {
            val mid = (low + high) / 2
            if (price(type, spot, strike, years, rate, mid) < target) low = mid else high = mid
        }
        return (low + high) / 2
    }

    private fun normalCdf(x: Double): Double {
        if (x < 0) return 1 - normalCdf(-x)
        val t = 1 / (1 + 0.2316419 * x)
        val pdf = exp(-x * x / 2) / sqrt(2 * Math.PI)
        return 1 - pdf * t * (0.319381530 + t * (-0.356563782 + t * (1.781477937 + t * (-1.821255978 + t * 1.330274429))))
    }
}
