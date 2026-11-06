package com.option.domain.market.entity

import com.option.domain.market.vo.SymbolGroup
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

// 종목
@Entity
@Table(name = "instrument")
class Instrument(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, unique = true)
    val symbol: String,

    var name: String?,

    @Enumerated(EnumType.STRING)
    @Column(name = "symbol_group", nullable = false)
    var symbolGroup: SymbolGroup,

    @Column(name = "in_sp500", nullable = false)
    var inSp500: Boolean,

    @Column(name = "as_of_date")
    var asOfDate: LocalDate?,
) : BaseEntity()
