package com.option.domain.collection.service

import com.fasterxml.jackson.databind.JsonNode
import com.option.domain.collection.client.AlpacaClient
import com.option.domain.collection.client.longOrNull
import com.option.domain.collection.client.textOrNull
import com.option.domain.collection.entity.RawFile
import com.option.domain.collection.repository.RawFileRepository
import com.option.domain.market.entity.OptionDaily
import com.option.domain.market.repository.OptionDailyRepository
import com.option.domain.market.service.TradingCalendarService
import com.option.domain.market.service.UniverseService
import com.option.domain.market.vo.Feeds
import com.option.domain.market.vo.OptionType
import com.option.domain.market.util.SessionCalendar
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate

// 계약 목록과 미결졔약정 받아오기
@Service
class ContractCollector(
    private val alpacaClient: AlpacaClient,
    private val rawFileStore: RawFileStore,
    private val rawFileRepository: RawFileRepository,
    private val optionDailyRepository: OptionDailyRepository,
    private val universeService: UniverseService,
    private val tradingCalendarService: TradingCalendarService,
    private val jobRunner: JobRunner,
) {

    // 종목 하나씩 순환
    fun collect(context: RunContext) {
        val symbols = universeService.symbols()
        val calendar = tradingCalendarService.calendar()
        context.symbolsTotal += symbols.size
        symbols.forEach { symbol -> jobRunner.isolated(context, symbol) { collectSymbol(context, symbol, calendar) } }
    }

    // 한 종목씩 끊어서 트랜잭션으로 ㅂ다음
    private fun collectSymbol(context: RunContext, symbol: String, calendar: SessionCalendar) {
        val contracts = mutableListOf<JsonNode>()
        val files = mutableListOf<RawFile>()
        var receivedAt = Instant.EPOCH
        alpacaClient.forEachPage(
            context,
            "contracts",
            "/v2/options/contracts",
            mapOf(
                "underlying_symbols" to symbol,
                "status" to "active",
                "limit" to "10000",
                "expiration_date_gte" to context.session.toString(),
                "expiration_date_lte" to context.session.plusYears(3).toString(),
            ),
            trading = true,
        ) { response, page, json ->
            files += rawFileStore.save(context, response, symbol, page)
            json.path("option_contracts").forEach(contracts::add)
            receivedAt = response.receivedAt
        }

        jobRunner.inTransaction(context) {
            rawFileRepository.saveAll(files)
            val written = applyOi(symbol, contracts, context, calendar, receivedAt)
            context.rowsWritten += written
        }
    }

    private fun applyOi(
        underlying: String,
        contracts: List<JsonNode>,
        context: RunContext,
        calendar: SessionCalendar,
        receivedAt: Instant,
    ): Int {
        val session = context.session
        val rows = optionDailyRepository
            .findByUnderlyingAndSessionDateAndFeed(underlying, session, Feeds.INDICATIVE)
            .associateBy { it.contractSymbol }
        val added = mutableListOf<OptionDaily>()
        contracts.forEach { node ->
            val symbol = node.path("symbol").asText()
            var row = rows[symbol]
            if (row == null) {
                row = OptionDaily(
                    contractSymbol = symbol,
                    underlying = underlying,
                    expirationDate = LocalDate.parse(node.path("expiration_date").asText()),
                    strike = node.path("strike_price").asText().toDouble(),
                    optionType = if (node.path("type").asText() == "call") OptionType.CALL else OptionType.PUT,
                    sessionDate = session,
                    provider = Feeds.PROVIDER_ALPACA,
                    feed = Feeds.INDICATIVE,
                    receivedAt = receivedAt,
                )
                added.add(row)
            }
            val oiDateText = node.textOrNull("open_interest_date")
            val oiDate = if (oiDateText != null) LocalDate.parse(oiDateText) else null
            row.openInterest = node.longOrNull("open_interest")
            row.oiAsOfDate = oiDate
            row.oiDelayDays = if (oiDate != null) calendar.countBetween(oiDate, session) else null
        }
        optionDailyRepository.saveAll(added)
        return contracts.size
    }
}
