package com.option.domain.collection.service

import com.option.domain.collection.job.Job
import java.time.Instant
import java.time.LocalDate

// 작업상태 기록용
class RunContext(
    val job: Job, // 원본 파일 값
    val session: LocalDate, // 어느 거래일 데이터를 받을지
    val startedAt: Instant, // 저장 파일의 receivedAt
) {
    var symbolsTotal: Int = 0
    var symbolsOk: Int = 0
    var symbolsFailed: Int = 0
    var rowsWritten: Long = 0

    // 시간이나 디스크 때문에 끝까지 못 갔으면 true
    var stoppedEarly: Boolean = false

    private val notes = mutableListOf<String>()

    // 끝날 때 로그에 같이 찍을 메모
    fun addNote(text: String) {
        notes += text
    }

    fun notes(): String = notes.joinToString("; ")
}
