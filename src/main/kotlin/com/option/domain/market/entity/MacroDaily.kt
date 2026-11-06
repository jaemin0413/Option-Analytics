package com.option.domain.market.entity

import com.option.global.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate


// vix나 하이일드 스프레드, 국채금리 등 매크로
@Entity
@Table(name = "macro_daily")
class MacroDaily(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    val series: String,

    @Column(name = "obs_date", nullable = false)
    val obsDate: LocalDate,

    @Column(nullable = false)
    val value: Double,

    @Column(nullable = false)
    val provider: String,

    @Column(name = "received_at", nullable = false)
    val receivedAt: Instant,
) : BaseEntity()
