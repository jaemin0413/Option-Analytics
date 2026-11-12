package com.option.domain.collection.service

import com.fasterxml.jackson.databind.JsonNode
import com.option.domain.collection.client.AlpacaClient
import com.option.domain.collection.client.doubleOrNull
import com.option.domain.collection.client.etDateOrNull
import com.option.domain.collection.client.instantOrNull
import com.option.domain.collection.client.longOrNull
import com.option.domain.collection.entity.RawFile
import com.option.domain.collection.repository.RawFileRepository
import com.option.domain.market.entity.OptionDaily
import com.option.domain.market.repository.OptionDailyRepository
import com.option.domain.market.service.UniverseService
import com.option.domain.market.vo.Feeds
import com.option.domain.market.util.OccSymbol
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate

// 전 종목 옵션 스냅샷 수집
@Service
class SnapshotCollector(
    private val alpacaClient: AlpacaClient,
    private val rawFileStore: RawFileStore,
    private val rawFileRepository: RawFileRepository,
    private val optionDailyRepository: OptionDailyRepository,
    private val universeService: UniverseService,
    private val jobRunner: JobRunner,
) {
    // 종목을 하나씩 돌면서 스냅샷 받기
    fun collect(context: RunContext) {
        val symbols = universeService.symbols()
        context.symbolsTotal = symbols.size
        symbols.forEach { symbol -> jobRunner.isolated(context, symbol) { collectSymbol(context, symbol) } }
    }

    // 종목별로 페이지 모으고 저장
    private fun collectSymbol(context: RunContext, symbol: String) {
        val rows = mutableListOf<OptionDaily>()
        val files = mutableListOf<RawFile>()
        alpacaClient.forEachPage(
            context,
            "snapshots",
            "/v1beta1/options/snapshots/$symbol",
            mapOf("feed" to Feeds.INDICATIVE, "limit" to "1000"),
        ) { response, page, json ->
            files += rawFileStore.save(context, response, symbol, page)
            json.path("snapshots").fields().forEach { (contract, snapshot) ->
                val row = toRow(symbol, contract, snapshot, context.session, response.receivedAt)
                if (row != null) {
                    rows.add(row)
                }
            }
        }
        jobRunner.inTransaction(context) {
            rawFileRepository.saveAll(files)
            optionDailyRepository.saveAll(rows)
        }
        context.rowsWritten += rows.size
    }

    companion object {
        fun toRow(
            underlying: String,
            contract: String,
            snapshot: JsonNode,
            session: LocalDate,
            receivedAt: Instant,
            ): OptionDaily? {
            val occ = OccSymbol.parse(contract) ?: return null
            val bar = snapshot.path("dailyBar")
            val quote = snapshot.path("latestQuote")
            val greeks = snapshot.path("greeks")
            return OptionDaily(
                contractSymbol = contract,
                underlying = underlying,
                expirationDate = occ.expiration,
                strike = occ.strike,
                optionType = occ.type,
                sessionDate = session,
                provider = Feeds.PROVIDER_ALPACA,
                feed = Feeds.INDICATIVE,
                receivedAt = receivedAt,
                barDate = bar.etDateOrNull("t"),
                openPrice = bar.doubleOrNull("o"),
                highPrice = bar.doubleOrNull("h"),
                lowPrice = bar.doubleOrNull("l"),
                closePrice = bar.doubleOrNull("c"),
                volume = bar.longOrNull("v"),
                tradeCount = bar.longOrNull("n"),
                vwap = bar.doubleOrNull("vw"),
                bid = quote.doubleOrNull("bp"),
                ask = quote.doubleOrNull("ap"),
                lastPrice = snapshot.path("latestTrade").doubleOrNull("p"),
                impliedVol = snapshot.doubleOrNull("impliedVolatility"),
                delta = greeks.doubleOrNull("delta"),
                gamma = greeks.doubleOrNull("gamma"),
                theta = greeks.doubleOrNull("theta"),
                vega = greeks.doubleOrNull("vega"),
                rho = greeks.doubleOrNull("rho"),
                priceAsOf = quote.instantOrNull("t"),
            )
        }
    }
}
