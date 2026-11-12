package com.option.domain.model.calculator

import com.option.domain.market.util.SessionCalendar
import com.option.domain.model.vo.SentimentComponent
import java.time.LocalDate

data class Observation(val date: LocalDate, val value: Double)

data class SentimentResult(val score: Double?, val usedBitset: Int, val usedCount: Int, val components: List<SentimentComponent>)

data class SentimentSeries(val name: String, val observations: List<Observation>, val reversed: Boolean)

// 시장심리지수 계산
object SentimentCalculator {
    const val MAX_DELAY_SESSIONS = 3
    private const val DISTRIBUTION_YEARS = 3L
    private const val MIN_DISTRIBUTION = 700

    fun compute(session: LocalDate, calendar: SessionCalendar, series: List<SentimentSeries>): SentimentResult {
        val components = series.map { component(session, calendar, it) }
        val used = components.filter { it.used }
        var bitset = 0
        components.forEachIndexed { index, component -> if (component.used) bitset = bitset or (1 shl index) }
        val percentiles = used.mapNotNull { it.percentile }
        return SentimentResult(
            score = if (percentiles.isEmpty()) null else percentiles.average(),
            usedBitset = bitset,
            usedCount = used.size,
            components = components,
        )
    }

    private fun component(session: LocalDate, calendar: SessionCalendar, series: SentimentSeries): SentimentComponent {
        val latest = series.observations.lastOrNull { !it.date.isAfter(session) }
            ?: return SentimentComponent(series.name, null, null, null, false)
        if (calendar.countBetween(latest.date, session) > MAX_DELAY_SESSIONS) {
            return SentimentComponent(series.name, latest.value, null, latest.date, false)
        }
        val from = latest.date.minusYears(DISTRIBUTION_YEARS)
        val prior = series.observations.filter { it.date.isBefore(latest.date) && it.date.isAfter(from) }
        if (prior.size < MIN_DISTRIBUTION) {
            return SentimentComponent(series.name, latest.value, null, latest.date, false)
        }
        val percentile = prior.count { it.value <= latest.value } * 100.0 / prior.size
        return SentimentComponent(
            series.name,
            latest.value,
            if (series.reversed) 100 - percentile else percentile,
            latest.date,
            true,
        )
    }

    fun ratioToMovingAverage(values: List<Observation>, window: Int): List<Observation> =
        values.windowed(window).map { part ->
            val last = part.last()
            val average = part.map { it.value }.average()
            Observation(last.date, last.value / average - 1)
        }

    fun movingAverage(values: List<Observation>, window: Int): List<Observation> =
        values.windowed(window).map { part -> Observation(part.last().date, part.map { it.value }.average()) }

    fun returns(values: List<Observation>, days: Int): List<Observation> =
        values.windowed(days + 1).map { part -> Observation(part.last().date, part.last().value / part.first().value - 1) }

    fun mcClellanSummation(net: List<Observation>): List<Observation> {
        var fast = net.firstOrNull()?.value ?: return emptyList()
        var slow = fast
        var summation = 0.0
        return net.map { obs ->
            fast += (obs.value - fast) * (2.0 / 20)
            slow += (obs.value - slow) * (2.0 / 40)
            summation += fast - slow
            Observation(obs.date, summation)
        }
    }
}
