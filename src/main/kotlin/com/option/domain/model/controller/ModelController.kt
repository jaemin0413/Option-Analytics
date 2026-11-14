package com.option.domain.model.controller

import com.option.domain.market.vo.SymbolGroup
import com.option.domain.model.dto.response.DashboardResponse
import com.option.domain.model.dto.response.HitRateDailyRow
import com.option.domain.model.dto.response.HitRateResponse
import com.option.domain.model.dto.response.TickerDetailResponse
import com.option.domain.model.dto.response.TickerSearchResponse
import com.option.domain.model.service.DashboardService
import com.option.domain.model.service.HitRateService
import com.option.global.apiPayLoad.response.ApiResponse
import com.option.global.security.annotation.UserId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "모델 결과", description = "대시보드, 종목 상세, 적중률")
@RestController
@RequestMapping("/api")
class ModelController(
    private val dashboardService: DashboardService,
    private val hitRateService: HitRateService,
) {
    @Operation(summary = "시장 대시보드", description = "공개된 최신 발표의 지수·섹터 ETF 카드, 시장심리, 이벤트를 반환합니다.")
    @GetMapping("/dashboard")
    fun getDashboard(): ApiResponse<DashboardResponse> = ApiResponse.ok(dashboardService.getDashboard())

    @Operation(summary = "종목 검색", description = "심볼 앞부분으로 찾습니다.")
    @GetMapping("/tickers")
    fun searchTickers(@RequestParam q: String): ApiResponse<List<TickerSearchResponse>> =
        ApiResponse.ok(dashboardService.search(q))

    @Operation(summary = "종목 상세", description = "예상 범위, 집중 가격대, GEX, PCR, 방향 실험 패널을 반환합니다.")
    @GetMapping("/tickers/{symbol}")
    fun getTicker(@PathVariable symbol: String): ApiResponse<TickerDetailResponse> =
        ApiResponse.ok(dashboardService.getTicker(symbol.uppercase()))

    @Operation(summary = "적중률", description = "가설·범위·거리 지표별 성적. 이벤트일 포함·제외를 함께 반환합니다.")
    @GetMapping("/hit-rates")
    fun getHitRates(
        @UserId userId: Long,
        @RequestParam(required = false) group: SymbolGroup?,
        @RequestParam(required = false) issuedWeekday: Int?,
    ): ApiResponse<HitRateResponse> = ApiResponse.ok(hitRateService.getHitRates(userId, group, issuedWeekday))

    @Operation(summary = "날짜별 적중률", description = "같은 날 여러 종목을 독립 표본으로 보지 않기 위한 날짜별 집계입니다.")
    @GetMapping("/hit-rates/daily")
    fun getDailyHitRates(@UserId userId: Long, @RequestParam subject: String): ApiResponse<List<HitRateDailyRow>> =
        ApiResponse.ok(hitRateService.getDailyHitRates(userId, subject))
}
