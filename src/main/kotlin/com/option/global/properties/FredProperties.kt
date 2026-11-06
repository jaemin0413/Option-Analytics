package com.option.global.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("fred")
class FredProperties(
    val apiKey: String,
    val baseUrl: String = "https://api.stlouisfed.org/fred",
)
