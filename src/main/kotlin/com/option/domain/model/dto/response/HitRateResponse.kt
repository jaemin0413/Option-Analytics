package com.option.domain.model.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@Schema(description = "적중률 표의 한 행")
data class HitRateRow(
    val subject: String,
    val horizon: String,
    @Schema(description = "PROVIDER(제공자 IV) 또는 BACKFILL(역산 IV, 등급 D). 성적을 섞지 않는다")
    val ivSource: String,
    @Schema(description = "평가 시작일")
    val startDate: LocalDate,
    val total: Long,
    @Schema(description = "채점 수")
    val scored: Long,
    val hits: Long,
    val up: Long,
    val upHits: Long,
    val down: Long,
    val downHits: Long,
    @Schema(description = "판단 보류 수")
    val abstain: Long,
    @Schema(description = "데이터 부족 수")
    val noData: Long,
    val avgWidth: Double?,
    val avgOutsideError: Double?,
    val avgDistance: Double?,
    val avgAbsReturn: Double?,
    val avgRangePct: Double?,
) {
    val hitRate: Double? get() = if (scored == 0L) null else hits.toDouble() / scored
    val signalRate: Double? get() = if (total == 0L) null else (up + down).toDouble() / total
    val abstainRate: Double? get() = if (total == 0L) null else abstain.toDouble() / total
    val missingRate: Double? get() = if (total == 0L) null else noData.toDouble() / total
}

@Schema(description = "이벤트일 포함·제외 성적을 항상 같이 준다")
data class HitRateResponse(
    val all: List<HitRateRow>,
    val excludingEvents: List<HitRateRow>,
)
