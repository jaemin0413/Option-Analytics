package com.option.domain.model.calculator

import com.option.domain.market.vo.OptionType
import kotlin.math.abs
import kotlin.math.max

data class ConcentrationResult(val maxPain: Double?, val callWall: Double?, val putWall: Double?)

// 옵션 잔고가 몰린 가격대를 찾아 계산
object MaxPainCalculator {

    fun maxPain(rows: List<OptionRow>, spot: Double): Double? {
        val withOi = rows.filter { (it.oi ?: 0L) > 0 }
        if (withOi.isEmpty()) return null
        return rows.map { it.strike }.distinct()
            .sortedBy { abs(it - spot) }
            .minBy { settle -> withOi.sumOf { payout(it, settle) } }
    }

    private fun payout(row: OptionRow, settle: Double): Double {
        val intrinsic = if (row.type == OptionType.CALL) max(0.0, settle - row.strike) else max(0.0, row.strike - settle)
        return intrinsic * (row.oi ?: 0L)
    }

    // 콜은 GEX 기여가 가장 큰 행사가, 풋은 기여가 음수라서 절댓값이 가장 큰 행사가
    fun walls(rows: List<OptionRow>, spot: Double): Pair<Double?, Double?> {
        fun strikeOfMaxAbs(type: OptionType): Double? {
            val strikeContributions = mutableListOf<Pair<Double, Double>>()
            for (row in rows) {
                if (row.type != type) continue
                val value = GexCalculator.contribution(row, spot)
                if (value != null) {
                    strikeContributions.add(row.strike to value)
                }
            }
            val absTotals = strikeContributions
                .groupBy({ it.first }, { it.second })
                .mapValues { abs(it.value.sum()) }
                .filterValues { it > 0 }
            val largest = absTotals.maxByOrNull { it.value } ?: return null
            return largest.key
        }

        return strikeOfMaxAbs(OptionType.CALL) to strikeOfMaxAbs(OptionType.PUT)
    }

    fun compute(rows: List<OptionRow>, spot: Double): ConcentrationResult {
        val (call, put) = walls(rows, spot)
        return ConcentrationResult(maxPain(rows, spot), call, put)
    }
}
