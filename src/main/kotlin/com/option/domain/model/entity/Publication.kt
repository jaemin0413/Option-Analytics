package com.option.domain.model.entity

import com.option.domain.model.vo.PublicationStatus
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

// 화면에 띄우는 결과물들
@Entity
@Table(name = "publication")
class Publication(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "publication_id")
    val publicationId: Long? = null,

    @Column(name = "session_date", nullable = false)
    val sessionDate: LocalDate,

    @Column(name = "model_version", nullable = false)
    val modelVersion: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: PublicationStatus,

    @Column(name = "published_at")
    var publishedAt: Instant? = null,
) : BaseEntity()
