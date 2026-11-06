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
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate

// 옵션 보고 상방하방 맞추는 용도, 종목당 하나
@Entity
@Table(
    name = "journal_entry",
    uniqueConstraints = [UniqueConstraint(name = "uk_journal", columnNames = ["symbol", "target_session"])],
)
class JournalEntry(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

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
