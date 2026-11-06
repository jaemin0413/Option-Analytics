package com.option.global.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration
import java.time.LocalDate

@ConfigurationProperties("job")
data class JobProperties(
    val lease: Duration,
    val tradeTimeBudget: Duration,
    val backfillStart: LocalDate,
    val backfillExpirationStart: LocalDate,
)
