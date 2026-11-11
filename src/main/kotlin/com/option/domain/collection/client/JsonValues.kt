package com.option.domain.collection.client

import com.fasterxml.jackson.databind.JsonNode
import com.option.domain.market.util.MarketTime
import java.time.Instant
import java.time.LocalDate

fun JsonNode.doubleOrNull(field: String): Double? {
    val node = get(field)
    if (node == null || node.isNull) return null
    return node.asText().toDoubleOrNull()
}

fun JsonNode.longOrNull(field: String): Long? {
    val node = get(field)
    if (node == null || node.isNull) return null
    return node.asText().toLongOrNull()
}

fun JsonNode.textOrNull(field: String): String? {
    val node = get(field)
    if (node == null || node.isNull) return null
    return node.asText()
}

fun JsonNode.instantOrNull(field: String): Instant? {
    val text = textOrNull(field) ?: return null
    return Instant.parse(text)
}

fun JsonNode.etDateOrNull(field: String): LocalDate? = instantOrNull(field)?.atZone(MarketTime.ET)?.toLocalDate()
