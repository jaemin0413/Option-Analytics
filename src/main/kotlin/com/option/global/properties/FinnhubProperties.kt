package com.option.global.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("finnhub")
class FinnhubProperties(
    val apiKey: String,
    val baseUrl: String = "https://finnhub.io/api/v1",
)
