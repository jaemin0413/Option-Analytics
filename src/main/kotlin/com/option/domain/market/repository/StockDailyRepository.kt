package com.option.domain.market.repository

import com.option.domain.market.entity.StockDaily
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.LocalDate

interface StockDailyRepository : JpaRepository<StockDaily, Long> {
    fun findBySymbolInAndSessionDateBetween(symbols: Collection<String>, from: LocalDate, to: LocalDate): List<StockDaily>

    fun findBySymbolOrderBySessionDate(symbol: String): List<StockDaily>

    fun findBySymbolAndSessionDate(symbol: String, sessionDate: LocalDate): StockDaily?

    fun findBySymbolAndSessionDateLessThanEqualOrderBySessionDateDesc(
        symbol: String,
        sessionDate: LocalDate,
        pageable: Pageable,
    ): List<StockDaily>

    fun findBySessionDateIn(sessions: Collection<LocalDate>): List<StockDaily>
}

data class BreadthRow(
    val sessionDate: LocalDate,
    val newHighs: Long,
    val newLows: Long,
    val advancingVolume: Long,
    val decliningVolume: Long,
)

@Repository
class BreadthQueryRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    // 현재 S&P 500 종목으로 날짜별 52주 신고가, 신저가 수와 상승, 하락 거래량을 센다.
    fun findBreadth(): List<BreadthRow> = jdbcTemplate.query(
        """
        WITH w AS (
            SELECT s.session_date, s.close_adj, s.volume,
                   MAX(s.close_adj) OVER win AS hi,
                   MIN(s.close_adj) OVER win AS lo,
                   COUNT(*) OVER win AS cnt,
                   LAG(s.close_adj) OVER (PARTITION BY s.symbol ORDER BY s.session_date) AS prev
            FROM stock_daily s
            JOIN instrument i ON i.symbol = s.symbol AND i.in_sp500
            WINDOW win AS (PARTITION BY s.symbol ORDER BY s.session_date ROWS BETWEEN 251 PRECEDING AND CURRENT ROW)
        )
        SELECT session_date,
               COUNT(*) FILTER (WHERE cnt = 252 AND close_adj >= hi) AS new_highs,
               COUNT(*) FILTER (WHERE cnt = 252 AND close_adj <= lo) AS new_lows,
               COALESCE(SUM(volume) FILTER (WHERE close_adj > prev), 0) AS adv,
               COALESCE(SUM(volume) FILTER (WHERE close_adj < prev), 0) AS dec
        FROM w
        GROUP BY session_date
        ORDER BY session_date
        """,
    ) { rs, _ ->
        BreadthRow(
            rs.getDate("session_date").toLocalDate(),
            rs.getLong("new_highs"),
            rs.getLong("new_lows"),
            rs.getLong("adv"),
            rs.getLong("dec"),
        )
    }
}
