package com.option.global.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.LocalDate

@ConfigurationProperties("job")
data class JobProperties(
    val backfillStart: LocalDate,
    val backfillExpirationStart: LocalDate,
)
