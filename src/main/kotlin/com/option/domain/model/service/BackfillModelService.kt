package com.option.domain.model.service

import com.option.domain.collection.service.JobRunner
import com.option.domain.collection.service.RunContext
import com.option.domain.market.entity.OptionDaily
import com.option.domain.market.repository.MacroDailyRepository
import com.option.domain.market.repository.OptionDailyRepository
import com.option.domain.market.repository.StockDailyRepository
import com.option.domain.market.service.TradingCalendarService
import com.option.domain.market.vo.Feeds
import com.option.domain.market.vo.MacroSeries
import com.option.domain.market.util.SessionCalendar
import com.option.domain.model.calculator.AtmIv
import com.option.domain.model.calculator.BlackScholes
import com.option.domain.model.calculator.ExpectedMoveCalculator
import com.option.domain.model.calculator.ExpiryPicker
import com.option.domain.model.calculator.HypothesisRules
import com.option.domain.model.calculator.OptionRow
import com.option.domain.model.calculator.PcrCalculator
import com.option.domain.model.calculator.RealizedVolCalculator
import com.option.domain.model.entity.HypothesisSignal
import com.option.domain.model.entity.Publication
import com.option.domain.model.entity.TickerDailyMetric
import com.option.domain.model.repository.HypothesisSignalRepository
import com.option.domain.model.repository.PublicationRepository
import com.option.domain.model.repository.TickerDailyMetricRepository
import com.option.domain.model.vo.Horizon
import com.option.domain.model.vo.ModelVersions
import com.option.domain.model.vo.PublicationStatus
import com.option.global.properties.UniverseProperties
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

// 과거 지표 계산. oi랑 그릭스가 없어서 따로 계산해야댐
@Service
class BackfillModelService(
    private val publicationRepository: PublicationRepository,
    private val tickerDailyMetricRepository: TickerDailyMetricRepository,
    private val hypothesisSignalRepository: HypothesisSignalRepository,
    private val optionDailyRepository: OptionDailyRepository,
    private val stockDailyRepository: StockDailyRepository,
    private val macroDailyRepository: MacroDailyRepository,
    private val tradingCalendarService: TradingCalendarService,
    private val scoringService: ScoringService,
    private val universeProperties: UniverseProperties,
    private val jobRunner: JobRunner,
) {
    fun compute(context: RunContext) {
        val calendar = tradingCalendarService.calendar()

        val sessions = optionDailyRepository.findSessions(Feeds.OPRA).filter { calendar.isSession(it) }
        context.symbolsTotal = sessions.size
        sessions.forEach { session ->
            jobRunner.isolated(context, session.toString()) {
                computeSession(context, session, calendar)
            }
        }

        sessions.mapNotNull { calendar.next(it) }.forEach { target ->
            jobRunner.isolated(context, "score:$target") {
                context.rowsWritten += scoringService.scoreSession(target)
            }
        }
    }

    private fun computeSession(context: RunContext, base: LocalDate, calendar: SessionCalendar) {
        if (publicationRepository.existsBySessionDateAndStatus(base, PublicationStatus.BACKFILL)) return
        val target = calendar.next(base) ?: return
        val weekTarget = calendar.weekEnd(target) ?: return
        val tbill = macroDailyRepository.findFirstBySeriesAndObsDateLessThanEqualOrderByObsDateDesc(MacroSeries.TBILL_3M, base)
        val rate = if (tbill != null) tbill.value / 100 else null

        jobRunner.inTransaction(context) {
            val publication = publicationRepository.save(
                Publication(sessionDate = base, modelVersion = ModelVersions.BACKFILL, status = PublicationStatus.BACKFILL),
            )
            for (symbol in universeProperties.backfillSymbols) {
                val metric = computeMetric(publication.publicationId!!, symbol, base, target, weekTarget, calendar, rate) ?: continue
                tickerDailyMetricRepository.save(metric)
                val signal = HypothesisRules.pcrContrarian(metric.pcrPercentile)
                val horizons = listOf(Horizon.NEXT_DAY to target, Horizon.WEEK to weekTarget)
                val signals = horizons.map { (horizon, session) ->
                    HypothesisSignal(
                        publicationId = publication.publicationId,
                        symbol = symbol,
                        hypothesis = ModelVersions.PCR_CONTRARIAN,
                        horizon = horizon,
                        targetSession = session,
                        signal = signal,
                        inputValue = metric.pcrPercentile,
                    )
                }
                hypothesisSignalRepository.saveAll(signals)
                context.rowsWritten++
            }
        }
    }

    private fun computeMetric(
        publicationId: Long,
        symbol: String,
        base: LocalDate,
        target: LocalDate,
        weekTarget: LocalDate,
        calendar: SessionCalendar,
        rate: Double?,
    ): TickerDailyMetric? {
        val stock = stockDailyRepository.findBySymbolAndSessionDate(symbol, base) ?: return null
        val spot = stock.closeRaw
        val daily = optionDailyRepository.findByUnderlyingAndSessionDateAndFeed(symbol, base, Feeds.OPRA)
        if (daily.isEmpty()) return null
        val expiries = daily.map { it.expirationDate }.toSet()
        val nearest = ExpiryPicker.nearest(expiries, base)
        val weekly = ExpiryPicker.weekly(expiries, base, target)
        val nearestRows = daily.filter { it.expirationDate == nearest }
        val weeklyRows = daily.filter { it.expirationDate == weekly }
        val dayIv = if (nearest != null) atmImpliedVol(nearestRows, spot, base, rate) else null
        val weekIv = if (weekly != null) atmImpliedVol(weeklyRows, spot, base, rate) else null

        val rows = daily.map { OptionRow(it.expirationDate, it.strike, it.optionType, volume = it.volume) }
        val pcr = PcrCalculator.volume(rows)
        val percentile = if (pcr != null) {
            val history = tickerDailyMetricRepository.findPcrHistory(symbol, base, PcrCalculator.HISTORY)
            PcrCalculator.percentile(pcr, history)
        } else {
            null
        }
        val closes = stockDailyRepository
            .findBySymbolAndSessionDateLessThanEqualOrderBySessionDateDesc(symbol, base, PageRequest.of(0, RV_ROWS))
            .map { it.closeAdj }
            .reversed()
        val stdev = RealizedVolCalculator.dailyStdev(closes)
        val dayDays = calendar.countBetween(base, target)
        val weekDays = calendar.countBetween(base, weekTarget)
        val dayWidth = if (dayIv != null) ExpectedMoveCalculator.width(spot, dayIv.iv, dayDays) else null
        val weekWidth = if (weekIv != null) ExpectedMoveCalculator.width(spot, weekIv.iv, weekDays) else null
        val dayRvWidth = if (stdev != null) RealizedVolCalculator.width(spot, stdev, dayDays) else null
        val weekRvWidth = if (stdev != null) RealizedVolCalculator.width(spot, stdev, weekDays) else null
        return TickerDailyMetric(
            publicationId = publicationId,
            symbol = symbol,
            baseSession = base,
            targetSession = target,
            weekTargetSession = weekTarget,
            baseClose = spot,
            dayIv = dayIv?.iv,
            dayIvExpiry = if (dayIv != null) nearest else null,
            dayIvFallback = dayIv?.fallback,
            dayWidth = dayWidth,
            weekIv = weekIv?.iv,
            weekIvExpiry = if (weekIv != null) weekly else null,
            weekIvFallback = weekIv?.fallback,
            weekWidth = weekWidth,
            dayRvWidth = dayRvWidth,
            weekRvWidth = weekRvWidth,
            pcrVolume = pcr,
            pcrPercentile = percentile,
        )
    }

    private fun atmImpliedVol(rows: List<OptionDaily>, spot: Double, base: LocalDate, rate: Double?): AtmIv? {
        if (rate == null) return null
        val strikes = rows.map { it.strike }
            .distinct()
            .sortedBy { abs(it - spot) }
            .take(NEAR_STRIKES)
            .toSet()
        val nearRows = rows.filter { it.strike in strikes }
        val inverted = nearRows.map { row ->
            val years = ChronoUnit.DAYS.between(base, row.expirationDate) / CALENDAR_DAYS
            val price = row.closePrice
            val iv = if (price != null) {
                BlackScholes.impliedVol(row.optionType, price, spot, row.strike, years, rate)
            } else {
                null
            }
            OptionRow(row.expirationDate, row.strike, row.optionType, iv = iv)
        }
        return ExpectedMoveCalculator.atmIv(inverted, spot)
    }

    private companion object {
        const val RV_ROWS = 21
        const val NEAR_STRIKES = 5
        const val CALENDAR_DAYS = 365.0
    }
}
