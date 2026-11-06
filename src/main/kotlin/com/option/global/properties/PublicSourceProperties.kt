package com.option.global.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("public-source")
data class PublicSourceProperties(
    val spdrHoldingsUrl: String = "https://www.ssga.com/us/en/intermediary/library-content/products/fund-data/etfs/us/holdings-daily-us-en-spy.xlsx",
    val cboeDailyUrl: String = "https://cdn.cboe.com/data/us/options/market_statistics/daily",
    val fedCalendarUrl: String = "https://www.federalreserve.gov/json/calendar.json",
)
