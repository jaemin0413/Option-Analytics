package com.option.domain.collection.repository

import com.option.domain.collection.entity.RawFile
import com.option.global.common.setInstant
import com.option.global.common.setStringOrNull
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Clock

// 원본 파일 기록을 jpa의 saveAll 대신 한 번에 모아서 보내는 용도. 백필만 약 7만 건이다.
// 컬럼 추가 시 RawFile과 이 SQL을 같이 수정
@Repository
class RawFileBatchRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val clock: Clock,
) {
    fun insertAll(rows: List<RawFile>) {
        if (rows.isEmpty()) return
        val now = clock.instant()
        jdbcTemplate.batchUpdate(SQL, rows, 1000) { ps, r ->
            var i = 0
            ps.setString(++i, r.job)
            ps.setObject(++i, r.sessionDate)
            ps.setString(++i, r.endpoint)
            ps.setString(++i, r.symbol)
            ps.setInt(++i, r.page)
            ps.setString(++i, r.path)
            ps.setString(++i, r.sha256)
            ps.setLong(++i, r.bytes)
            ps.setStringOrNull(++i, r.requestParams)
            ps.setInstant(++i, r.receivedAt)
            ps.setInstant(++i, now)
            ps.setInstant(++i, now)
        }
    }

    companion object {
        private const val SQL = """
            insert into raw_file (
                job, session_date, endpoint, symbol, page, path, sha256, bytes, request_params, received_at, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """
    }
}
