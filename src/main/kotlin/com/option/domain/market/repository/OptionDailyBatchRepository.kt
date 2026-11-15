package com.option.domain.market.repository

import com.option.domain.market.entity.OptionDaily
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.PreparedStatement
import java.sql.Types
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

// 데이터 수집시 jpa의 saveAll 대신 한 번에 모아서 보내는 용도
@Repository
class OptionDailyBatchRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val clock: Clock,
) {

    fun insertAll(rows: List<OptionDaily>) {
        if (rows.isEmpty()) return
        val now = clock.instant()
        jdbcTemplate.batchUpdate(SQL, rows, 1000) { ps, r ->
            var i = 0
            ps.setString(++i, r.contractSymbol)
            ps.setString(++i, r.underlying)
            ps.setObject(++i, r.expirationDate)
            ps.setDouble(++i, r.strike)
            ps.setString(++i, r.optionType.name)
            ps.setObject(++i, r.sessionDate)
            ps.setString(++i, r.provider)
            ps.setString(++i, r.feed)
            ps.setTs(++i, r.receivedAt)
            ps.setDate(++i, r.barDate)
            ps.setDbl(++i, r.openPrice)
            ps.setDbl(++i, r.highPrice)
            ps.setDbl(++i, r.lowPrice)
            ps.setDbl(++i, r.closePrice)
            ps.setLng(++i, r.volume)
            ps.setLng(++i, r.tradeCount)
            ps.setDbl(++i, r.vwap)
            ps.setDbl(++i, r.bid)
            ps.setDbl(++i, r.ask)
            ps.setDbl(++i, r.lastPrice)
            ps.setDbl(++i, r.impliedVol)
            ps.setDbl(++i, r.delta)
            ps.setDbl(++i, r.gamma)
            ps.setDbl(++i, r.theta)
            ps.setDbl(++i, r.vega)
            ps.setDbl(++i, r.rho)
            ps.setTs(++i, r.priceAsOf)
            ps.setLng(++i, r.openInterest)
            ps.setDate(++i, r.oiAsOfDate)
            if (r.oiDelayDays == null) ps.setNull(++i, Types.INTEGER) else ps.setInt(++i, r.oiDelayDays!!)
            ps.setTs(++i, now)
            ps.setTs(++i, now)
        }
    }

    private fun PreparedStatement.setDbl(i: Int, v: Double?) = if (v == null) setNull(i, Types.DOUBLE) else setDouble(i, v)
    private fun PreparedStatement.setLng(i: Int, v: Long?) = if (v == null) setNull(i, Types.BIGINT) else setLong(i, v)
    private fun PreparedStatement.setDate(i: Int, v: java.time.LocalDate?) = if (v == null) setNull(i, Types.DATE) else setObject(i, v)
    private fun PreparedStatement.setTs(i: Int, v: Instant?) =
        if (v == null) setNull(i, Types.TIMESTAMP_WITH_TIMEZONE) else setObject(i, v.atOffset(ZoneOffset.UTC))

    companion object {
        private const val SQL = """
            insert into option_daily (
                contract_symbol, underlying, expiration_date, strike, option_type, session_date, provider, feed, received_at,
                bar_date, open_price, high_price, low_price, close_price, volume, trade_count, vwap, bid, ask, last_price,
                implied_vol, delta, gamma, theta, vega, rho, price_as_of, open_interest, oi_as_of_date, oi_delay_days,
                created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """
    }
}
