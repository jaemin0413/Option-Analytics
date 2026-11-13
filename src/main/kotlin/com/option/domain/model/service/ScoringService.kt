package com.option.domain.model.service

import com.option.domain.journal.repository.JournalRepository
import com.option.domain.journal.vo.JournalDirection
import com.option.domain.market.entity.StockDaily
import com.option.domain.market.repository.StockDailyRepository
import com.option.domain.market.service.TradingCalendarService
import com.option.domain.market.service.UniverseService
import com.option.domain.model.calculator.HypothesisRules
import com.option.domain.model.entity.Score
import com.option.domain.model.entity.TickerDailyMetric
import com.option.domain.model.repository.HypothesisSignalRepository
import com.option.domain.model.repository.PublicationRepository
import com.option.domain.model.repository.ScoreRepository
import com.option.domain.model.repository.TickerDailyMetricRepository
import com.option.domain.model.vo.Horizon
import com.option.domain.model.vo.IvSource
import com.option.domain.model.vo.PublicationStatus
import com.option.domain.model.vo.ScoreSubjects
import com.option.domain.model.vo.Signal
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max

// 장 종료시 예측 채점
@Service
class ScoringService(
    private val tickerDailyMetricRepository: TickerDailyMetricRepository,
    private val hypothesisSignalRepository: HypothesisSignalRepository,
    private val publicationRepository: PublicationRepository,
    private val scoreRepository: ScoreRepository,
    private val journalRepository: JournalRepository,
    private val stockDailyRepository: StockDailyRepository,
    private val tradingCalendarService: TradingCalendarService,
    private val universeService: UniverseService,
    private val transactionTemplate: TransactionTemplate,
) {
    private data class Key(val publicationId: Long?, val userId: Long?, val symbol: String, val subject: String, val horizon: Horizon)

    private inner class Scorer(session: LocalDate, private val prices: Map<Pair<String, LocalDate>, StockDaily>) {
        val session = session
        val scores = mutableListOf<Score>()

        fun returnOf(symbol: String, from: LocalDate): Double? {
            val start = prices[symbol to from] ?: return null
            val end = prices[symbol to session] ?: return null
            return end.closeAdj / start.closeAdj - 1
        }

        fun price(symbol: String, date: LocalDate): StockDaily? = prices[symbol to date]
    }

    // 세선 저장 후 채점
    fun scoreSession(session: LocalDate): Int = transactionTemplate.execute {
        val calendar = tradingCalendarService.calendar()
        val metrics = tickerDailyMetricRepository.findPublishedByTarget(session)
        val signals = hypothesisSignalRepository.findPublishedByTarget(session)
        val journals = journalRepository.findByTargetSession(session)
        val previous = calendar.prev(session)

        val baseDates = metrics.map { it.baseSession }
        val beforeBaseDates = metrics.mapNotNull { calendar.prev(it.baseSession) }
        val dates = (baseDates + beforeBaseDates + listOfNotNull(session, previous)).toSet()
        val prices = stockDailyRepository.findBySessionDateIn(dates).associateBy { it.symbol to it.sessionDate }
        val done = scoreRepository.findByTargetSession(session)
            .map { Key(it.publicationId, it.userId, it.symbol, it.subject, it.horizon) }
            .toSet()
        val backfillIds = metrics.map { it.publicationId }.distinct()
            .filter { publicationRepository.findById(it).orElse(null)?.status == PublicationStatus.BACKFILL }
            .toSet()
        val scorer = Scorer(session, prices)

        val signalIndex = signals.groupBy { Triple(it.publicationId, it.symbol, it.horizon) }
        metrics.forEach { metric ->
            val source = if (metric.publicationId in backfillIds) IvSource.BACKFILL else IvSource.PROVIDER
            val beforeBase = calendar.prev(metric.baseSession)
            if (metric.targetSession == session) {
                scoreMetric(scorer, metric, Horizon.NEXT_DAY, source, beforeBase, signalIndex)
            }
            if (metric.weekTargetSession == session) {
                scoreMetric(scorer, metric, Horizon.WEEK, source, beforeBase, signalIndex)
            }
        }
        for (journal in journals) {
            val base = previous ?: continue
            val ret = scorer.returnOf(journal.symbol, base) ?: continue
            val prediction = when (journal.direction) {
                JournalDirection.UP -> Signal.UP
                JournalDirection.DOWN -> Signal.DOWN
                JournalDirection.FLAT -> Signal.FLAT
                JournalDirection.HOLD -> Signal.ABSTAIN
            }
            add(
                scorer,
                null,
                journal.symbol,
                ScoreSubjects.JOURNAL,
                Horizon.NEXT_DAY,
                IvSource.PROVIDER,
                base,
                prediction,
                false,
                ret,
                HypothesisRules.isHit(prediction, ret),
                userId = journal.userId,
            )
        }

        val fresh = scorer.scores.filter { Key(it.publicationId, it.userId, it.symbol, it.subject, it.horizon) !in done }
        scoreRepository.saveAll(fresh)
        fresh.size
    } ?: 0

    // 지표 하나당 채점
    private fun scoreMetric(
        scorer: Scorer,
        metric: TickerDailyMetric,
        horizon: Horizon,
        source: IvSource,
        beforeBase: LocalDate?,
        signals: Map<Triple<Long, String, Horizon>, List<com.option.domain.model.entity.HypothesisSignal>>,
    ) {
        val symbol = metric.symbol
        val base = metric.baseSession
        val ret = scorer.returnOf(symbol, base) ?: return
        val baseClose = metric.baseClose ?: scorer.price(symbol, base)?.closeRaw ?: return
        val event = if (horizon == Horizon.NEXT_DAY) metric.dayEvent else metric.weekEvent
        val id = metric.publicationId

        fun put(
            subject: String,
            prediction: Signal,
            hit: Boolean? = HypothesisRules.isHit(prediction, ret),
            width: Double? = null,
            outside: Double? = null,
            distance: Double? = null,
            range: Double? = null,
        ) {
            add(scorer, id, symbol, subject, horizon, source, base, prediction, event, ret, hit, width, outside, distance, range)
        }

        signals[Triple(id, symbol, horizon)].orEmpty().forEach { put(it.hypothesis, it.signal) }

        put(ScoreSubjects.ALWAYS_UP, Signal.UP)
        val previous = if (beforeBase != null) scorer.price(symbol, beforeBase) else null
        val current = scorer.price(symbol, base)
        val prevDirection = when {
            previous == null || current == null -> Signal.NO_DATA
            current.closeAdj > previous.closeAdj -> Signal.UP
            current.closeAdj < previous.closeAdj -> Signal.DOWN
            else -> Signal.ABSTAIN
        }
        put(ScoreSubjects.PREV_DIRECTION, prevDirection)

        val emWidth = if (horizon == Horizon.NEXT_DAY) metric.dayWidth else metric.weekWidth
        val rvWidth = if (horizon == Horizon.NEXT_DAY) metric.dayRvWidth else metric.weekRvWidth
        listOf(ScoreSubjects.EXPECTED_MOVE to emWidth, ScoreSubjects.RV_RANGE to rvWidth).forEach { (subject, width) ->
            if (width == null) {
                put(subject, Signal.NO_DATA, hit = null)
            } else {
                val move = abs(ret) * baseClose
                put(subject, Signal.IN_RANGE, hit = move <= width, width = width / baseClose, outside = max(0.0, move - width) / baseClose)
            }
        }

        if (source == IvSource.PROVIDER) {
            val gex = if (horizon == Horizon.NEXT_DAY) metric.gexWeekly else metric.gexMonthly
            val regime = when {
                gex == null || !metric.gexOfficial -> Signal.NO_DATA
                gex >= 0 -> Signal.POSITIVE
                else -> Signal.NEGATIVE
            }
            val targetPrice = scorer.price(symbol, scorer.session)
            val range = if (targetPrice != null && horizon == Horizon.NEXT_DAY) {
                (targetPrice.highPrice - targetPrice.lowPrice) / targetPrice.closeRaw
            } else {
                null
            }
            put(ScoreSubjects.GEX_REGIME, regime, hit = null, range = range)

            val maxPain = metric.maxpainStrike
            if (horizon == Horizon.WEEK && metric.maxpainExpiry == scorer.session && maxPain != null) {
                val settle = baseClose * (1 + ret)
                put(ScoreSubjects.MAXPAIN, Signal.IN_RANGE, hit = null, distance = abs(settle - maxPain) / baseClose)
                put(ScoreSubjects.MAXPAIN_BASE_SPOT, Signal.IN_RANGE, hit = null, distance = abs(settle - baseClose) / baseClose)
                val nearestStrike = metric.maxpainNearestStrike
                if (nearestStrike != null) {
                    put(ScoreSubjects.MAXPAIN_BASE_STRIKE, Signal.IN_RANGE, hit = null, distance = abs(settle - nearestStrike) / baseClose)
                }
            }
        }
    }

    // 채점 결과 한 건씩 모아둠
    private fun add(
        scorer: Scorer,
        publicationId: Long?,
        symbol: String,
        subject: String,
        horizon: Horizon,
        source: IvSource,
        base: LocalDate,
        prediction: Signal,
        event: Boolean,
        actualReturn: Double,
        hit: Boolean?,
        width: Double? = null,
        outside: Double? = null,
        distance: Double? = null,
        range: Double? = null,
        userId: Long? = null,
    ) {
        val noData = prediction == Signal.NO_DATA
        scorer.scores += Score(
            publicationId = publicationId,
            userId = userId,
            symbol = symbol,
            symbolGroup = universeService.groupOf(symbol),
            subject = subject,
            horizon = horizon,
            ivSource = source,
            baseSession = base,
            targetSession = scorer.session,
            issuedWeekday = tradingCalendarService.calendar().next(base)?.dayOfWeek?.value ?: 0,
            prediction = prediction,
            eventDay = event,
            actualReturn = actualReturn,
            hit = hit,
            width = if (noData) null else width,
            outsideError = outside,
            distance = distance,
            rangePct = range,
        )
    }
}
