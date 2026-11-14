package com.option.global.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("storage")
data class StorageProperties(
    val rawDir: String,
    val stopBackgroundFreeRatio: Double,
    val stopCollectionFreeRatio: Double,
    val minFreeBytes: Long,
)
