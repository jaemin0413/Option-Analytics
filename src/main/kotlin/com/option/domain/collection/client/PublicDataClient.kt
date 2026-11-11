package com.option.domain.collection.client

import com.option.domain.collection.service.RunContext
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Clock

// 공공 데이터 수집
@Component
class PublicDataClient(
    restClientBuilder: RestClient.Builder,
    private val meterRegistry: MeterRegistry,
    private val clock: Clock,
) {
    private val restClient: RestClient = restClientBuilder.build()

    fun get(
        context: RunContext,
        endpoint: String,
        url: String,
        logParams: Map<String, String> = emptyMap(),
        secretParams: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
    ): RawResponse {
        val builder = UriComponentsBuilder.fromUriString(url)
        for ((key, value) in logParams + secretParams) {
            builder.queryParam(key, URLEncoder.encode(value, StandardCharsets.UTF_8))
        }
        val uri = builder.build(true).toUri()
        val timer = Timer.builder("option.http").tag("endpoint", endpoint).publishPercentileHistogram().register(meterRegistry)
        val body = timer.recordCallable {
            restClient.get()
                .uri(uri)
                .headers { target -> headers.forEach { (key, value) -> target.set(key, value) } }
                .retrieve()
                .body(ByteArray::class.java)
        } ?: ByteArray(0)
        return RawResponse(endpoint, logParams, body, clock.instant())
    }
}
