package com.option.domain.model.repository

import com.option.domain.model.entity.HypothesisSignal
import com.option.global.common.setDoubleOrNull
import com.option.global.common.setInstant
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Clock

// 백필 가설 신호(약 50만 행)를 jpa의 saveAll 대신 한 번에 모아서 보내는 용도
// 컬럼 추가 시 HypothesisSignal과 이 SQL을 같이 수정
@Repository
class HypothesisSignalBatchRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val clock: Clock,
) {
    fun insertAll(rows: List<HypothesisSignal>) {
        if (rows.isEmpty()) return
        val now = clock.instant()
        jdbcTemplate.batchUpdate(SQL, rows, 1000) { ps, r ->
            var i = 0
            ps.setLong(++i, r.publicationId)
            ps.setString(++i, r.symbol)
            ps.setString(++i, r.hypothesis)
            ps.setString(++i, r.horizon.name)
            ps.setObject(++i, r.targetSession)
            ps.setString(++i, r.signal.name)
            ps.setDoubleOrNull(++i, r.inputValue)
            ps.setInstant(++i, now)
            ps.setInstant(++i, now)
        }
    }

    companion object {
        private const val SQL = """
            insert into hypothesis_signal (
                publication_id, symbol, hypothesis, horizon, target_session, signal, input_value, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """
    }
}
