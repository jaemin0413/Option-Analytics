package com.option.domain.journal.dto.response

import com.option.domain.journal.entity.Journal
import com.option.domain.journal.vo.JournalDirection
import java.time.LocalDate

data class JournalResponse(
    val symbol: String,
    val targetSession: LocalDate,
    val direction: JournalDirection,
    val memo: String?,
) {
    companion object {
        fun from(entry: Journal) =
            JournalResponse(entry.symbol, entry.targetSession, entry.direction, entry.memo)
    }
}
