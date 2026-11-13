package com.option.domain.model.service

import com.option.domain.market.vo.SymbolGroup
import com.option.domain.model.dto.response.HitRateDailyRow
import com.option.domain.model.dto.response.HitRateResponse
import com.option.domain.model.repository.HitRateQueryRepository
import org.springframework.stereotype.Service

@Service
class HitRateService(
    private val hitRateQueryRepository: HitRateQueryRepository,
) {

    fun getHitRates(userId: Long, group: SymbolGroup?, issuedWeekday: Int?): HitRateResponse =
        HitRateResponse(
            all = hitRateQueryRepository.find(userId, group, false, issuedWeekday),
            excludingEvents = hitRateQueryRepository.find(userId, group, true, issuedWeekday),
        )

    // 날짜별 적중률
    fun getDailyHitRates(userId: Long, subject: String): List<HitRateDailyRow> = hitRateQueryRepository.findDaily(userId, subject)
}
