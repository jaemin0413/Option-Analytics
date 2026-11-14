package com.option.domain.market.entity

import com.option.domain.market.vo.OptionType
import com.option.global.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

// 종목 각각에서 체결된 계약 하나
@Entity
@Table(name = "option_daily")
class OptionDaily(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "contract_symbol", nullable = false)
    val contractSymbol: String,

    @Column(nullable = false)
    val underlying: String,

    @Column(name = "expiration_date", nullable = false)
    val expirationDate: LocalDate,

    @Column(nullable = false)
    val strike: Double,

    @Enumerated(EnumType.STRING)
    @Column(name = "option_type", nullable = false)
    val optionType: OptionType,

    @Column(name = "session_date", nullable = false)
    val sessionDate: LocalDate,

    @Column(nullable = false)
    val provider: String,

    @Column(nullable = false)
    val feed: String,

    @Column(name = "received_at", nullable = false)
    val receivedAt: Instant,

    @Column(name = "bar_date")
    val barDate: LocalDate? = null,

    @Column(name = "open_price")
    val openPrice: Double? = null,

    @Column(name = "high_price")
    val highPrice: Double? = null,

    @Column(name = "low_price")
    val lowPrice: Double? = null,

    @Column(name = "close_price")
    val closePrice: Double? = null,

    val volume: Long? = null,

    @Column(name = "trade_count")
    val tradeCount: Long? = null,

    val vwap: Double? = null,

    val bid: Double? = null,

    val ask: Double? = null,

    @Column(name = "last_price")
    val lastPrice: Double? = null,

    @Column(name = "implied_vol")
    val impliedVol: Double? = null,

    val delta: Double? = null,

    val gamma: Double? = null,

    val theta: Double? = null,

    val vega: Double? = null,

    val rho: Double? = null,

    @Column(name = "price_as_of")
    val priceAsOf: Instant? = null,

    @Column(name = "open_interest")
    var openInterest: Long? = null,

    @Column(name = "oi_as_of_date")
    var oiAsOfDate: LocalDate? = null,

    @Column(name = "oi_delay_days")
    var oiDelayDays: Int? = null,
) : BaseEntity()
