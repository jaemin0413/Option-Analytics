package com.option.domain.model.calculator

import com.option.domain.market.vo.OptionType
import com.option.domain.model.vo.ExpiryQuality
import java.time.LocalDate
import kotlin.math.abs

data class GexResult(
    val total: Double,
    val contracts: Int,
    val missingContracts: Int,
    val contractMissingRate: Double,
    val oiMissingRate: Double,
    val callOiMissingRate: Double,
    val putOiMissingRate: Double,
    val atmMissing: Boolean,
    val official: Boolean,
    val byStrike: Map<Double, Double>,
    val byExpiry: Map<LocalDate, ExpiryQuality>,
)


object GexCalculator {
    private const val MULTIPLIER = 100.0
    private const val STRIKE_BAND = 0.15
    private const val MIN_CONTRACTS = 20
    private const val MAX_OI_MISSING = 0.15
    private const val ATM_STRIKES = 2

    fun contribution(row: OptionRow, spot: Double): Double? {
        val gamma = row.gamma ?: return null
        val value = gamma * (row.oi ?: 0L) * MULTIPLIER * spot * spot * 0.01
        return if (row.type == OptionType.CALL) value else -value
    }

    fun compute(rows: List<OptionRow>, spot: Double, base: LocalDate, until: LocalDate?): GexResult {
        val scope = rows.filter { row ->
            val afterBase = row.expiry.isAfter(base)
            val inRange = until == null || (!row.expiry.isAfter(until) && abs(row.strike - spot) <= spot * STRIKE_BAND)
            afterBase && inRange
        }
        val missing = scope.filter { it.gamma == null }
        val strikeContributions = mutableListOf<Pair<Double, Double>>()
        for (row in scope) {
            val value = contribution(row, spot)
            if (value != null) {
                strikeContributions.add(row.strike to value)
            }
        }
        val byStrike = strikeContributions
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.sum() }
        val oiMissingRate = oiMissing(scope)
        return GexResult(
            total = byStrike.values.sum(),
            contracts = scope.size,
            missingContracts = missing.size,
            contractMissingRate = if (scope.isEmpty()) 0.0 else missing.size.toDouble() / scope.size,
            oiMissingRate = oiMissingRate,
            callOiMissingRate = oiMissing(scope.filter { it.type == OptionType.CALL }),
            putOiMissingRate = oiMissing(scope.filter { it.type == OptionType.PUT }),
            atmMissing = atmMissing(scope, spot),
            official = scope.size >= MIN_CONTRACTS && scope.sumOf { it.oi ?: 0L } > 0 && oiMissingRate <= MAX_OI_MISSING,
            byStrike = byStrike.toSortedMap(),
            byExpiry = scope.groupBy { it.expiry }.toSortedMap().mapValues { (_, list) ->
                ExpiryQuality(list.size, list.count { it.gamma == null }, oiMissing(list))
            },
        )
    }

    private fun oiMissing(rows: List<OptionRow>): Double {
        val total = rows.sumOf { it.oi ?: 0L }
        if (total == 0L) return 0.0
        return rows.filter { it.gamma == null }.sumOf { it.oi ?: 0L }.toDouble() / total
    }

    private fun atmMissing(rows: List<OptionRow>, spot: Double): Boolean {
        val strikes = rows.map { it.strike }.distinct().sorted()
        if (strikes.isEmpty()) return false
        val atm = strikes.indices.minBy { abs(strikes[it] - spot) }
        val start = (atm - ATM_STRIKES).coerceAtLeast(0)
        val end = (atm + ATM_STRIKES + 1).coerceAtMost(strikes.size)
        val near = strikes.subList(start, end).toSet()
        return rows.any { it.strike in near && it.gamma == null }
    }
}
