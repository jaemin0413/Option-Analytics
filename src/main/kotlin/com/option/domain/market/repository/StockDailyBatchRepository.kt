package com.option.domain.market.repository

import com.option.domain.market.entity.StockDaily
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.PreparedStatement
import java.sql.Types
import java.time.Clock
import java.time.ZoneOffset

// 부트스트랩의 주식 과거 일봉(약 50만 행)을 jpa의 saveAll 대신 한 번에 모아서 보내는 용도
// 컬럼 추가 시 StockDaily와 이 SQL을 같이 수정
@Repository
class StockDailyBatchRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val clock: Clock,
) {

    fun insertAll(rows: List<StockDaily>) {
        if (rows.isEmpty()) return
        val now = clock.instant().atOffset(ZoneOffset.UTC)
        jdbcTemplate.batchUpdate(SQL, rows, 1000) { ps, r ->
            var i = 0
            ps.setString(++i, r.symbol)
            ps.setObject(++i, r.sessionDate)
            ps.setString(++i, r.feed)
            ps.setDouble(++i, r.openPrice)
            ps.setDouble(++i, r.highPrice)
            ps.setDouble(++i, r.lowPrice)
            ps.setDouble(++i, r.closeRaw)
            ps.setDouble(++i, r.closeAdj)
            ps.setLong(++i, r.volume)
            ps.setLng(++i, r.tradeCount)
            ps.setDbl(++i, r.vwap)
            ps.setObject(++i, r.receivedAt.atOffset(ZoneOffset.UTC))
            ps.setObject(++i, now)
            ps.setObject(++i, now)
        }
    }

    // null 타입을 직접 줘야 드라이버가 타입을 물으러 DB에 다시 가지 않는다
    private fun PreparedStatement.setDbl(i: Int, v: Double?) = if (v == null) setNull(i, Types.DOUBLE) else setDouble(i, v)
    private fun PreparedStatement.setLng(i: Int, v: Long?) = if (v == null) setNull(i, Types.BIGINT) else setLong(i, v)

    companion object {
        private const val SQL = """
            insert into stock_daily (
                symbol, session_date, feed, open_price, high_price, low_price, close_raw, close_adj, volume, trade_count, vwap,
                received_at, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """
    }
}
