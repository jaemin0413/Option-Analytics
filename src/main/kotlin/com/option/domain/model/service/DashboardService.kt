package com.option.domain.model.service

import com.option.domain.market.repository.EventCalendarRepository
import com.option.domain.market.repository.InstrumentRepository
import com.option.domain.model.dto.response.DashboardResponse
import com.option.domain.model.dto.response.EventResponse
import com.option.domain.model.dto.response.PublicationResponse
import com.option.domain.model.dto.response.SentimentResponse
import com.option.domain.model.dto.response.SignalResponse
import com.option.domain.model.dto.response.TickerDetailResponse
import com.option.domain.model.dto.response.TickerMetricResponse
import com.option.domain.model.dto.response.TickerSearchResponse
import com.option.domain.model.entity.Publication
import com.option.domain.model.repository.HypothesisSignalRepository
import com.option.domain.model.repository.MarketSentimentRepository
import com.option.domain.model.repository.PublicationRepository
import com.option.domain.model.repository.TickerDailyMetricRepository
import com.option.global.apiPayLoad.error.ModelErrorCode
import com.option.global.apiPayLoad.exception.BusinessException
import com.option.global.properties.UniverseProperties
import org.springframework.stereotype.Service
import java.time.Clock

// 공개된 최근 자료만 읽어 db를 안 뒤지게 함
@Service
class DashboardService(
    private val publicationRepository: PublicationRepository,
    private val tickerDailyMetricRepository: TickerDailyMetricRepository,
    private val hypothesisSignalRepository: HypothesisSignalRepository,
    private val marketSentimentRepository: MarketSentimentRepository,
    private val eventCalendarRepository: EventCalendarRepository,
    private val instrumentRepository: InstrumentRepository,
    private val universeProperties: UniverseProperties,
    private val clock: Clock,
) {
    private fun latest(): Publication? = publicationRepository.findLatestVisible(clock.instant())

    fun getDashboard(): DashboardResponse {
        val publication = latest() ?: return DashboardResponse(null, emptyList(), emptyList(), null, emptyList())
        val id = publication.publicationId!!
        val universe = universeProperties
        val metrics = tickerDailyMetricRepository
            .findByPublicationIdAndSymbolIn(id, universe.indexEtfs + universe.sectorEtfs)
            .associateBy { it.symbol }
        val target = metrics.values.firstOrNull()
        val events = if (target != null) {
            eventCalendarRepository.findByEventDateBetweenAndSymbolInOrderByEventDate(
                target.baseSession.plusDays(1),
                target.weekTargetSession,
                listOf(""),
            )
        } else {
            emptyList()
        }
        val sentiment = marketSentimentRepository.findByPublicationId(id)
        return DashboardResponse(
            publication = PublicationResponse.from(publication),
            indexCards = universe.indexEtfs.mapNotNull { metrics[it] }.map { TickerMetricResponse.from(it) },
            sectors = universe.sectorEtfs.mapNotNull { metrics[it] }.map { TickerMetricResponse.from(it) },
            sentiment = if (sentiment != null) SentimentResponse.from(sentiment) else null,
            events = events.map(EventResponse::from),
        )
    }

    fun getTicker(symbol: String): TickerDetailResponse {
        val publication = latest() ?: throw BusinessException(ModelErrorCode.PUBLICATION_NOT_FOUND)
        val id = publication.publicationId!!
        val metric = tickerDailyMetricRepository.findByPublicationIdAndSymbol(id, symbol)
            ?: throw BusinessException(ModelErrorCode.TICKER_NOT_FOUND, mapOf("symbol" to symbol))
        val events = eventCalendarRepository.findByEventDateBetweenAndSymbolInOrderByEventDate(
            metric.baseSession.plusDays(1),
            metric.weekTargetSession,
            listOf("", symbol),
        )
        return TickerDetailResponse(
            publication = PublicationResponse.from(publication),
            metric = TickerMetricResponse.from(metric, includeStrikes = true),
            signals = hypothesisSignalRepository.findByPublicationIdAndSymbol(id, symbol).map(SignalResponse::from),
            events = events.map(EventResponse::from),
        )
    }

    fun search(prefix: String): List<TickerSearchResponse> =
        instrumentRepository.findTop20BySymbolStartingWithOrderBySymbol(prefix.uppercase())
            .map { TickerSearchResponse(it.symbol, it.name) }
}
