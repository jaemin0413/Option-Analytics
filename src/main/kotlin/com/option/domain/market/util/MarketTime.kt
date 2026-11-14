package com.option.domain.market.util

import java.time.ZoneId

// 시장 숫자 꼬일까봐..
object MarketTime {
    val ET: ZoneId = ZoneId.of("America/New_York")
    val KST: ZoneId = ZoneId.of("Asia/Seoul")
}
