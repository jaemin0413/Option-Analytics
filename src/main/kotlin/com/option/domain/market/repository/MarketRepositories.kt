package com.option.domain.market.repository

import com.option.domain.market.entity.EventCalendar
import com.option.domain.market.entity.Instrument
import com.option.domain.market.entity.MacroDaily
import com.option.domain.market.entity.MarketSession
import com.option.domain.market.vo.EventType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate

interface InstrumentRepository : JpaRepository<Instrument, Long> {
    fun findBySymbol(symbol: String): Instrument?

    fun findTop20BySymbolStartingWithOrderBySymbol(prefix: String): List<Instrument>
}

interface MarketSessionRepository : JpaRepository<MarketSession, LocalDate>

interface MacroDailyRepository : JpaRepository<MacroDaily, Long> {
    @Query("select m.obsDate from MacroDaily m where m.series = :series")
    fun findDates(series: String): List<LocalDate>

    fun findBySeriesOrderByObsDate(series: String): List<MacroDaily>

    fun findFirstBySeriesAndObsDateLessThanEqualOrderByObsDateDesc(series: String, date: LocalDate): MacroDaily?
}

interface EventCalendarRepository : JpaRepository<EventCalendar, Long> {
    fun existsByEventDateAndEventTypeAndSymbol(eventDate: LocalDate, eventType: EventType, symbol: String): Boolean

    fun findByEventDateBetweenOrderByEventDate(from: LocalDate, to: LocalDate): List<EventCalendar>

    fun findByEventDateBetweenAndSymbolInOrderByEventDate(
        from: LocalDate,
        to: LocalDate,
        symbols: Collection<String>,
    ): List<EventCalendar>
}
