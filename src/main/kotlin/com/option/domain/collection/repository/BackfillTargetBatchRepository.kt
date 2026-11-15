package com.option.domain.collection.repository

import com.option.domain.collection.entity.BackfillTarget
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

// 백필 작업 목록도 한 번에 db로 보내는 용도
@Repository
class BackfillTargetBatchRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun insertAll(rows: List<BackfillTarget>) {
        if (rows.isEmpty()) return
        jdbcTemplate.batchUpdate(SQL, rows, 1000) { ps, r ->
            ps.setString(1, r.underlying)
            ps.setInt(2, r.groupIndex)
            ps.setString(3, r.contracts)
        }
    }

    companion object {
        private const val SQL = "insert into backfill_target (underlying, group_index, contracts) values (?, ?, ?)"
    }
}
