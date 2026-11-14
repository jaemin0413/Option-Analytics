package com.option.domain.model.calculator

import com.option.domain.market.util.SessionCalendar
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

// 만기 선택
object ExpiryPicker {
    fun nearest(expiries: Collection<LocalDate>, base: LocalDate): LocalDate? =
        expiries.filter { it.isAfter(base) }.minOrNull()

    // 주간 만기
    fun weekly(expiries: Collection<LocalDate>, base: LocalDate, target: LocalDate): LocalDate? {
        val friday = target.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
        val monday = target.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return expiries.filter { it.isAfter(base) && !it.isAfter(friday) && !it.isBefore(monday) }.maxOrNull()
    }

    // 월간 만기
    fun monthly(expiries: Collection<LocalDate>, base: LocalDate): LocalDate? {
        val third = SessionCalendar.nextMonthly(base)
        return expiries.filter { it.isAfter(base) && !it.isAfter(third) && !it.isBefore(third.minusDays(4)) }.maxOrNull()
    }
}
