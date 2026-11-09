package com.option.domain.market.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import java.util.TreeSet

// 거래일 계산용
class SessionCalendar(sessions: Collection<LocalDate>) {
    private val sessions = TreeSet(sessions)

    fun isSession(date: LocalDate): Boolean = date in sessions

    fun next(date: LocalDate): LocalDate? = sessions.higher(date)

    fun prev(date: LocalDate): LocalDate? = sessions.lower(date)

    fun weekEnd(date: LocalDate): LocalDate? {
        val friday = date.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
        val last = sessions.floor(friday) ?: return null
        return if (sameWeek(last, date)) last else null
    }

    // from 다음 거래일부터 to까지 거래일이 몇 번 있었나 세는 코드
    fun countBetween(from: LocalDate, to: LocalDate): Int =
        if (!to.isAfter(from)) 0 else sessions.subSet(from, false, to, true).size

    private fun sameWeek(a: LocalDate, b: LocalDate) =
        a.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) == b.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) &&
            a.get(IsoFields.WEEK_BASED_YEAR) == b.get(IsoFields.WEEK_BASED_YEAR)

    companion object {
        // 월간 옵션 만기
        fun thirdFriday(year: Int, month: Int): LocalDate =
            LocalDate.of(year, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.FRIDAY))

        // after 이후에 오는 첫 월간 만기 기준일
        fun nextMonthly(after: LocalDate): LocalDate {
            val thisMonth = thirdFriday(after.year, after.monthValue)
            if (thisMonth.isAfter(after)) return thisMonth
            val next = after.plusMonths(1)
            return thirdFriday(next.year, next.monthValue)
        }
    }
}
