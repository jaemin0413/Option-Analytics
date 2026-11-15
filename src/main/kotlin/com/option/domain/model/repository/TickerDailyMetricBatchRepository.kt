package com.option.domain.model.repository

import com.fasterxml.jackson.databind.ObjectMapper
import com.option.domain.model.entity.TickerDailyMetric
import com.option.global.common.setBooleanOrNull
import com.option.global.common.setDateOrNull
import com.option.global.common.setDoubleOrNull
import com.option.global.common.setInstant
import com.option.global.common.setIntOrNull
import com.option.global.common.setStringOrNull
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Clock

// 백필 지표(거래일 x 종목, 약 25만 행)를 jpa의 save 대신 한 번에 모아서 보내는 용도
// 컬럼 추가 시 TickerDailyMetric과 이 SQL을 같이 수정
@Repository
class TickerDailyMetricBatchRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) {
    fun insertAll(rows: List<TickerDailyMetric>) {
        if (rows.isEmpty()) return
        val now = clock.instant()
        jdbcTemplate.batchUpdate(SQL, rows, 1000) { ps, r ->
            var i = 0
            ps.setLong(++i, r.publicationId)
            ps.setString(++i, r.symbol)
            ps.setObject(++i, r.baseSession)
            ps.setObject(++i, r.targetSession)
            ps.setObject(++i, r.weekTargetSession)
            ps.setDoubleOrNull(++i, r.baseClose)
            ps.setDateOrNull(++i, r.oiAsOfDate)
            ps.setIntOrNull(++i, r.oiDelayDays)
            ps.setDoubleOrNull(++i, r.dayIv)
            ps.setDateOrNull(++i, r.dayIvExpiry)
            ps.setBooleanOrNull(++i, r.dayIvFallback)
            ps.setDoubleOrNull(++i, r.dayWidth)
            ps.setDoubleOrNull(++i, r.weekIv)
            ps.setDateOrNull(++i, r.weekIvExpiry)
            ps.setBooleanOrNull(++i, r.weekIvFallback)
            ps.setDoubleOrNull(++i, r.weekWidth)
            ps.setDoubleOrNull(++i, r.dayRvWidth)
            ps.setDoubleOrNull(++i, r.weekRvWidth)
            ps.setDateOrNull(++i, r.maxpainExpiry)
            ps.setDoubleOrNull(++i, r.maxpainStrike)
            ps.setDoubleOrNull(++i, r.maxpainNearestStrike)
            ps.setDoubleOrNull(++i, r.callWallStrike)
            ps.setDoubleOrNull(++i, r.putWallStrike)
            ps.setDateOrNull(++i, r.monthlyExpiry)
            ps.setDoubleOrNull(++i, r.monthlyMaxpainStrike)
            ps.setDoubleOrNull(++i, r.monthlyCallWallStrike)
            ps.setDoubleOrNull(++i, r.monthlyPutWallStrike)
            ps.setDoubleOrNull(++i, r.gexWeekly)
            ps.setDoubleOrNull(++i, r.gexMonthly)
            ps.setDoubleOrNull(++i, r.gexAll)
            ps.setIntOrNull(++i, r.gexContracts)
            ps.setDoubleOrNull(++i, r.gexContractMissingRate)
            ps.setDoubleOrNull(++i, r.gexOiMissingRate)
            ps.setBooleanOrNull(++i, r.gexAtmMissing)
            ps.setBoolean(++i, r.gexOfficial)
            ps.setStringOrNull(++i, r.gexDetail?.let { objectMapper.writeValueAsString(it) })
            ps.setDoubleOrNull(++i, r.pcrVolume)
            ps.setDoubleOrNull(++i, r.pcrOi)
            ps.setDoubleOrNull(++i, r.pcrVolumeWeek)
            ps.setDoubleOrNull(++i, r.pcrOiWeek)
            ps.setDoubleOrNull(++i, r.pcrPercentile)
            ps.setBoolean(++i, r.dayEvent)
            ps.setBoolean(++i, r.weekEvent)
            ps.setInstant(++i, now)
            ps.setInstant(++i, now)
        }
    }

    companion object {
        private const val SQL = """
            insert into ticker_daily_metric (
                publication_id, symbol, base_session, target_session, week_target_session, base_close, oi_as_of_date, oi_delay_days,
                day_iv, day_iv_expiry, day_iv_fallback, day_width, week_iv, week_iv_expiry, week_iv_fallback, week_width,
                day_rv_width, week_rv_width, maxpain_expiry, maxpain_strike, maxpain_nearest_strike, call_wall_strike, put_wall_strike,
                monthly_expiry, monthly_maxpain_strike, monthly_call_wall_strike, monthly_put_wall_strike,
                gex_weekly, gex_monthly, gex_all, gex_contracts, gex_contract_missing_rate, gex_oi_missing_rate, gex_atm_missing,
                gex_official, gex_detail, pcr_volume, pcr_oi, pcr_volume_week, pcr_oi_week, pcr_percentile, day_event, week_event,
                created_at, updated_at
            ) values (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?
            )
        """
    }
}
