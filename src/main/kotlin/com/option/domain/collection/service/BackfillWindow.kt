package com.option.domain.collection.service

import com.option.domain.market.util.MarketTime
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalTime

@Component
class BackfillWindow(private val clock: Clock) {
    // 지금이 쉬는 시간대인지 확인
    fun isPaused(): Boolean {
        val now = clock.instant().atZone(MarketTime.KST).toLocalTime()
        return !now.isBefore(LocalTime.of(17, 0)) && now.isBefore(LocalTime.of(21, 0))
    }
}
