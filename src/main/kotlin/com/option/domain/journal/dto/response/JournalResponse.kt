package com.option.domain.journal.dto.response

import com.option.domain.journal.entity.Journal
import com.option.domain.journal.repository.JournalResult
import com.option.domain.journal.vo.JournalDirection
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

data class JournalResponse(
    val symbol: String,
    val targetSession: LocalDate,
    val direction: JournalDirection,
    val memo: String?,
    @Schema(description = "적중 여부. 채점 전이거나 HOLD면 null")
    val hit: Boolean? = null,
    @Schema(description = "기준일 종가 대비 대상일 종가 수익률. 채점 전이면 null")
    val actualReturn: Double? = null,
) {
    companion object {
        fun from(entry: Journal, result: JournalResult? = null) =
            JournalResponse(entry.symbol, entry.targetSession, entry.direction, entry.memo, result?.hit, result?.actualReturn)
    }
}
