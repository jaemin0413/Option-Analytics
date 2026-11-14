package com.option.domain.market.entity

import com.option.global.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

// 거래일
@Entity
@Table(name = "market_session")
class MarketSession(
    @Id
    @Column(name = "session_date")
    val sessionDate: LocalDate,

    @Column(name = "open_at", nullable = false)
    val openAt: Instant,

    @Column(name = "close_at", nullable = false)
    val closeAt: Instant,
) : BaseEntity()
