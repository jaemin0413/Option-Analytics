package com.option.domain.market.vo

enum class OptionType { CALL, PUT }

enum class SymbolGroup { INDEX_ETF, SECTOR_ETF, OTHER_ETF, STOCK }

enum class EventType { EARNINGS, FOMC, CPI, JOBS }

object Feeds { // 데이터 출처
    const val PROVIDER_ALPACA = "alpaca"
    const val INDICATIVE = "indicative"
    const val OPRA = "opra"
    const val SIP = "sip"
}

object MacroSeries { // 매크로 관련
    const val VIX = "VIXCLS"
    const val HY_SPREAD = "BAMLH0A0HYM2"
    const val TBILL_3M = "DTB3"
    const val CBOE_TOTAL_PCR = "CBOE_TOTAL_PCR"
}
