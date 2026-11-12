package com.option.domain.collection.service

import com.option.domain.collection.client.AlpacaClient
import com.option.domain.market.entity.MarketSession
import com.option.domain.market.repository.MarketSessionRepository
import com.option.domain.market.service.TradingCalendarService
import com.option.domain.market.util.MarketTime
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalTime

// 초기적재
@Service
class BootstrapCollector(
    private val alpacaClient: AlpacaClient,
    private val marketSessionRepository: MarketSessionRepository,
    private val tradingCalendarService: TradingCalendarService,
    private val externalSourceCollector: ExternalSourceCollector,
    private val stockBarCollector: StockBarCollector,
    private val jobRunner: JobRunner,
) {
    // 달력, 구성종목, 과거 일봉, FRED, CBOE
    fun collect(context: RunContext, today: LocalDate) {
        jobRunner.isolated(context, "CALENDAR") { syncCalendar(context, today) }
        externalSourceCollector.syncInstruments(context, today)
        val calendar = tradingCalendarService.calendar()
        val end = calendar.prev(today) ?: today.minusDays(1)
        val start = today.minusYears(HISTORY_YEARS)
        stockBarCollector.collectHistory(context, start, end)
        externalSourceCollector.collectFred(context, start)
        var date = start
        while (!date.isAfter(end)) {
            if (calendar.isSession(date)) {
                val session = date
                jobRunner.isolated(context, "CBOE:$session") { externalSourceCollector.collectCboePcr(context, session) }
            }
            date = date.plusDays(1)
        }
    }

    // 거래일달력
    fun syncCalendar(context: RunContext, today: LocalDate) {
        val response = alpacaClient.getTrading(
            context,
            "calendar",
            "/v2/calendar",
            mapOf(
                "start" to today.minusYears(HISTORY_YEARS + 1).toString(),
                "end" to today.plusYears(1).toString(),
            ),
        )
        val sessions = alpacaClient.parse(context, response).map { node ->
            val date = LocalDate.parse(node.path("date").asText())
            MarketSession(
                date,
                date.atTime(LocalTime.parse(node.path("open").asText())).atZone(MarketTime.ET).toInstant(),
                date.atTime(LocalTime.parse(node.path("close").asText())).atZone(MarketTime.ET).toInstant(),
            )
        }
        if (sessions.isEmpty()) return
        val responded = sessions.map { it.sessionDate }.toSet()
        val last = responded.max()
        val existing = marketSessionRepository.findAll()

        // 임시 휴장은 뒤늦게 발표된다. 미리 받아 둔 미래 날짜가 응답에서 빠졌으면 지운다.
        val closed = existing.filter { it.sessionDate.isAfter(today) && !it.sessionDate.isAfter(last) && it.sessionDate !in responded }
        if (closed.isNotEmpty()) {
            marketSessionRepository.deleteAll(closed)
            context.addNote("임시 휴장 반영: ${closed.map { it.sessionDate }}")
        }
        val known = existing.map { it.sessionDate }.toSet()
        marketSessionRepository.saveAll(sessions.filter { it.sessionDate !in known })
    }

    private companion object {
        const val HISTORY_YEARS = 4L
    }
}
