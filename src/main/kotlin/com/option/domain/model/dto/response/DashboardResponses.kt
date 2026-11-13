package com.option.domain.model.dto.response

import com.option.domain.market.entity.EventCalendar
import com.option.domain.market.vo.EventType
import com.option.domain.model.vo.SentimentComponent
import com.option.domain.model.entity.HypothesisSignal
import com.option.domain.model.entity.MarketSentiment
import com.option.domain.model.entity.Publication
import com.option.domain.model.entity.TickerDailyMetric
import com.option.domain.model.vo.Horizon
import com.option.domain.model.vo.Signal
import java.time.Instant
import java.time.LocalDate

data class PublicationResponse(
    val publicationId: Long,
    val baseSession: LocalDate,
    val publishedAt: Instant?,
    val modelVersion: String,
) {
    companion object {
        fun from(publication: Publication) = PublicationResponse(
            publication.publicationId!!,
            publication.sessionDate,
            publication.publishedAt,
            publication.modelVersion,
        )
    }
}

data class RangeResponse(
    val iv: Double?,
    val ivExpiry: LocalDate?,
    val fallback: Boolean?,
    val width: Double?,
    val low: Double?,
    val high: Double?,
    val rvWidth: Double?,
)

data class ConcentrationResponse(
    val expiry: LocalDate?,
    val maxPain: Double?,
    val callWall: Double?,
    val putWall: Double?,
)

data class GexResponse(
    val weekly: Double?,
    val monthly: Double?,
    val allExpiries: Double?,
    val regime: String,
    val official: Boolean,
    val contracts: Int?,
    val contractMissingRate: Double?,
    val oiMissingRate: Double?,
    val atmMissing: Boolean?,
    val byStrike: Map<String, Double>?,
)

data class PcrResponse(
    val volume: Double?,
    val openInterest: Double?,
    val volumeWeek: Double?,
    val openInterestWeek: Double?,
    val percentile: Double?,
)

data class TickerMetricResponse(
    val symbol: String,
    val baseSession: LocalDate,
    val targetSession: LocalDate,
    val weekTargetSession: LocalDate,
    val baseClose: Double?,
    val oiAsOfDate: LocalDate?,
    val oiDelayDays: Int?,
    val dayRange: RangeResponse,
    val weekRange: RangeResponse,
    val weekly: ConcentrationResponse,
    val monthly: ConcentrationResponse,
    val gex: GexResponse,
    val pcr: PcrResponse,
    val dayEvent: Boolean,
    val weekEvent: Boolean,
) {
    companion object {
        fun from(metric: TickerDailyMetric, includeStrikes: Boolean = false) = TickerMetricResponse(
            symbol = metric.symbol,
            baseSession = metric.baseSession,
            targetSession = metric.targetSession,
            weekTargetSession = metric.weekTargetSession,
            baseClose = metric.baseClose,
            oiAsOfDate = metric.oiAsOfDate,
            oiDelayDays = metric.oiDelayDays,
            dayRange = range(
                close = metric.baseClose,
                iv = metric.dayIv,
                expiry = metric.dayIvExpiry,
                fallback = metric.dayIvFallback,
                width = metric.dayWidth,
                rvWidth = metric.dayRvWidth,
            ),
            weekRange = range(
                close = metric.baseClose,
                iv = metric.weekIv,
                expiry = metric.weekIvExpiry,
                fallback = metric.weekIvFallback,
                width = metric.weekWidth,
                rvWidth = metric.weekRvWidth,
            ),
            weekly = ConcentrationResponse(
                expiry = metric.maxpainExpiry,
                maxPain = metric.maxpainStrike,
                callWall = metric.callWallStrike,
                putWall = metric.putWallStrike,
            ),
            monthly = ConcentrationResponse(
                expiry = metric.monthlyExpiry,
                maxPain = metric.monthlyMaxpainStrike,
                callWall = metric.monthlyCallWallStrike,
                putWall = metric.monthlyPutWallStrike,
            ),
            gex = GexResponse(
                weekly = metric.gexWeekly,
                monthly = metric.gexMonthly,
                allExpiries = metric.gexAll,
                regime = when {
                    metric.gexWeekly == null -> "NO_DATA"
                    metric.gexWeekly >= 0 -> "DAMPENING"
                    else -> "AMPLIFYING"
                },
                official = metric.gexOfficial,
                contracts = metric.gexContracts,
                contractMissingRate = metric.gexContractMissingRate,
                oiMissingRate = metric.gexOiMissingRate,
                atmMissing = metric.gexAtmMissing,
                byStrike = if (includeStrikes) metric.gexDetail?.byStrike else null,
            ),
            pcr = PcrResponse(metric.pcrVolume, metric.pcrOi, metric.pcrVolumeWeek, metric.pcrOiWeek, metric.pcrPercentile),
            dayEvent = metric.dayEvent,
            weekEvent = metric.weekEvent,
        )

        private fun range(
            close: Double?,
            iv: Double?,
            expiry: LocalDate?,
            fallback: Boolean?,
            width: Double?,
            rvWidth: Double?,
        ): RangeResponse {
            return RangeResponse(
                iv = iv,
                ivExpiry = expiry,
                fallback = fallback,
                width = width,
                low = if (close != null && width != null) close - width else null,
                high = if (close != null && width != null) close + width else null,
                rvWidth = rvWidth,
            )
        }
    }
}

data class SignalResponse(
    val hypothesis: String,
    val horizon: Horizon,
    val targetSession: LocalDate,
    val signal: Signal,
    val inputValue: Double?,
) {
    companion object {
        fun from(signal: HypothesisSignal) =
            SignalResponse(signal.hypothesis, signal.horizon, signal.targetSession, signal.signal, signal.inputValue)
    }
}

data class EventResponse(
    val date: LocalDate,
    val type: EventType,
    val symbol: String?,
    val title: String?,
) {
    companion object {
        fun from(event: EventCalendar): EventResponse {
            // 시장 전체 일정은 symbol이 빈 문자열이라 응답에서는 null로 바꾼다.
            val symbol = if (event.symbol.isEmpty()) null else event.symbol
            return EventResponse(event.eventDate, event.eventType, symbol, event.title)
        }
    }
}

data class SentimentResponse(
    val score: Double?,
    val usedCount: Int,
    val usedBitset: Int,
    val components: List<SentimentComponent>,
) {
    companion object {
        fun from(sentiment: MarketSentiment) =
            SentimentResponse(sentiment.score, sentiment.usedCount, sentiment.usedBitset, sentiment.components)
    }
}

data class DashboardResponse(
    val publication: PublicationResponse?,
    val indexCards: List<TickerMetricResponse>,
    val sectors: List<TickerMetricResponse>,
    val sentiment: SentimentResponse?,
    val events: List<EventResponse>,
)

data class TickerDetailResponse(
    val publication: PublicationResponse,
    val metric: TickerMetricResponse?,
    val signals: List<SignalResponse>,
    val events: List<EventResponse>,
)
