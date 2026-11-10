package com.option.domain.journal.entity

import com.option.domain.journal.vo.JournalDirection
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

// 옵션 보고 상방하방 맞추는 용도
@Entity
@Table(name = "journal")
class Journal(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(nullable = false)
    val symbol: String,

    @Column(name = "target_session", nullable = false)
    val targetSession: LocalDate,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var direction: JournalDirection,

    @Column(length = 500)
    var memo: String?,
) : BaseEntity()
