package com.option.domain.collection.client

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.option.domain.collection.service.RunContext
import com.option.global.properties.AlpacaProperties
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
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
class AlpacaRateGate(alpacaProperties: AlpacaProperties, meterRegistry: MeterRegistry) {
    // 병렬일 때 한도 때문에 기다린 시간
    private val waitTimer = meterRegistry.timer("option.alpaca.gate.wait")
    private val intervalMillis = alpacaProperties.minCallInterval.toMillis()
    private var nextAllowed = 0L

    // 잠금 안에서는 내 차례 시각만 예약하고, 기다리는 건 잠금 밖에서 한다. 그래야 기다린 시간이 전부 지표에 잡힌다
    fun await() {
        val now = System.currentTimeMillis()
        val slot = synchronized(this) {
            val slot = maxOf(nextAllowed, now)
            nextAllowed = slot + intervalMillis
            slot
        }
        val wait = slot - now
        waitTimer.record(Duration.ofMillis(wait))
        if (wait > 0) Thread.sleep(wait)
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

    // 호출. 429면 한도가 풀릴 때까지 기다렸다가 같은 호출을 다시 한다
    private fun get(context: RunContext, endpoint: String, url: String, params: Map<String, String>): RawResponse {
        repeat(RATE_LIMIT_RETRIES) { attempt ->
            try {
                return call(context, endpoint, url, params)
            } catch (e: HttpClientErrorException.TooManyRequests) {
                val wait = untilReset(e)
                meterRegistry.counter("option.alpaca.retry", "endpoint", endpoint).increment()
                logger.warn("job={} endpoint={} 429, {}ms 뒤 다시 호출 ({}/{})", context.job.name, endpoint, wait, attempt + 1, RATE_LIMIT_RETRIES)
                Thread.sleep(wait)
            }
        }
        return call(context, endpoint, url, params)
    }

    // X-Ratelimit-Reset(초 단위 epoch)까지 남은 시간. 헤더가 없으면 설정한 고정 대기
    private fun untilReset(e: HttpClientErrorException): Long {
        val reset = e.responseHeaders?.getFirst("X-Ratelimit-Reset")?.toLongOrNull()
            ?: return alpacaProperties.rateLimitWait.toMillis()
        return (reset * 1000 - clock.millis()).coerceIn(0, 60_000)
    }

    private fun call(context: RunContext, endpoint: String, url: String, params: Map<String, String>): RawResponse {
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

    private companion object {
        // 호출 하나당 429 재시도 횟수
        const val RATE_LIMIT_RETRIES = 7
    }
}
