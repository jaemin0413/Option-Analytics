package com.option.global.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("alpaca")
class AlpacaProperties(
    val keyId: String,
    val secretKey: String,
    val minCallInterval: Duration,
    val dataUrl: String = "https://data.alpaca.markets",
    val tradingUrl: String = "https://paper-api.alpaca.markets",
    // 종목을 동시에 몇 개 받을지
    val concurrency: Int = 3,
    // 429 응답에 X-Ratelimit-Reset이 없을 때 다시 호출하기 전까지 기다릴 시간
    val rateLimitWait: Duration = Duration.ofSeconds(10),
)
