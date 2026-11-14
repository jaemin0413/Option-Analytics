package com.option.domain.market.util

import com.option.domain.market.vo.OptionType
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// 옵션 심볼, SPY261120C00650000은 SPY 2026년 11월 20일 만기 650 콜임
data class OccSymbol(
    val root: String,
    val expiration: LocalDate,
    val type: OptionType,
    val strike: Double,
) {
    fun isStandardFor(underlying: String): Boolean = root == underlying.replace(".", "")

    companion object {
        private val PATTERN = Regex("""^([A-Z0-9]+)(\d{6})([CP])(\d{8})$""")
        private val DATE = DateTimeFormatter.ofPattern("yyMMdd")

        fun parse(symbol: String): OccSymbol? {
            val match = PATTERN.matchEntire(symbol) ?: return null
            val (root, date, callOrPut, strike) = match.destructured
            val type = if (callOrPut == "C") OptionType.CALL else OptionType.PUT
            return OccSymbol(root, LocalDate.parse(date, DATE), type, strike.toLong() / 1000.0)
        }
    }
}
