package com.option.domain.journal.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Date
import java.time.LocalDate

data class JournalResult(
    val symbol: String,
    val targetSession: LocalDate,
    val hit: Boolean?,
    val actualReturn: Double?,
)

@Repository
class JournalResultQueryRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    // 채점은 model 패키지가 score 테이블에 남김. model 클래스를 쓰면 패키지 순환이 생겨서 테이블을 직접 읽음
    // 'journal'은 ScoreSubjects.JOURNAL과 같은 값
    fun find(userId: Long, sessions: Collection<LocalDate>): List<JournalResult> {
        if (sessions.isEmpty()) return emptyList()
        return jdbcTemplate.query(
            """
            SELECT symbol, target_session, hit, actual_return
            FROM score
            WHERE user_id = ? AND subject = 'journal' AND target_session IN (${sessions.joinToString { "?" }})
            """,
            { rs, _ ->
                JournalResult(
                    rs.getString("symbol"),
                    rs.getDate("target_session").toLocalDate(),
                    rs.getObject("hit") as Boolean?,
                    rs.getObject("actual_return") as Double?,
                )
            },
            userId,
            *sessions.map(Date::valueOf).toTypedArray(),
        )
    }
}
