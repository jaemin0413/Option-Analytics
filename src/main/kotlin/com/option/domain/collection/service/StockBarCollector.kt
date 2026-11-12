package com.option.domain.collection.service

import com.fasterxml.jackson.databind.JsonNode
import com.option.domain.collection.client.AlpacaClient
import com.option.domain.collection.client.doubleOrNull
import com.option.domain.collection.client.etDateOrNull
import com.option.domain.collection.client.longOrNull
import com.option.domain.collection.entity.RawFile
import com.option.domain.collection.repository.RawFileRepository
import com.option.domain.market.entity.StockDaily
import com.option.domain.market.repository.StockDailyRepository
import com.option.domain.market.service.UniverseService
import com.option.domain.market.vo.Feeds
import org.springframework.stereotype.Service
import java.time.LocalDate
import kotlin.math.abs

// 주식 일봉 수집
@Service
class StockBarCollector(
    private val alpacaClient: AlpacaClient,
    private val rawFileStore: RawFileStore,
    private val rawFileRepository: RawFileRepository,
    private val stockDailyRepository: StockDailyRepository,
    private val universeService: UniverseService,
    private val jobRunner: JobRunner,
) {
    private data class Bars(val raw: Map<Pair<String, LocalDate>, JsonNode>, val adjusted: Map<Pair<String, LocalDate>, Double>)

    // 매일 수집하는 거
    fun collectSession(context: RunContext, previous: LocalDate) {
        val symbols = universeService.symbols()
        context.symbolsTotal = symbols.size
        symbols.chunked(CHUNK).forEach { chunk ->
            jobRunner.isolated(context, chunk.first() + "+") {
                val bars = fetch(context, chunk, previous, context.session)
                val drifted = jobRunner.inTransaction(context) { store(context, chunk, previous, bars) }
                drifted.forEach { symbol -> refreshAdjusted(context, symbol, previous) }
            }
        }
    }

    // 초기수집용
    fun collectHistory(context: RunContext, start: LocalDate, end: LocalDate) {
        val symbols = universeService.symbols() + listOf("SPY", "TLT")
        val distinct = symbols.distinct()
        context.symbolsTotal += distinct.size
        distinct.chunked(CHUNK).forEach { chunk ->
            jobRunner.isolated(context, "history:" + chunk.first() + "+") {
                val bars = fetch(context, chunk, start, end)
                jobRunner.inTransaction(context) { store(context, chunk, start, bars, end) }
            }
        }
    }

    // 종가 분리해서 저장
    private fun fetch(context: RunContext, symbols: List<String>, start: LocalDate, end: LocalDate): Bars {
        val files = mutableListOf<RawFile>()
        val raw = mutableMapOf<Pair<String, LocalDate>, JsonNode>()
        val adjusted = mutableMapOf<Pair<String, LocalDate>, Double>()
        val params = mapOf(
            "symbols" to symbols.joinToString(","),
            "timeframe" to "1Day",
            "start" to start.toString(),
            "end" to end.toString(),
            "feed" to Feeds.SIP,
            "limit" to "10000",
        )
        listOf("raw", "all").forEach { adjustment ->
            val pageParams = params + ("adjustment" to adjustment)
            alpacaClient.forEachPage(context, "stock_bars_$adjustment", "/v2/stocks/bars", pageParams) { response, page, json ->
                files += rawFileStore.save(context, response, symbols.first() + "+", page)
                json.path("bars").fields().forEach { (symbol, list) ->
                    for (bar in list) {
                        val date = bar.etDateOrNull("t") ?: continue
                        if (adjustment == "raw") {
                            raw[symbol to date] = bar
                        } else {
                            adjusted[symbol to date] = bar.path("c").asDouble()
                        }
                    }
                }
            }
        }
        jobRunner.inTransaction(context) { rawFileRepository.saveAll(files) }
        return Bars(raw, adjusted)
    }

    // 행 적재
    private fun store(
        context: RunContext,
        symbols: List<String>,
        start: LocalDate,
        bars: Bars,
        end: LocalDate = context.session,
    ): Set<String> {
        val existing = stockDailyRepository.findBySymbolInAndSessionDateBetween(symbols, start, end)
            .associateBy { it.symbol to it.sessionDate }
        val drifted = mutableSetOf<String>()
        val added = bars.raw.mapNotNull { (key, bar) ->
            val adjusted = bars.adjusted[key] ?: return@mapNotNull null
            val found = existing[key]
            if (found != null) {
                if (abs(found.closeAdj - adjusted) > found.closeAdj * ADJUST_TOLERANCE) drifted += key.first
                return@mapNotNull null
            }
            StockDaily(
                symbol = key.first,
                sessionDate = key.second,
                feed = Feeds.SIP,
                openPrice = bar.path("o").asDouble(),
                highPrice = bar.path("h").asDouble(),
                lowPrice = bar.path("l").asDouble(),
                closeRaw = bar.path("c").asDouble(),
                closeAdj = adjusted,
                volume = bar.path("v").asLong(),
                tradeCount = bar.longOrNull("n"),
                vwap = bar.doubleOrNull("vw"),
                receivedAt = context.startedAt,
            )
        }
        stockDailyRepository.saveAll(added)
        context.rowsWritten += added.size
        return drifted
    }

    // 종가 달라졌으면 이력을 받아와서 수정함
    private fun refreshAdjusted(context: RunContext, symbol: String, previous: LocalDate) {
        val rows = stockDailyRepository.findBySymbolOrderBySessionDate(symbol)
        val first = rows.firstOrNull()?.sessionDate ?: return
        val adjusted = mutableMapOf<LocalDate, Double>()
        alpacaClient.forEachPage(
            context,
            "stock_bars_refresh",
            "/v2/stocks/bars",
            mapOf(
                "symbols" to symbol,
                "timeframe" to "1Day",
                "start" to first.toString(),
                "end" to context.session.toString(),
                "feed" to Feeds.SIP,
                "adjustment" to "all",
                "limit" to "10000",
            ),
        ) { _, _, json ->
            json.path("bars").path(symbol).forEach { bar ->
                val date = bar.etDateOrNull("t")
                if (date != null) {
                    adjusted[date] = bar.path("c").asDouble()
                }
            }
        }
        jobRunner.inTransaction(context) {
            for (row in rows) {
                val closeAdj = adjusted[row.sessionDate]
                if (closeAdj != null) {
                    row.closeAdj = closeAdj
                }
            }
            stockDailyRepository.saveAll(rows)
        }
    }

    private companion object {
        const val CHUNK = 100
        const val ADJUST_TOLERANCE = 1e-5
    }
}
