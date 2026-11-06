package com.option.global.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("universe")
data class UniverseProperties(
    val tradeSymbols: List<String>,
    val backfillSymbols: List<String>,
    // 이거 빼면 S&P 500과 아래 ETF 전부 수집
    val only: List<String> = emptyList(),
    val indexEtfs: List<String> = listOf("SPY", "QQQ", "IWM", "DIA"),
    val sectorEtfs: List<String> = listOf("XLB", "XLC", "XLE", "XLF", "XLI", "XLK", "XLP", "XLRE", "XLU", "XLV", "XLY"),
    val otherEtfs: List<String> = listOf("VXX", "UVXY", "SVXY", "GLD", "TLT", "HYG"),
)
