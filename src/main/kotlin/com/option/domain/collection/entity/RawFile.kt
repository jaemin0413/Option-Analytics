package com.option.domain.collection.entity

import com.option.domain.collection.vo.JobType
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

// 원본파일 위치 저장용
@Entity
@Table(name = "raw_file")
class RawFile(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "raw_file_id")
    val rawFileId: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val job: JobType,

    @Column(name = "session_date", nullable = false)
    val sessionDate: LocalDate,

    @Column(nullable = false)
    val endpoint: String,

    @Column(nullable = false)
    val symbol: String,

    @Column(nullable = false)
    val page: Int,

    @Column(nullable = false, length = 400)
    val path: String,

    @Column(nullable = false)
    val sha256: String,

    @Column(nullable = false)
    val bytes: Long,

    @Column(name = "request_params", columnDefinition = "text")
    val requestParams: String?,

    @Column(name = "received_at", nullable = false)
    val receivedAt: Instant,
) : BaseEntity()
