package com.option.domain.market.entity

import com.option.domain.market.vo.EventType
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

// 시장 이벤트
@Entity
@Table(name = "event_calendar")
class EventCalendar(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "event_date", nullable = false)
    val eventDate: LocalDate,

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    val eventType: EventType,

    @Column(nullable = false)
    val symbol: String,

    val title: String?,

    @Column(nullable = false)
    val source: String,
) : BaseEntity()
