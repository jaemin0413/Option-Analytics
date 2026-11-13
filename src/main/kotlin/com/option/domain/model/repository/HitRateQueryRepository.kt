package com.option.domain.model.repository

import com.option.domain.market.vo.SymbolGroup
import com.option.domain.model.dto.response.HitRateDailyRow
import com.option.domain.model.dto.response.HitRateRow
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet

@Repository
class HitRateQueryRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    // 조건에 맞는 적중률 모집
    fun find(userId: Long, group: SymbolGroup?, excludeEvents: Boolean, issuedWeekday: Int?): List<HitRateRow> {
        val conditions = mutableListOf("(user_id IS NULL OR user_id = ?)")
        val args = mutableListOf<Any>(userId)
        if (group != null) {
            conditions += "symbol_group = ?"
            args += group.name
        }
        if (excludeEvents) conditions += "NOT event_day"
        if (issuedWeekday != null) {
            conditions += "issued_weekday = ?"
            args += issuedWeekday
        }
        return jdbcTemplate.query(
            """
            SELECT subject, horizon, iv_source,
                   MIN(target_session) AS start_date,
                   COUNT(*) AS total,
                   COUNT(hit) AS scored,
                   COUNT(*) FILTER (WHERE hit) AS hits,
                   COUNT(*) FILTER (WHERE prediction = 'UP') AS up,
                   COUNT(*) FILTER (WHERE prediction = 'UP' AND hit) AS up_hits,
                   COUNT(*) FILTER (WHERE prediction = 'DOWN') AS down,
                   COUNT(*) FILTER (WHERE prediction = 'DOWN' AND hit) AS down_hits,
                   COUNT(*) FILTER (WHERE prediction = 'ABSTAIN') AS abstain,
                   COUNT(*) FILTER (WHERE prediction = 'NO_DATA') AS no_data,
                   AVG(width) AS avg_width,
                   AVG(outside_error) AS avg_outside_error,
                   AVG(distance) AS avg_distance,
                   AVG(ABS(actual_return)) AS avg_abs_return,
                   AVG(range_pct) AS avg_range_pct
            FROM score
            WHERE ${conditions.joinToString(" AND ")}
            GROUP BY subject, horizon, iv_source
            ORDER BY subject, horizon, iv_source
            """,
            { rs, _ -> toRow(rs) },
            *args.toTypedArray(),
        )
    }

    // 날짜별 집계
    fun findDaily(userId: Long, subject: String): List<HitRateDailyRow> = jdbcTemplate.query(
        """
        SELECT target_session, horizon, COUNT(hit) AS scored, COUNT(*) FILTER (WHERE hit) AS hits
        FROM score
        WHERE subject = ? AND (user_id IS NULL OR user_id = ?)
        GROUP BY target_session, horizon
        ORDER BY target_session, horizon
        """,
        { rs, _ ->
            HitRateDailyRow(
                rs.getDate("target_session").toLocalDate(),
                rs.getString("horizon"),
                rs.getLong("scored"),
                rs.getLong("hits"),
            )
        },
        subject,
        userId,
    )

    private fun toRow(rs: ResultSet) = HitRateRow(
        subject = rs.getString("subject"),
        horizon = rs.getString("horizon"),
        ivSource = rs.getString("iv_source"),
        startDate = rs.getDate("start_date").toLocalDate(),
        total = rs.getLong("total"),
        scored = rs.getLong("scored"),
        hits = rs.getLong("hits"),
        up = rs.getLong("up"),
        upHits = rs.getLong("up_hits"),
        down = rs.getLong("down"),
        downHits = rs.getLong("down_hits"),
        abstain = rs.getLong("abstain"),
        noData = rs.getLong("no_data"),
        avgWidth = rs.getObject("avg_width") as Double?,
        avgOutsideError = rs.getObject("avg_outside_error") as Double?,
        avgDistance = rs.getObject("avg_distance") as Double?,
        avgAbsReturn = rs.getObject("avg_abs_return") as Double?,
        avgRangePct = rs.getObject("avg_range_pct") as Double?,
    )
}
