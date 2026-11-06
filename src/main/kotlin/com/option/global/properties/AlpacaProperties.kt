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
)
