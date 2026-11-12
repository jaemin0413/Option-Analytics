package com.option.domain.collection.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.option.domain.collection.client.PublicDataClient
import com.option.domain.collection.client.RawResponse
import com.option.domain.collection.repository.RawFileRepository
import com.option.domain.market.entity.EventCalendar
import com.option.domain.market.entity.MacroDaily
import com.option.domain.market.repository.EventCalendarRepository
import com.option.domain.market.repository.MacroDailyRepository
import com.option.domain.market.service.TradingCalendarService
import com.option.domain.market.service.UniverseService
import com.option.domain.market.vo.EventType
import com.option.domain.market.vo.MacroSeries
import com.option.domain.market.util.SessionCalendar
import com.option.global.apiPayLoad.error.CollectionErrorCode
import com.option.global.apiPayLoad.exception.BusinessException
import com.option.global.properties.FinnhubProperties
import com.option.global.properties.FredProperties
import com.option.global.properties.PublicSourceProperties
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException

// 외부 소스
@Service
class ExternalSourceCollector(
    private val publicDataClient: PublicDataClient,
    private val rawFileStore: RawFileStore,
    private val rawFileRepository: RawFileRepository,
    private val macroDailyRepository: MacroDailyRepository,
    private val eventCalendarRepository: EventCalendarRepository,
    private val universeService: UniverseService,
    private val tradingCalendarService: TradingCalendarService,
    private val finnhubProperties: FinnhubProperties,
    private val fredProperties: FredProperties,
    private val publicSourceProperties: PublicSourceProperties,
    private val objectMapper: ObjectMapper,
    private val jobRunner: JobRunner,
) {
    // 외부 소스를 순서대로 받기
    fun collect(context: RunContext, today: LocalDate) {
        syncInstruments(context, today)
        collectFred(context, today.minusDays(FRED_DAILY_LOOKBACK_DAYS))
        jobRunner.isolated(context, "CBOE:PCR") { collectCboePcr(context, context.session) }
        jobRunner.isolated(context, "FED:FOMC") { collectFomc(context, today) }
        collectFredReleases(context, today)
        collectEarnings(context, today)
    }

    // 종목 양식에 맞추기
    fun syncInstruments(context: RunContext, today: LocalDate) {
        jobRunner.isolated(context, "SPDR:SPY") {
            val response = publicDataClient.get(context, "spdr_holdings", publicSourceProperties.spdrHoldingsUrl)
            val holdings = parseHoldings(response.body)
            // 파일 형식이 바뀌어서 몇 개만 읽혔을 때 구성종목이 날아가지 않게 막는다.
            if (holdings.size < MIN_HOLDINGS) throw BusinessException(CollectionErrorCode.HOLDINGS_TOO_FEW)
            jobRunner.inTransaction(context) {
                rawFileRepository.save(rawFileStore.save(context, response, "SPY", 0))
                universeService.seedEtfs()
                universeService.syncSp500(holdings, today)
            }
        }
    }

    // vix랑 ㅎ하이일드 받기
    fun collectFred(context: RunContext, start: LocalDate) {
        if (fredProperties.apiKey.isBlank()) {
            context.addNote("FRED 키 없음: VIX·HY·T-bill 건너뜀")
            return
        }
        listOf(MacroSeries.VIX, MacroSeries.HY_SPREAD, MacroSeries.TBILL_3M).forEach { series ->
            jobRunner.isolated(context, "FRED:$series") {
                val response = publicDataClient.get(
                    context,
                    "fred_observations",
                    "${fredProperties.baseUrl}/series/observations",
                    mapOf("series_id" to series, "file_type" to "json", "observation_start" to start.toString()),
                    mapOf("api_key" to fredProperties.apiKey),
                )
                val rows = objectMapper.readTree(response.body).path("observations").mapNotNull { node ->
                    // FRED는 값이 없는 날을 점 하나로 준다.
                    val value = node.path("value").asText().toDoubleOrNull()
                    if (value == null) null else LocalDate.parse(node.path("date").asText()) to value
                }
                saveMacro(context, series, "fred", rows, response)
            }
        }
    }

    // 풋콜 받기
    fun collectCboePcr(context: RunContext, date: LocalDate) {
        val response = publicDataClient.get(
            context,
            "cboe_daily",
            "${publicSourceProperties.cboeDailyUrl}/${date}_daily_options",
        )
        val value = parseTotalPcr(objectMapper.readTree(response.body))
        if (value != null) {
            saveMacro(context, MacroSeries.CBOE_TOTAL_PCR, "cboe", listOf(date to value), response, date.toString())
        }
    }

    // fomc 일정 저장
    private fun collectFomc(context: RunContext, today: LocalDate) {
        val response = publicDataClient.get(context, "fed_calendar", publicSourceProperties.fedCalendarUrl)
        val text = String(response.body, Charsets.UTF_8).removePrefix("\uFEFF")
        val events = parseFomc(objectMapper.readTree(text)).filter { !it.isBefore(today) }
        saveEvents(context, response, events.map { EventCalendar(eventDate = it, eventType = EventType.FOMC, symbol = "", title = "FOMC Meeting", source = "fed") }, "fed")
    }

    // cpi랑 고용발표일
    private fun collectFredReleases(context: RunContext, today: LocalDate) {
        if (fredProperties.apiKey.isBlank()) return
        listOf(EventType.CPI to CPI_RELEASE_ID, EventType.JOBS to JOBS_RELEASE_ID).forEach { (type, releaseId) ->
            jobRunner.isolated(context, "FRED:release:$type") {
                val response = publicDataClient.get(
                    context,
                    "fred_release_dates",
                    "${fredProperties.baseUrl}/release/dates",
                    mapOf(
                        "release_id" to releaseId.toString(),
                        "file_type" to "json",
                        "include_release_dates_with_no_data" to "true",
                        "realtime_start" to today.toString(),
                        "realtime_end" to "9999-12-31",
                        "sort_order" to "asc",
                    ),
                    mapOf("api_key" to fredProperties.apiKey),
                )
                val dates = objectMapper.readTree(response.body).path("release_dates")
                    .map { LocalDate.parse(it.path("date").asText()) }
                    .filter { !it.isBefore(today) }
                saveEvents(context, response, dates.map { EventCalendar(eventDate = it, eventType = type, symbol = "", title = type.name, source = "fred") }, type.name)
            }
        }
    }

    fun collectEarnings(context: RunContext, today: LocalDate) {
        if (finnhubProperties.apiKey.isBlank()) {
            context.addNote("Finnhub 키 없음: 실적일 건너뜀")
            return
        }
        val universe = universeService.symbols().toSet()
        val sessions = tradingCalendarService.calendar()
        val fetched = mutableListOf<EventCalendar>()
        var completeDays = 0
        (0..EARNINGS_LOOKAHEAD_DAYS).map(today::plusDays).forEach { date ->
            jobRunner.isolated(context, "FINNHUB:$date") {
                val response = publicDataClient.get(
                    context,
                    "finnhub_earnings",
                    "${finnhubProperties.baseUrl}/calendar/earnings",
                    mapOf("from" to date.toString(), "to" to date.toString()),
                    headers = mapOf("X-Finnhub-Token" to finnhubProperties.apiKey),
                )
                val calendar = objectMapper.readTree(response.body).path("earningsCalendar")
                val truncated = calendar.size() >= FINNHUB_MAX_ROWS
                if (truncated) context.addNote("Finnhub $date 응답이 잘렸을 수 있음")
                val events = calendar.flatMap { node ->
                    shareClasses(node.path("symbol").asText())
                        .filter { it in universe }
                        .map { toEarningsEvent(node, sessions, it) }
                }
                saveEvents(context, response, events, date.toString())
                fetched += events
                if (!truncated) completeDays++
            }
        }
        if (completeDays.toLong() == EARNINGS_LOOKAHEAD_DAYS + 1) {
            removeMovedEarnings(context, sessions, today, fetched)
        }
    }

    private fun removeMovedEarnings(context: RunContext, sessions: SessionCalendar, today: LocalDate, fetched: List<EventCalendar>) {
        val from = (sessions.next(today.minusDays(1)) ?: today).plusDays(1)
        val keep = fetched.map { Triple(it.eventDate, it.eventType, it.symbol) }.toSet()
        jobRunner.inTransaction(context) {
            val moved = eventCalendarRepository.findByEventDateBetweenOrderByEventDate(from, today.plusDays(EARNINGS_LOOKAHEAD_DAYS))
                .filter { it.eventType == EventType.EARNINGS && it.source == "finnhub" && Triple(it.eventDate, it.eventType, it.symbol) !in keep }
            if (moved.isNotEmpty()) {
                eventCalendarRepository.deleteAll(moved)
                context.addNote("바뀐 실적일 ${moved.size}건 삭제")
            }
        }
    }

    // 거시 지표를 없는 날짜만 저장
    private fun saveMacro(
        context: RunContext,
        series: String,
        provider: String,
        rows: List<Pair<LocalDate, Double>>,
        response: RawResponse,
        fileKey: String = series,
    ) {
        jobRunner.inTransaction(context) {
            rawFileRepository.save(rawFileStore.save(context, response, fileKey, 0))
            val known = macroDailyRepository.findDates(series).toSet()
            val added = rows.filter { it.first !in known }
                .map { MacroDaily(series = series, obsDate = it.first, value = it.second, provider = provider, receivedAt = response.receivedAt) }
            macroDailyRepository.saveAll(added)
            context.rowsWritten += added.size
        }
    }

    // 일정을 없는 것만 저장
    private fun saveEvents(
        context: RunContext,
        response: RawResponse,
        events: List<EventCalendar>,
        fileKey: String,
    ) {
        jobRunner.inTransaction(context) {
            rawFileRepository.save(rawFileStore.save(context, response, fileKey, 0))
            val distinctEvents = events.distinctBy { Triple(it.eventDate, it.eventType, it.symbol) }
            val added = distinctEvents.filterNot { event ->
                eventCalendarRepository.existsByEventDateAndEventTypeAndSymbol(event.eventDate, event.eventType, event.symbol)
            }
            eventCalendarRepository.saveAll(added)
            context.rowsWritten += added.size
        }
    }

    companion object {
        private const val MIN_HOLDINGS = 400
        private const val FRED_DAILY_LOOKBACK_DAYS = 30L
        private const val EARNINGS_LOOKAHEAD_DAYS = 30L
        private const val FINNHUB_MAX_ROWS = 1500
        private const val CPI_RELEASE_ID = 10
        private const val JOBS_RELEASE_ID = 50

        fun toEarningsEvent(
            node: JsonNode,
            sessions: SessionCalendar,
            symbol: String = node.path("symbol").asText(),
        ): EventCalendar {
            val announced = LocalDate.parse(node.path("date").asText())
            val afterClose = node.path("hour").asText() == "amc"
            val eventDate = if (afterClose) sessions.next(announced) ?: announced else announced
            val title = if (afterClose) "실적 발표 ($announced 장 마감 후)" else "실적 발표"
            return EventCalendar(eventDate = eventDate, eventType = EventType.EARNINGS, symbol = symbol, title = title, source = "finnhub")
        }

        private val SHARE_CLASS_PAIRS = listOf("NWS" to "NWSA", "GOOG" to "GOOGL", "FOX" to "FOXA")

        fun shareClasses(symbol: String): List<String> =
            SHARE_CLASS_PAIRS.firstOrNull { symbol == it.first || symbol == it.second }?.toList() ?: listOf(symbol)

        fun parseHoldings(xlsx: ByteArray): Map<String, String> {
            XSSFWorkbook(ByteArrayInputStream(xlsx)).use { workbook ->
                val sheet = workbook.getSheetAt(0)
                val result = linkedMapOf<String, String>()
                var inTable = false
                for (row in sheet) {
                    val first = row.getCell(0)?.toString()?.trim().orEmpty()
                    val second = row.getCell(1)?.toString()?.trim().orEmpty()
                    if (first == "Name" && second == "Ticker") {
                        inTable = true
                        continue
                    }
                    if (!inTable) continue
                    if (first.isEmpty()) break
                    if (second.matches(Regex("[A-Z]+(\\.[A-Z])?"))) result[second] = first
                }
                return result
            }
        }


        fun parseTotalPcr(json: JsonNode): Double? = json.path("ratios")
            .firstOrNull { it.path("name").asText() == "TOTAL PUT/CALL RATIO" }
            ?.path("value")?.asText()?.toDoubleOrNull()

        fun parseFomc(json: JsonNode): List<LocalDate> {
            val dates = mutableListOf<LocalDate>()
            for (event in json.path("events")) {
                val isFomc = event.path("type").asText() == "FOMC"
                val isMeeting = event.path("title").asText().trim().equals("FOMC Meeting", ignoreCase = true)
                if (!isFomc || !isMeeting) continue

                val month = try {
                    YearMonth.parse(event.path("month").asText())
                } catch (e: DateTimeParseException) {
                    continue
                }
                val lastDay = Regex("\\d+").findAll(event.path("days").asText()).lastOrNull() ?: continue
                dates.add(month.atDay(lastDay.value.toInt()))
            }
            return dates
        }
    }
}
