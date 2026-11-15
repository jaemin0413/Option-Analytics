package com.option.domain.model.repository

import com.option.domain.model.entity.Score
import com.option.global.common.setBooleanOrNull
import com.option.global.common.setDoubleOrNull
import com.option.global.common.setInstant
import com.option.global.common.setLongOrNull
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Clock

// 채점 결과를 jpa의 saveAll 대신 한 번에 모아서 보내는 용도. 백필 채점은 거래일마다 수천 행이다.
// 컬럼 추가 시 Score와 이 SQL을 같이 수정
@Repository
class ScoreBatchRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val clock: Clock,
) {
    fun insertAll(rows: List<Score>) {
        if (rows.isEmpty()) return
        val now = clock.instant()
        jdbcTemplate.batchUpdate(SQL, rows, 1000) { ps, r ->
            var i = 0
            ps.setLongOrNull(++i, r.publicationId)
            ps.setLongOrNull(++i, r.userId)
            ps.setString(++i, r.symbol)
            ps.setString(++i, r.symbolGroup.name)
            ps.setString(++i, r.subject)
            ps.setString(++i, r.horizon.name)
            ps.setString(++i, r.ivSource.name)
            ps.setObject(++i, r.baseSession)
            ps.setObject(++i, r.targetSession)
            ps.setInt(++i, r.issuedWeekday)
            ps.setString(++i, r.prediction.name)
            ps.setBoolean(++i, r.eventDay)
            ps.setDoubleOrNull(++i, r.actualReturn)
            ps.setBooleanOrNull(++i, r.hit)
            ps.setDoubleOrNull(++i, r.width)
            ps.setDoubleOrNull(++i, r.outsideError)
            ps.setDoubleOrNull(++i, r.distance)
            ps.setDoubleOrNull(++i, r.rangePct)
            ps.setInstant(++i, now)
            ps.setInstant(++i, now)
        }
    }

    companion object {
        private const val SQL = """
            insert into score (
                publication_id, user_id, symbol, symbol_group, subject, horizon, iv_source, base_session, target_session,
                issued_weekday, prediction, event_day, actual_return, hit, width, outside_error, distance, range_pct,
                created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """
    }
}
