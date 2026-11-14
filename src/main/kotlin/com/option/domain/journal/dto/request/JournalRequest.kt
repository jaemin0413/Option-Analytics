package com.option.domain.journal.dto.request

import com.option.domain.journal.vo.JournalDirection
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDate

data class JournalRequest(
    @field:NotBlank
    @field:Size(max = 16)
    val symbol: String,
    val targetSession: LocalDate,
    val direction: JournalDirection,
    @field:Size(max = 500)
    val memo: String? = null,
)
