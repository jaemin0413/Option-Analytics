package com.option.domain.collection.service

import com.fasterxml.jackson.databind.JsonNode
import com.option.domain.collection.client.AlpacaClient
import com.option.domain.collection.client.doubleOrNull
import com.option.domain.collection.client.etDateOrNull
import com.option.domain.collection.client.longOrNull
import com.option.domain.collection.entity.BackfillTarget
import com.option.domain.collection.entity.RawFile
import com.option.domain.collection.repository.BackfillTargetRepository
import com.option.domain.collection.repository.RawFileBatchRepository
import com.option.domain.market.entity.OptionDaily
import com.option.domain.market.repository.OptionDailyBatchRepository
import com.option.domain.market.vo.Feeds
import com.option.domain.market.util.OccSymbol
import com.option.global.properties.JobProperties
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference

// 백필 작업, 2년치 데이터 다 때려박을 예정.
// 안정성을 위해 배치 적용
@Service
class BackfillCollector(
    private val alpacaClient: AlpacaClient,
    private val rawFileStore: RawFileStore,
    private val rawFileBatchRepository: RawFileBatchRepository,
    private val optionDailyBatchRepository: OptionDailyBatchRepository,
    private val backfillTargetRepository: BackfillTargetRepository,
    private val backfillWindow: BackfillWindow,
    private val diskGuard: DiskGuard,
    private val jobProperties: JobProperties,
    private val jobRunner: JobRunner,
    meterRegistry: MeterRegistry,
) {
    private val progress = AtomicReference(0.0)

    init {
        Gauge.builder("option.backfill.progress") { progress.get() }.register(meterRegistry)
    }

    // 만료된 계약이랑 살아 있는 계약 다 모으고 표준 심볼만 남기기
    fun listContracts(context: RunContext, underlying: String, asOf: LocalDate): List<String> {
        val symbols = mutableListOf<String>()
        val conditions = listOf(
            mapOf(
                "status" to "inactive",
                "expiration_date_gte" to jobProperties.backfillExpirationStart.toString(),
                "expiration_date_lte" to asOf.toString(),
            ),
            mapOf("status" to "active", "expiration_date_gte" to asOf.toString()),
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
                jobRunner.inTransaction(context) { rawFileBatchRepository.insertAll(listOf(file)) }
                json.path("option_contracts").forEach { symbols += it.path("symbol").asText() }
            }
        }
        val standard = symbols.distinct().filter { OccSymbol.parse(it)?.isStandardFor(underlying) == true }
        context.addNote("$underlying 계약 ${standard.size}개, 비표준 제외 ${symbols.distinct().size - standard.size}개")
        return standard
    }

    // 계약 한 묶음의 일봉을 받아 저장
    fun fetch(context: RunContext, target: BackfillTarget, end: LocalDate): BackfillChunk {
        waitOutPauseWindow()
        if (diskGuard.isTooLow(context.job)) throw IllegalStateException("디스크 여유 부족으로 ${target.underlying}:${target.groupIndex}에서 멈춤")
        val files = mutableListOf<RawFile>()
        val rows = mutableListOf<OptionDaily>()
        alpacaClient.forEachPage(
            context,
            "option_bars",
            "/v1beta1/options/bars",
            mapOf(
                "symbols" to target.contracts,
                "timeframe" to "1Day",
                "start" to jobProperties.backfillStart.toString(),
                "end" to end.toString(),
                "limit" to "10000",
            ),
        ) { response, page, json ->
            files += rawFileStore.save(context, response, "${target.underlying}-${target.groupIndex}", page)
            json.path("bars").fields().forEach { (contract, bars) ->
                for (bar in bars) {
                    val row = toRow(target.underlying, contract, bar, response.receivedAt)
                    if (row != null) {
                        rows.add(row)
                    }
                }
            }
        }
        return BackfillChunk(target.id!!, files, rows)
    }

    // 저장할 데이터들과 배치 진행상황을 같이 저장
    fun write(context: RunContext, chunks: List<BackfillChunk>) {
        if (chunks.isEmpty()) return
        val rows = chunks.flatMap { it.rows }
        jobRunner.inTransaction(context) {
            rawFileBatchRepository.insertAll(chunks.flatMap { it.files })
            optionDailyBatchRepository.insertAll(rows)
        }
        context.symbolsOk += chunks.size
        context.rowsWritten += rows.size
        progress.set(backfillTargetRepository.countByIdLessThanEqual(chunks.last().targetId).toDouble() / backfillTargetRepository.count())
    }

    private fun waitOutPauseWindow() {
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
        const val PAUSE_POLL_MILLIS = 60_000L
    }
}

// 계약 묶음 하나를 받은 결과
class BackfillChunk(
    val targetId: Long,
    val files: List<RawFile>,
    val rows: List<OptionDaily>,
)
