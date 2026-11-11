package com.option.domain.collection.service

import com.option.domain.collection.vo.DiskRule
import com.option.domain.collection.job.Job
import com.option.global.properties.StorageProperties
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path

// 디스크 꽉 찰 것 같으면 정지
@Component
class DiskGuard(
    private val storageProperties: StorageProperties,
    meterRegistry: MeterRegistry,
) {
    init {
        Gauge.builder("option.disk.free.ratio") { freeRatio() }.register(meterRegistry)
    }

    fun isTooLow(job: Job): Boolean {
        val storage = storageProperties
        val threshold = when (job.diskRule) {
            DiskRule.BACKGROUND -> storage.stopBackgroundFreeRatio
            DiskRule.COLLECTION -> storage.stopCollectionFreeRatio
            DiskRule.NONE -> return false
        }
        return freeRatio() < threshold || usableBytes() < storage.minFreeBytes
    }

    private fun store() = Files.getFileStore(root())

    private fun root(): Path = Files.createDirectories(Path.of(storageProperties.rawDir))

    private fun usableBytes(): Long = store().usableSpace

    // 디스크 여유 비율 구하기
    private fun freeRatio(): Double {
        val store = store()
        return store.usableSpace.toDouble() / store.totalSpace
    }
}
