package com.option.domain.collection.service

import com.option.domain.collection.client.RawResponse
import com.option.domain.collection.entity.RawFile
import com.option.global.properties.StorageProperties
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.util.HexFormat
import java.util.zip.GZIPOutputStream

// 원본 파일도 같이 저장
@Component
class RawFileStore(
    private val storageProperties: StorageProperties,
    private val meterRegistry: MeterRegistry,
    private val clock: Clock,
) {
    // 응답 파일은 gz로 저장
    fun save(context: RunContext, response: RawResponse, symbol: String, page: Int): RawFile {
        val started = clock.millis()
        val root = storageProperties.rawDir
        val dir = Files.createDirectories(Path.of(root, context.session.toString(), context.job.name))
        val file = dir.resolve("${safe(symbol)}_${response.endpoint}_${page}_${response.receivedAt.toEpochMilli()}.json.gz")
        GZIPOutputStream(Files.newOutputStream(file)).use { it.write(response.body) }
        val size = Files.size(file)

        val elapsed = clock.millis() - started
        meterRegistry.timer("option.raw.save", "endpoint", response.endpoint).record(Duration.ofMillis(elapsed))
        return RawFile(
            job = context.job.name,
            sessionDate = context.session,
            endpoint = response.endpoint,
            symbol = symbol,
            page = page,
            path = file.toString(),
            sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(response.body)),
            bytes = size,
            requestParams = response.params.toString(),
            receivedAt = response.receivedAt,
        )
    }

    private fun safe(name: String) = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
