package com.option.global.common

import java.sql.PreparedStatement
import java.sql.Types
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// JDBC 배치 저장소용. null 타입을 직접 줘야 드라이버가 타입을 물으러 DB에 다시 가지 않는다.
fun PreparedStatement.setDoubleOrNull(i: Int, v: Double?) = if (v == null) setNull(i, Types.DOUBLE) else setDouble(i, v)

fun PreparedStatement.setLongOrNull(i: Int, v: Long?) = if (v == null) setNull(i, Types.BIGINT) else setLong(i, v)

fun PreparedStatement.setIntOrNull(i: Int, v: Int?) = if (v == null) setNull(i, Types.INTEGER) else setInt(i, v)

fun PreparedStatement.setBooleanOrNull(i: Int, v: Boolean?) = if (v == null) setNull(i, Types.BOOLEAN) else setBoolean(i, v)

fun PreparedStatement.setDateOrNull(i: Int, v: LocalDate?) = if (v == null) setNull(i, Types.DATE) else setObject(i, v)

fun PreparedStatement.setStringOrNull(i: Int, v: String?) = if (v == null) setNull(i, Types.VARCHAR) else setString(i, v)

fun PreparedStatement.setInstant(i: Int, v: Instant?) =
    if (v == null) setNull(i, Types.TIMESTAMP_WITH_TIMEZONE) else setObject(i, v.atOffset(ZoneOffset.UTC))
