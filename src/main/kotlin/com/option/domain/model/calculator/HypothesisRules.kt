package com.option.domain.model.calculator

import com.option.domain.model.vo.Signal

// 가설 규칙
object HypothesisRules {
    private const val PCR_HIGH = 90.0
    private const val PCR_LOW = 10.0
    private const val MAXPAIN_BAND = 0.25
    const val FLAT_BAND = 0.0015

    fun pcrContrarian(percentile: Double?): Signal = when {
        percentile == null -> Signal.NO_DATA
        percentile >= PCR_HIGH -> Signal.UP
        percentile <= PCR_LOW -> Signal.DOWN
        else -> Signal.ABSTAIN
    }

    fun maxPainPull(spot: Double?, maxPain: Double?, sigma: Double?): Signal {
        if (spot == null || maxPain == null || sigma == null) return Signal.NO_DATA
        val distance = spot - maxPain
        return when {
            distance < -MAXPAIN_BAND * sigma -> Signal.UP
            distance > MAXPAIN_BAND * sigma -> Signal.DOWN
            else -> Signal.ABSTAIN
        }
    }

    fun isHit(prediction: Signal, actualReturn: Double): Boolean? = when (prediction) {
        Signal.UP -> actualReturn > 0
        Signal.DOWN -> actualReturn < 0
        Signal.FLAT -> kotlin.math.abs(actualReturn) <= FLAT_BAND
        else -> null
    }
}
