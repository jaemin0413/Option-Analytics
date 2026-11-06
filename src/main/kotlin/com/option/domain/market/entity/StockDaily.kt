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

// 일일 주가
@Entity
@Table(name = "stock_daily")
class StockDaily(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    val symbol: String,

    @Column(name = "session_date", nullable = false)
    val sessionDate: LocalDate,

    @Column(nullable = false)
    val feed: String,

    @Column(name = "open_price", nullable = false)
    val openPrice: Double,

    @Column(name = "high_price", nullable = false)
    val highPrice: Double,

    @Column(name = "low_price", nullable = false)
    val lowPrice: Double,

    @Column(name = "close_raw", nullable = false)
    val closeRaw: Double,

    @Column(name = "close_adj", nullable = false)
    var closeAdj: Double,

    @Column(nullable = false)
    val volume: Long,

    @Column(name = "trade_count")
    val tradeCount: Long?,

    val vwap: Double?,

    @Column(name = "received_at", nullable = false)
    val receivedAt: Instant,
) : BaseEntity()
