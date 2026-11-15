package com.option.domain.collection.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

// 백필할 계약 묶음
@Entity
@Table(name = "backfill_target")
class BackfillTarget(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "backfill_target_id")
    val id: Long? = null,

    @Column(nullable = false)
    val underlying: String,

    @Column(name = "group_index", nullable = false)
    val groupIndex: Int,

    @Column(nullable = false, columnDefinition = "text")
    val contracts: String,
)
