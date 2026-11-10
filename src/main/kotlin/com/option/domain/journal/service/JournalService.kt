package com.option.domain.journal.service

import com.option.domain.journal.dto.request.JournalRequest
import com.option.domain.journal.dto.response.JournalResponse
import com.option.domain.journal.entity.Journal
import com.option.domain.journal.repository.JournalRepository
import com.option.domain.market.service.TradingCalendarService
import com.option.global.apiPayLoad.error.JournalErrorCode
import com.option.global.apiPayLoad.exception.BusinessException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class JournalService(
    private val journalRepository: JournalRepository,
    private val tradingCalendarService: TradingCalendarService,
    private val clock: Clock,
) {
    // 다음 정규장 열리면 수정 금지
    @Transactional
    fun save(userId: Long, request: JournalRequest): JournalResponse {
        val now = clock.instant()
        val openAt = tradingCalendarService.openAt(request.targetSession)
            ?: throw BusinessException(JournalErrorCode.JOURNAL_NOT_TRADING_SESSION, mapOf("session" to request.targetSession))
        if (!now.isBefore(openAt)) {
            throw BusinessException(JournalErrorCode.JOURNAL_CLOSED, mapOf("session" to request.targetSession))
        }
        val symbol = request.symbol.uppercase()
        val entry = journalRepository.findByUserIdAndSymbolAndTargetSession(userId, symbol, request.targetSession)
        if (entry != null) {
            entry.direction = request.direction
            entry.memo = request.memo
            return JournalResponse.from(entry)
        }
        return JournalResponse.from(
            journalRepository.save(Journal(userId = userId, symbol = symbol, targetSession = request.targetSession, direction = request.direction, memo = request.memo)),
        )
    }

    // 최근 저널
    fun getRecent(userId: Long, limit: Int): List<JournalResponse> =
        journalRepository.findByUserIdOrderByTargetSessionDescSymbolAsc(userId, PageRequest.of(0, limit.coerceIn(1, 500)))
            .map(JournalResponse::from)
}
