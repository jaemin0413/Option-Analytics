package com.option.domain.model.entity

import com.option.domain.model.vo.SentimentComponent
import com.option.global.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

// 시장 심리지수
@Entity
@Table(name = "market_sentiment")
class MarketSentiment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "publication_id", nullable = false, unique = true)
    val publicationId: Long,

    val score: Double?,

    @Column(name = "used_bitset", nullable = false)
    val usedBitset: Int,

    @Column(name = "used_count", nullable = false)
    val usedCount: Int,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    val components: List<SentimentComponent>,
) : BaseEntity()
