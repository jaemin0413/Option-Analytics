package com.option.domain.market.service

import com.option.domain.market.repository.MarketSessionRepository
import com.option.domain.market.util.SessionCalendar
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate

@Service
class TradingCalendarService(
    private val marketSessionRepository: MarketSessionRepository,
) {
    // DB에 있는 거래일로 달력을 만든다.
    fun calendar(): SessionCalendar = SessionCalendar(marketSessionRepository.findAll().map { it.sessionDate })

    // 그 세션의 정규장 시작 시각을 찾는다.
    fun openAt(session: LocalDate): Instant? = marketSessionRepository.findById(session).orElse(null)?.openAt

    // 그 세션의 정규장 마감 시각을 찾는다.
    fun closeAt(session: LocalDate): Instant? = marketSessionRepository.findById(session).orElse(null)?.closeAt
}
