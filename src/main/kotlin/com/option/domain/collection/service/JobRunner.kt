package com.option.domain.collection.service

import com.fasterxml.jackson.core.JsonProcessingException
import com.option.domain.collection.vo.FailureCategory
import com.option.domain.collection.job.Job
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClientException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.http.HttpTimeoutException
import java.time.Clock
import java.time.Duration
import java.time.LocalDate

// 작업 돌리는 코드
@Service
class JobRunner(
    private val transactionTemplate: TransactionTemplate,
    private val diskGuard: DiskGuard,
    private val meterRegistry: MeterRegistry,
    private val clock: Clock,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun start(job: Job, session: LocalDate): RunContext = RunContext(job, session, clock.instant())

    // 작업 돌리고 로깅
    fun execute(context: RunContext, body: (RunContext) -> Unit = context.job::run) {
        val sample = meterRegistry.timer("option.job", "job", context.job.name)
        try {
            if (diskGuard.isTooLow(context.job)) {
                recordFailure(context, "DISK", FailureCategory.DISK, "디스크 여유 부족으로 시작하지 않음")
                return
            }
            body(context)
        } catch (e: Exception) {
            recordFailure(context, "JOB", classify(e), e.message)
            logger.error("job={} session={} 실패", context.job.name, context.session, e)
        } finally {
            sample.record(Duration.between(context.startedAt, clock.instant()))
            logger.info(
                "job={} session={} ok={} failed={} rows={} stoppedEarly={} {}",
                context.job.name, context.session, context.symbolsOk, context.symbolsFailed,
                context.rowsWritten, context.stoppedEarly, context.notes(),
            )
        }
    }

    // 종목 하나 못 받아도 다른 종목은 받아오게끔
    fun isolated(context: RunContext, symbol: String, body: () -> Unit) {
        try {
            body()
            context.symbolsOk++
        } catch (e: Exception) {
            context.symbolsFailed++
            recordFailure(context, symbol, classify(e), e.message)
        }
    }

    // 적재 측정용
    fun <T> inTransaction(context: RunContext, body: () -> T): T {
        val started = clock.millis()
        try {
            return transactionTemplate.execute { body() } as T
        } finally {
            meterRegistry.timer("option.db.load", "job", context.job.name)
                .record(Duration.ofMillis(clock.millis() - started))
        }
    }

    // 실패 로깅용
    fun recordFailure(context: RunContext, symbol: String, category: FailureCategory, message: String?) {
        meterRegistry.counter("option.failure", "job", context.job.name, "category", category.name).increment()
        logger.warn("job={} session={} symbol={} category={} {}", context.job.name, context.session, symbol, category, sanitize(message))
    }

    // 예외 나누기
    private fun classify(e: Exception): FailureCategory {
        val causes = generateSequence<Throwable>(e) { it.cause }
        return when {
            causes.any { it is HttpTimeoutException || it is SocketTimeoutException } -> FailureCategory.TIMEOUT
            e is RestClientException && causes.any { it is IOException } -> FailureCategory.NETWORK
            e is HttpClientErrorException.TooManyRequests -> FailureCategory.RATE_LIMIT
            e is HttpServerErrorException -> FailureCategory.SERVER_ERROR
            e is HttpClientErrorException -> FailureCategory.CLIENT_ERROR
            e is JsonProcessingException -> FailureCategory.PARSE
            e is DataIntegrityViolationException -> FailureCategory.DB_CONSTRAINT
            else -> FailureCategory.OTHER
        }
    }

    private fun sanitize(message: String?): String? = message?.replace(Regex("api_key=[^&\\s\"]+"), "api_key=***")
}
