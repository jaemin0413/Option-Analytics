package com.option.domain.journal.repository

import com.option.domain.journal.entity.Journal
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface JournalRepository : JpaRepository<Journal, Long> {
    fun findByUserIdAndSymbolAndTargetSession(userId: Long, symbol: String, targetSession: LocalDate): Journal?

    fun findByTargetSession(targetSession: LocalDate): List<Journal>

    fun findByUserIdOrderByTargetSessionDescSymbolAsc(userId: Long, pageable: Pageable): List<Journal>

    fun deleteByUserId(userId: Long)
}
