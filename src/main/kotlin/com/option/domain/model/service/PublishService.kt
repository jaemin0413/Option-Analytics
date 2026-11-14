package com.option.domain.model.service

import com.option.domain.collection.service.JobRunner
import com.option.domain.collection.service.RunContext
import com.option.domain.market.entity.OptionDaily
import com.option.domain.market.repository.EventCalendarRepository
import com.option.domain.market.repository.OptionDailyRepository
import com.option.domain.market.repository.StockDailyRepository
import com.option.domain.market.service.TradingCalendarService
import com.option.domain.market.service.UniverseService
import com.option.domain.market.vo.Feeds
import com.option.domain.market.util.MarketTime
import com.option.domain.market.util.OccSymbol
import com.option.domain.market.util.SessionCalendar
import com.option.domain.model.calculator.AtmIv
import com.option.domain.model.calculator.ExpectedMoveCalculator
import com.option.domain.model.calculator.ExpiryPicker
import com.option.domain.model.calculator.GexCalculator
import com.option.domain.model.vo.GexDetail
import com.option.domain.model.calculator.HypothesisRules
import com.option.domain.model.calculator.MaxPainCalculator
import com.option.domain.model.calculator.OptionRow
import com.option.domain.model.calculator.PcrCalculator
import com.option.domain.model.calculator.RealizedVolCalculator
import com.option.domain.model.entity.HypothesisSignal
import com.option.domain.model.entity.MarketSentiment
import com.option.domain.model.entity.Publication
import com.option.domain.model.entity.TickerDailyMetric
import com.option.domain.model.repository.HypothesisSignalRepository
import com.option.domain.model.repository.MarketSentimentRepository
import com.option.domain.model.repository.PublicationRepository
import com.option.domain.model.repository.TickerDailyMetricRepository
import com.option.domain.model.vo.Horizon
import com.option.domain.model.vo.ModelVersions
import com.option.domain.model.vo.PublicationStatus
import com.option.domain.model.vo.Signal
import com.option.global.apiPayLoad.error.ModelErrorCode
import com.option.global.apiPayLoad.exception.BusinessException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs

// 발표 작업
@Service
class PublishService(
    private val publicationRepository: PublicationRepository,
    private val tickerDailyMetricRepository: TickerDailyMetricRepository,
    private val hypothesisSignalRepository: HypothesisSignalRepository,
    private val marketSentimentRepository: MarketSentimentRepository,
    private val optionDailyRepository: OptionDailyRepository,
    private val stockDailyRepository: StockDailyRepository,
    private val eventCalendarRepository: EventCalendarRepository,
    private val tradingCalendarService: TradingCalendarService,
    private val universeService: UniverseService,
    private val sentimentService: SentimentService,
    private val jobRunner: JobRunner,
    private val transactionTemplate: TransactionTemplate,
    private val clock: Clock,
) {
    // 지표와 시장심리 계산
    fun compute(context: RunContext) {
        val base = context.session
        val calendar = tradingCalendarService.calendar()
        val target = calendar.next(base) ?: throw BusinessException(ModelErrorCode.CALENDAR_SESSION_MISSING)
        val weekTarget = calendar.weekEnd(target) ?: throw BusinessException(ModelErrorCode.CALENDAR_SESSION_MISSING)

        val publication = publicationRepository.save(Publication(sessionDate = base, modelVersion = ModelVersions.LIVE, status = PublicationStatus.COMPUTING))
        val publicationId = publication.publicationId!!

        val symbols = universeService.symbols()
        context.symbolsTotal = symbols.size
        val events = eventCalendarRepository.findByEventDateBetweenOrderByEventDate(base.plusDays(1), weekTarget)
        symbols.forEach { symbol ->
            jobRunner.isolated(context, symbol) {
                jobRunner.inTransaction(context) {
                    computeSymbol(
                        SymbolInput(symbol, publicationId, base, target, weekTarget, calendar),
                        events.filter { it.symbol == "" || it.symbol == symbol }.map { it.eventDate },
                    )
                }
                context.rowsWritten++
            }
        }

        jobRunner.isolated(context, "SENTIMENT") {
            val result = sentimentService.compute(base, calendar)
            marketSentimentRepository.save(
                MarketSentiment(
                    publicationId = publicationId,
                    score = result.score,
                    usedBitset = result.usedBitset,
                    usedCount = result.usedCount,
                    components = result.components,
                ),
            )
        }
        publication.status = PublicationStatus.COMPUTED
        publicationRepository.save(publication)
    }

    // 21시에 발표
    fun publish(session: LocalDate) {
        transactionTemplate.executeWithoutResult {
            if (publicationRepository.existsBySessionDateAndStatus(session, PublicationStatus.PUBLISHED)) {
                throw BusinessException(ModelErrorCode.ALREADY_PUBLISHED, mapOf("session" to session))
            }
            val publication = publicationRepository
                .findFirstBySessionDateAndStatusOrderByPublicationIdDesc(session, PublicationStatus.COMPUTED)
                ?: throw BusinessException(ModelErrorCode.PUBLICATION_NOT_FOUND, mapOf("session" to session))
            publication.status = PublicationStatus.PUBLISHED
            publication.publishedAt = visibleAt(session, clock.instant())
        }
    }

    private fun visibleAt(session: LocalDate, now: Instant): Instant {
        val day = tradingCalendarService.calendar().next(session) ?: return now
        val slot = day.atTime(LocalTime.of(21, 0)).atZone(MarketTime.KST).toInstant()
        return if (now.isBefore(slot)) slot else now
    }

    private data class SymbolInput(
        val symbol: String,
        val publicationId: Long,
        val base: LocalDate,
        val target: LocalDate,
        val weekTarget: LocalDate,
        val calendar: SessionCalendar,
    )

    // 가설 저장
    private fun computeSymbol(
        input: SymbolInput,
        eventDates: List<LocalDate>,
    ) {
        val (symbol, publicationId, base, target, weekTarget, calendar) = input
        val stock = stockDailyRepository.findBySymbolAndSessionDate(symbol, base)
        val spot = stock?.closeRaw
        val dbRows = optionDailyRepository.findByUnderlyingAndSessionDateAndFeed(symbol, base, Feeds.INDICATIVE)
            .filter { OccSymbol.parse(it.contractSymbol)?.isStandardFor(symbol) == true }
        val fallbackOi = if (dbRows.none { it.openInterest != null }) {
            previousOi(symbol, base)
        } else {
            emptyMap()
        }
        val rows = dbRows.map { toOptionRow(it, base, fallbackOi) }
        val oiDates = dbRows.mapNotNull { it.oiAsOfDate } +
            fallbackOi.values.map { it.second }
        val oiAsOf = oiDates.maxOrNull()

        val metric = if (spot == null) {
            TickerDailyMetric(publicationId = publicationId, symbol = symbol, baseSession = base, targetSession = target, weekTargetSession = weekTarget)
        } else {
            buildMetric(input, spot, rows, oiAsOf, eventDates)
        }
        tickerDailyMetricRepository.save(metric)
        hypothesisSignalRepository.saveAll(buildSignals(metric, rows))
    }

    private fun toOptionRow(
        row: OptionDaily,
        base: LocalDate,
        fallbackOi: Map<String, Pair<Long, LocalDate>>,
    ): OptionRow {
        val oi = row.openInterest ?: fallbackOi[row.contractSymbol]?.first
        return OptionRow(
            expiry = row.expirationDate,
            strike = row.strike,
            type = row.optionType,
            iv = row.impliedVol,
            gamma = row.gamma,
            oi = oi,
            volume = if (row.barDate == base) row.volume else null,
            close = row.closePrice,
        )
    }

    // 최근 미결제약정 사용
    private fun previousOi(symbol: String, base: LocalDate): Map<String, Pair<Long, LocalDate>> {
        val session = optionDailyRepository.findLatestOiSessionBefore(symbol, Feeds.INDICATIVE, base) ?: return emptyMap()
        return optionDailyRepository.findByUnderlyingAndSessionDateAndFeed(symbol, session, Feeds.INDICATIVE)
            .mapNotNull { row ->
                val oi = row.openInterest ?: return@mapNotNull null
                val date = row.oiAsOfDate ?: return@mapNotNull null
                row.contractSymbol to (oi to date)
            }
            .toMap()
    }

    private fun buildMetric(
        input: SymbolInput,
        spot: Double,
        rows: List<OptionRow>,
        oiAsOf: LocalDate?,
        eventDates: List<LocalDate>,
    ): TickerDailyMetric {
        val (symbol, publicationId, base, target, weekTarget, calendar) = input
        val expiries = rows.map { it.expiry }.toSet()
        val byExpiry = rows.groupBy { it.expiry }

        val nearest = ExpiryPicker.nearest(expiries, base)
        val weekly = ExpiryPicker.weekly(expiries, base, target)
        val monthly = ExpiryPicker.monthly(expiries, base)
        val dayDays = calendar.countBetween(base, target)
        val weekDays = calendar.countBetween(base, weekTarget)

        val dayIv: AtmIv? = if (nearest != null) ExpectedMoveCalculator.atmIv(byExpiry[nearest].orEmpty(), spot) else null
        val weekIv: AtmIv? = if (weekly != null) ExpectedMoveCalculator.atmIv(byExpiry[weekly].orEmpty(), spot) else null

        val closes = stockDailyRepository
            .findBySymbolAndSessionDateLessThanEqualOrderBySessionDateDesc(input.symbol, base, PageRequest.of(0, RV_ROWS))
            .map { it.closeAdj }
            .reversed()
        val stdev = RealizedVolCalculator.dailyStdev(closes)

        val weeklyRows = if (weekly != null) byExpiry[weekly].orEmpty() else emptyList()
        val monthlyRows = if (monthly != null) byExpiry[monthly].orEmpty() else emptyList()
        val concentration = MaxPainCalculator.compute(weeklyRows, spot)
        val monthlyConcentration = MaxPainCalculator.compute(monthlyRows, spot)
        val maxPainOk = weeklyRows.count { (it.oi ?: 0L) > 0 } >= MIN_MAXPAIN_CONTRACTS

        val gexWeekly = if (weekly != null) GexCalculator.compute(rows, spot, base, weekly) else null
        val gexMonthly = if (monthly != null) GexCalculator.compute(rows, spot, base, monthly) else null
        val gexAll = GexCalculator.compute(rows, spot, base, null)

        val weekRows = rows.filter { it.expiry.isAfter(base) && weekly != null && !it.expiry.isAfter(weekly) }
        val pcrVolume = PcrCalculator.volume(rows)
        val history = tickerDailyMetricRepository.findPcrHistory(symbol, base, PcrCalculator.HISTORY)
        val percentile = if (pcrVolume != null) PcrCalculator.percentile(pcrVolume, history) else null

        val oiDelayDays = if (oiAsOf != null) calendar.countBetween(oiAsOf, base) else null
        val dayWidth = if (dayIv != null) ExpectedMoveCalculator.width(spot, dayIv.iv, dayDays) else null
        val weekWidth = if (weekIv != null) ExpectedMoveCalculator.width(spot, weekIv.iv, weekDays) else null
        val dayRvWidth = if (stdev != null) RealizedVolCalculator.width(spot, stdev, dayDays) else null
        val weekRvWidth = if (stdev != null) RealizedVolCalculator.width(spot, stdev, weekDays) else null
        val gexDetail = if (gexWeekly != null) {
            GexDetail(
                byStrike = gexWeekly.byStrike.mapKeys { it.key.toString() },
                byExpiry = gexWeekly.byExpiry.mapKeys { it.key.toString() },
                callOiMissingRate = gexWeekly.callOiMissingRate,
                putOiMissingRate = gexWeekly.putOiMissingRate,
            )
        } else {
            null
        }

        return TickerDailyMetric(
            publicationId = publicationId,
            symbol = symbol,
            baseSession = base,
            targetSession = target,
            weekTargetSession = weekTarget,
            baseClose = spot,
            oiAsOfDate = oiAsOf,
            oiDelayDays = oiDelayDays,
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
            maxpainExpiry = weekly,
            maxpainStrike = if (maxPainOk) concentration.maxPain else null,
            maxpainNearestStrike = weeklyRows.map { it.strike }.minByOrNull { abs(it - spot) },
            callWallStrike = concentration.callWall,
            putWallStrike = concentration.putWall,
            monthlyExpiry = monthly,
            monthlyMaxpainStrike = monthlyConcentration.maxPain,
            monthlyCallWallStrike = monthlyConcentration.callWall,
            monthlyPutWallStrike = monthlyConcentration.putWall,
            gexWeekly = gexWeekly?.total,
            gexMonthly = gexMonthly?.total,
            gexAll = gexAll.total,
            gexContracts = gexWeekly?.contracts,
            gexContractMissingRate = gexWeekly?.contractMissingRate,
            gexOiMissingRate = gexWeekly?.oiMissingRate,
            gexAtmMissing = gexWeekly?.atmMissing,
            gexOfficial = gexWeekly?.official == true,
            gexDetail = gexDetail,
            pcrVolume = pcrVolume,
            pcrOi = PcrCalculator.openInterest(rows),
            pcrVolumeWeek = PcrCalculator.volume(weekRows),
            pcrOiWeek = PcrCalculator.openInterest(weekRows),
            pcrPercentile = percentile,
            dayEvent = eventDates.any { it.isAfter(base) && !it.isAfter(target) },
            weekEvent = eventDates.any { it.isAfter(base) && !it.isAfter(weekTarget) },
        )
    }

    private fun buildSignals(metric: TickerDailyMetric, rows: List<OptionRow>): List<HypothesisSignal> {
        if (rows.isEmpty() && metric.baseClose == null) return emptyList()
        val pcr = HypothesisRules.pcrContrarian(metric.pcrPercentile)
        val horizons = listOf(
            Triple(Horizon.NEXT_DAY, metric.targetSession, metric.dayWidth),
            Triple(Horizon.WEEK, metric.weekTargetSession, metric.weekWidth),
        )
        return horizons.flatMap { (horizon, target, width) ->
            val pull: Signal = HypothesisRules.maxPainPull(metric.baseClose, metric.maxpainStrike, width)
            listOf(
                HypothesisSignal(
                    publicationId = metric.publicationId,
                    symbol = metric.symbol,
                    hypothesis = ModelVersions.PCR_CONTRARIAN,
                    horizon = horizon,
                    targetSession = target,
                    signal = pcr,
                    inputValue = metric.pcrPercentile,
                ),
                HypothesisSignal(
                    publicationId = metric.publicationId,
                    symbol = metric.symbol,
                    hypothesis = ModelVersions.MAXPAIN_PULL,
                    horizon = horizon,
                    targetSession = target,
                    signal = pull,
                    inputValue = metric.maxpainStrike,
                ),
            )
        }
    }

    private companion object {
        const val RV_ROWS = 21
        const val MIN_MAXPAIN_CONTRACTS = 20
    }
}
