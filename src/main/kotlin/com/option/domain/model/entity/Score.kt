package com.option.domain.model.entity

import com.option.domain.market.vo.SymbolGroup
import com.option.domain.model.vo.Horizon
import com.option.domain.model.vo.IvSource
import com.option.domain.model.vo.Signal
import com.option.global.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate

// 예측 점수
@Entity
@Table(name = "score")
class Score(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "publication_id")
    val publicationId: Long?,

    // 사용자 확인용
    @Column(name = "user_id")
    val userId: Long? = null,

    @Column(nullable = false)
    val symbol: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "symbol_group", nullable = false)
    val symbolGroup: SymbolGroup,

    @Column(nullable = false)
    val subject: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val horizon: Horizon,

    @Enumerated(EnumType.STRING)
    @Column(name = "iv_source", nullable = false)
    val ivSource: IvSource,

    @Column(name = "base_session", nullable = false)
    val baseSession: LocalDate,

    @Column(name = "target_session", nullable = false)
    val targetSession: LocalDate,

    @Column(name = "issued_weekday", nullable = false)
    val issuedWeekday: Int,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val prediction: Signal,

    @Column(name = "event_day", nullable = false)
    val eventDay: Boolean,

    @Column(name = "actual_return")
    val actualReturn: Double? = null,

    val hit: Boolean? = null,

    val width: Double? = null,

    @Column(name = "outside_error")
    val outsideError: Double? = null,

    val distance: Double? = null,

    @Column(name = "range_pct")
    val rangePct: Double? = null,
) : BaseEntity()
