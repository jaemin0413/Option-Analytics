package com.option.domain.model.service

import com.option.domain.market.repository.BreadthQueryRepository
import com.option.domain.market.repository.MacroDailyRepository
import com.option.domain.market.repository.StockDailyRepository
import com.option.domain.market.vo.MacroSeries
import com.option.domain.market.util.SessionCalendar
import com.option.domain.model.calculator.Observation
import com.option.domain.model.calculator.SentimentCalculator
import com.option.domain.model.calculator.SentimentResult
import com.option.domain.model.calculator.SentimentSeries
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class SentimentService(
    private val stockDailyRepository: StockDailyRepository,
    private val macroDailyRepository: MacroDailyRepository,
    private val breadthQueryRepository: BreadthQueryRepository,
) {

    fun compute(session: LocalDate, calendar: SessionCalendar): SentimentResult {
        val spy = closes("SPY", session)
        val tlt = closes("TLT", session).associateBy { it.date }
        val breadth = breadthQueryRepository.findBreadth().filter { !it.sessionDate.isAfter(session) }

        val strength = breadth.map { Observation(it.sessionDate, (it.newHighs - it.newLows).toDouble()) }
        val net = breadth.map { Observation(it.sessionDate, (it.advancingVolume - it.decliningVolume).toDouble()) }
        val spyReturn = SentimentCalculator.returns(spy, RETURN_DAYS)
        val tltReturn = SentimentCalculator.returns(tlt.values.sortedBy { it.date }, RETURN_DAYS).associateBy { it.date }
        val safeHaven = mutableListOf<Observation>()
        for (spyObservation in spyReturn) {
            val tltObservation = tltReturn[spyObservation.date]
            if (tltObservation != null) {
                safeHaven.add(Observation(spyObservation.date, spyObservation.value - tltObservation.value))
            }
        }

        return SentimentCalculator.compute(
            session,
            calendar,
            listOf(
                SentimentSeries("momentum", SentimentCalculator.ratioToMovingAverage(spy, MOMENTUM_WINDOW), false),
                SentimentSeries("strength", strength, false),
                SentimentSeries("breadth", SentimentCalculator.mcClellanSummation(net), false),
                SentimentSeries("putCall", SentimentCalculator.movingAverage(macro(MacroSeries.CBOE_TOTAL_PCR), PCR_WINDOW), true),
                SentimentSeries("junkBond", macro(MacroSeries.HY_SPREAD), true),
                SentimentSeries("volatility", SentimentCalculator.ratioToMovingAverage(macro(MacroSeries.VIX), VIX_WINDOW), true),
                SentimentSeries("safeHaven", safeHaven, false),
            ),
        )
    }

    private fun closes(symbol: String, session: LocalDate) = stockDailyRepository.findBySymbolOrderBySessionDate(symbol)
        .filter { !it.sessionDate.isAfter(session) }
        .map { Observation(it.sessionDate, it.closeAdj) }

    private fun macro(series: String) = macroDailyRepository.findBySeriesOrderByObsDate(series)
        .map { Observation(it.obsDate, it.value) }

    private companion object {
        const val MOMENTUM_WINDOW = 125
        const val VIX_WINDOW = 50
        const val PCR_WINDOW = 5
        const val RETURN_DAYS = 20
    }
}
