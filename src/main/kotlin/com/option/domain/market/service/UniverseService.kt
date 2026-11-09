package com.option.domain.market.service

import com.option.domain.market.entity.Instrument
import com.option.domain.market.repository.InstrumentRepository
import com.option.domain.market.vo.SymbolGroup
import com.option.global.properties.UniverseProperties
import org.springframework.stereotype.Service
import java.time.LocalDate

// 수집 대상 관리
@Service
class UniverseService(
    private val instrumentRepository: InstrumentRepository,
    private val universeProperties: UniverseProperties,
) {
    // 무슨 종목을 수집할지
    fun symbols(): List<String> {
        val only = universeProperties.only.filter { it.isNotBlank() }
        if (only.isNotEmpty()) return only
        return instrumentRepository.findAll()
            .filter { it.inSp500 || it.symbolGroup != SymbolGroup.STOCK }
            .map { it.symbol }
            .sorted()
    }

    // 설정을 통해 주식 종류 분류
    fun groupOf(symbol: String): SymbolGroup = when (symbol) {
        in universeProperties.indexEtfs -> SymbolGroup.INDEX_ETF
        in universeProperties.sectorEtfs -> SymbolGroup.SECTOR_ETF
        in universeProperties.otherEtfs -> SymbolGroup.OTHER_ETF
        else -> SymbolGroup.STOCK
    }

    // snp 500 관리
    fun syncSp500(holdings: Map<String, String>, asOf: LocalDate) {
        val existing = instrumentRepository.findAll().associateBy { it.symbol }
        existing.values.filter { it.inSp500 && it.symbol !in holdings }.forEach { it.inSp500 = false }
        holdings.forEach { (symbol, name) ->
            val instrument = existing[symbol]
            if (instrument == null) {
                instrumentRepository.save(Instrument(symbol = symbol, name = name, symbolGroup = groupOf(symbol), inSp500 = true, asOfDate = asOf))
            } else {
                instrument.inSp500 = true
                instrument.name = name
                instrument.asOfDate = asOf
            }
        }
        instrumentRepository.saveAll(existing.values)
    }

    fun seedEtfs() {
        val universe = universeProperties
        val existing = instrumentRepository.findAll().map { it.symbol }.toSet()
        val etfs = (universe.indexEtfs + universe.sectorEtfs + universe.otherEtfs).filter { it !in existing }
        instrumentRepository.saveAll(etfs.map { Instrument(symbol = it, name = null, symbolGroup = groupOf(it), inSp500 = false, asOfDate = null) })
    }
}
