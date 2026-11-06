package com.option.domain.model.entity

import com.option.domain.model.vo.Horizon
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
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate

// 이론별로 상승 하강 예측
@Entity
@Table(
    name = "hypothesis_signal",
    uniqueConstraints = [UniqueConstraint(name = "uk_signal", columnNames = ["publication_id", "symbol", "hypothesis", "horizon"])],
)
class HypothesisSignal(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "publication_id", nullable = false)
    val publicationId: Long,

    @Column(nullable = false)
    val symbol: String,

    @Column(nullable = false)
    val hypothesis: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val horizon: Horizon,

    @Column(name = "target_session", nullable = false)
    val targetSession: LocalDate,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val signal: Signal,

    @Column(name = "input_value")
    val inputValue: Double?,
) : BaseEntity()
