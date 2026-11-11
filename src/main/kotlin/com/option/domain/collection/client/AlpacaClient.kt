package com.option.domain.collection.client

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.option.domain.collection.service.RunContext
import com.option.global.properties.AlpacaProperties
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.time.Instant

data class RawResponse(
    val endpoint: String,
    val params: Map<String, String>,
    val body: ByteArray,
    val receivedAt: Instant,
)

// 분당 200회 한도
@Component
class AlpacaRateGate(alpacaProperties: AlpacaProperties) {
    private val intervalMillis = alpacaProperties.minCallInterval.toMillis()
    private var nextAllowed = 0L

    // 시간간격 두기
    @Synchronized
    fun await() {
        val wait = nextAllowed - System.currentTimeMillis()
        if (wait > 0) Thread.sleep(wait)
        nextAllowed = System.currentTimeMillis() + intervalMillis
    }
}

@Component
class AlpacaClient(
    restClientBuilder: RestClient.Builder,
    private val alpacaProperties: AlpacaProperties,
    private val gate: AlpacaRateGate,
    private val objectMapper: ObjectMapper,
    private val meterRegistry: MeterRegistry,
    private val clock: Clock,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val restClient: RestClient = restClientBuilder
        .defaultHeader("APCA-API-KEY-ID", alpacaProperties.keyId)
        .defaultHeader("APCA-API-SECRET-KEY", alpacaProperties.secretKey)
        .build()

    // 시세 api 호출
    fun getData(context: RunContext, endpoint: String, path: String, params: Map<String, String>): RawResponse =
        get(context, endpoint, alpacaProperties.dataUrl + path, params)

    // 트레이딩 api 호출
    fun getTrading(context: RunContext, endpoint: String, path: String, params: Map<String, String>): RawResponse =
        get(context, endpoint, alpacaProperties.tradingUrl + path, params)

    fun forEachPage(
        context: RunContext,
        endpoint: String,
        path: String,
        params: Map<String, String>,
        trading: Boolean = false,
        handle: (RawResponse, Int, JsonNode) -> Unit,
    ) {
        var token: String? = null
        var page = 0
        do {
            val current = if (token == null) params else params + ("page_token" to token)
            val response = if (trading) {
                getTrading(context, endpoint, path, current)
            } else {
                getData(context, endpoint, path, current)
            }
            val json = parse(context, response)
            handle(response, page, json)
            val nextToken = json.path("next_page_token")
            token = if (nextToken.isMissingNode || nextToken.isNull) null else nextToken.asText()
            page++
        } while (token != null)
    }

    // 파싱에 걸린 시간 측정
    fun parse(context: RunContext, response: RawResponse): JsonNode {
        val started = clock.millis()
        val json = objectMapper.readTree(response.body)
        val elapsed = clock.millis() - started
        meterRegistry.timer("option.parse", "endpoint", response.endpoint).record(Duration.ofMillis(elapsed))
        return json
    }

    // 호출
    private fun get(context: RunContext, endpoint: String, url: String, params: Map<String, String>): RawResponse {
        gate.await()
        val builder = UriComponentsBuilder.fromUriString(url)
        for ((key, value) in params) {
            builder.queryParam(key, URLEncoder.encode(value, StandardCharsets.UTF_8))
        }
        val uri = builder.build(true).toUri()
        val started = clock.millis()
        val body = httpTimer(endpoint).recordCallable {
            restClient.get().uri(uri).retrieve().body(ByteArray::class.java)
        } ?: ByteArray(0)
        val elapsed = clock.millis() - started
        logger.debug("job={} endpoint={} params={} ms={}", context.job.name, endpoint, params, elapsed)
        return RawResponse(endpoint, params, body, clock.instant())
    }

    private fun httpTimer(endpoint: String): Timer =
        Timer.builder("option.http").tag("endpoint", endpoint).publishPercentileHistogram().register(meterRegistry)
}
