package com.option.domain.model.calculator

import com.option.domain.market.vo.OptionType

// 풋콜 비율
object PcrCalculator {
    const val HISTORY = 252

    fun volume(rows: List<OptionRow>): Double? = ratio(rows) { it.volume }

    fun openInterest(rows: List<OptionRow>): Double? = ratio(rows) { it.oi }

    private fun ratio(rows: List<OptionRow>, value: (OptionRow) -> Long?): Double? {
        val calls = rows.filter { it.type == OptionType.CALL }.sumOf { value(it) ?: 0L }
        val puts = rows.filter { it.type == OptionType.PUT }.sumOf { value(it) ?: 0L }
        return if (calls == 0L) null else puts.toDouble() / calls
    }

    fun percentile(value: Double, history: List<Double>): Double? {
        if (history.size < HISTORY) return null
        return history.count { it <= value } * 100.0 / history.size
    }
}
