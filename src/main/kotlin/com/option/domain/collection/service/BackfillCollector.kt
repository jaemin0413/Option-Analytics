package com.option.domain.collection.service

import com.fasterxml.jackson.databind.JsonNode
import com.option.domain.collection.client.AlpacaClient
import com.option.domain.collection.client.doubleOrNull
import com.option.domain.collection.client.etDateOrNull
import com.option.domain.collection.client.longOrNull
import com.option.domain.collection.repository.RawFileRepository
import com.option.domain.market.entity.OptionDaily
import com.option.domain.market.repository.OptionDailyRepository
import com.option.domain.market.service.TradingCalendarService
import com.option.domain.market.vo.Feeds
import com.option.domain.market.util.MarketTime
import com.option.domain.market.util.OccSymbol
import com.option.global.properties.JobProperties
import com.option.global.properties.UniverseProperties
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference

// 백필 작업, 2년치 데이터 다 때려박을 예정
@Service
class BackfillCollector(
    private val alpacaClient: AlpacaClient,
    private val rawFileStore: RawFileStore,
    private val rawFileRepository: RawFileRepository,
    private val optionDailyRepository: OptionDailyRepository,
    private val tradingCalendarService: TradingCalendarService,
    private val backfillWindow: BackfillWindow,
    private val diskGuard: DiskGuard,
    private val jobProperties: JobProperties,
    private val universeProperties: UniverseProperties,
    private val jobRunner: JobRunner,
    private val clock: Clock,
    meterRegistry: MeterRegistry,
) {
    private val progress = AtomicReference(0.0)

    init {
        Gauge.builder("option.backfill.progress") { progress.get() }.register(meterRegistry)
    }

    // 계약 목록을 묶어 일봉으로 받음
    fun collect(context: RunContext) {
        val today = clock.instant().atZone(MarketTime.ET).toLocalDate()
        val end = tradingCalendarService.calendar().prev(today) ?: today.minusDays(1)
        progress.set(0.0)

        val contracts = universeProperties.backfillSymbols.associateWith { collectContracts(context, it, today) }
        val chunks = contracts.flatMap { (underlying, symbols) ->
            symbols.chunked(CHUNK).mapIndexed { index, chunk -> Triple(underlying, index, chunk) }
        }
        context.symbolsTotal = chunks.size

        chunks.forEachIndexed { done, (underlying, index, chunk) ->
            waitOutPauseWindow(context)
            if (diskGuard.isTooLow(context.job)) {
                context.stoppedEarly = true
                context.addNote("디스크 여유 부족으로 ${done}번째 묶음에서 중단")
                return
            }
            jobRunner.isolated(context, "$underlying:$index") { collectChunk(context, underlying, index, chunk, end) }
            progress.set((done + 1).toDouble() / chunks.size)
        }
    }

    // 만료된 계약이랑 살아 있는 계약 다 모으고 표준 심볼만 남기기
    private fun collectContracts(context: RunContext, underlying: String, today: LocalDate): List<String> {
        val symbols = mutableListOf<String>()
        val conditions = listOf(
            mapOf(
                "status" to "inactive",
                "expiration_date_gte" to jobProperties.backfillExpirationStart.toString(),
                "expiration_date_lte" to today.toString(),
            ),
            mapOf("status" to "active", "expiration_date_gte" to today.toString()),
        )
        conditions.forEach { condition ->
            alpacaClient.forEachPage(
                context,
                "contracts_${condition["status"]}",
                "/v2/options/contracts",
                condition + mapOf("underlying_symbols" to underlying, "limit" to "10000"),
                trading = true,
            ) { response, page, json ->
                val file = rawFileStore.save(context, response, underlying, page)
                jobRunner.inTransaction(context) { rawFileRepository.save(file) }
                json.path("option_contracts").forEach { symbols += it.path("symbol").asText() }
            }
        }
        val standard = symbols.distinct().filter { OccSymbol.parse(it)?.isStandardFor(underlying) == true }
        context.addNote("$underlying 계약 ${standard.size}개, 비표준 제외 ${symbols.distinct().size - standard.size}개")
        return standard
    }

    // 계약 한 묶음의 일봉을 받아 저장
    private fun collectChunk(context: RunContext, underlying: String, index: Int, contracts: List<String>, end: LocalDate) {
        alpacaClient.forEachPage(
            context,
            "option_bars",
            "/v1beta1/options/bars",
            mapOf(
                "symbols" to contracts.joinToString(","),
                "timeframe" to "1Day",
                "start" to jobProperties.backfillStart.toString(),
                "end" to end.toString(),
                "limit" to "10000",
            ),
        ) { response, page, json ->
            val file = rawFileStore.save(context, response, "$underlying-$index", page)
            val rows = mutableListOf<OptionDaily>()
            json.path("bars").fields().forEach { (contract, bars) ->
                for (bar in bars) {
                    val row = toRow(underlying, contract, bar, response.receivedAt)
                    if (row != null) {
                        rows.add(row)
                    }
                }
            }
            jobRunner.inTransaction(context) {
                rawFileRepository.save(file)
                optionDailyRepository.saveAll(rows)
            }
            context.rowsWritten += rows.size
        }
    }

    private fun waitOutPauseWindow(context: RunContext) {
        while (backfillWindow.isPaused()) {
            Thread.sleep(PAUSE_POLL_MILLIS)
        }
    }

    // 일봉 하나를 option_daily 행으로 변환
    private fun toRow(
        underlying: String,
        contract: String,
        bar: JsonNode,
        receivedAt: java.time.Instant,
    ): OptionDaily? {
        val occ = OccSymbol.parse(contract) ?: return null
        val date = bar.etDateOrNull("t") ?: return null
        return OptionDaily(
            contractSymbol = contract,
            underlying = underlying,
            expirationDate = occ.expiration,
            strike = occ.strike,
            optionType = occ.type,
            sessionDate = date,
            provider = Feeds.PROVIDER_ALPACA,
            feed = Feeds.OPRA,
            receivedAt = receivedAt,
            barDate = date,
            openPrice = bar.doubleOrNull("o"),
            highPrice = bar.doubleOrNull("h"),
            lowPrice = bar.doubleOrNull("l"),
            closePrice = bar.doubleOrNull("c"),
            volume = bar.longOrNull("v"),
            tradeCount = bar.longOrNull("n"),
            vwap = bar.doubleOrNull("vw"),
        )
    }

    private companion object {
        const val CHUNK = 100
        const val PAUSE_POLL_MILLIS = 60_000L
    }
}
