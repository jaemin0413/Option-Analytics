package com.option.domain.journal.controller

import com.option.domain.journal.dto.request.JournalRequest
import com.option.domain.journal.dto.response.JournalResponse
import com.option.domain.journal.service.JournalService
import com.option.global.apiPayLoad.response.ApiResponse
import com.option.global.security.annotation.UserId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "저널", description = "장 전 내 판단 기록")
@RestController
@RequestMapping("/api/journals")
class JournalController(
    private val journalService: JournalService,
) {
    @Operation(summary = "저널 저장", description = "종목·세션당 한 건. 정규장 시작 이후에는 수정할 수 없습니다.")
    @PutMapping
    fun save(@UserId userId: Long, @Valid @RequestBody request: JournalRequest): ApiResponse<JournalResponse> =
        ApiResponse.ok(journalService.save(userId, request))

    @Operation(summary = "최근 저널", description = "최근 세션부터 반환합니다.")
    @GetMapping
    fun getRecent(@UserId userId: Long, @RequestParam(defaultValue = "50") limit: Int): ApiResponse<List<JournalResponse>> =
        ApiResponse.ok(journalService.getRecent(userId, limit))
}
