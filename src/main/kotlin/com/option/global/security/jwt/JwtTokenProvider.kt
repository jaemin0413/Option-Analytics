package com.option.global.security.jwt

import com.option.global.apiPayLoad.error.AuthErrorCode
import com.option.global.apiPayLoad.exception.BusinessException
import com.option.global.properties.JwtProperties
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.JwtParser
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(
    private val jwtProperties: JwtProperties,
    private val clock: Clock,
) {
    private val secretKey: SecretKey
    private val parser: JwtParser

    init {
        if (jwtProperties.secret.isBlank()) throw BusinessException(AuthErrorCode.JWT_SECRET_MISSING)
        secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.secret))
        parser = Jwts.parser().verifyWith(secretKey).clock { Date.from(clock.instant()) }.build()
    }

    fun createAccessToken(userId: Long): String = createToken(userId, ACCESS, jwtProperties.accessTokenValidity)

    fun createRefreshToken(userId: Long): String = createToken(userId, REFRESH, jwtProperties.refreshTokenValidity)

    fun getUserIdFromAccessToken(token: String): Long = getUserId(token, ACCESS)

    fun getUserIdFromRefreshToken(token: String): Long = getUserId(token, REFRESH)

    // 토큰 생성
    private fun createToken(userId: Long, category: String, validity: Duration): String {
        val now = clock.instant()
        return Jwts.builder()
            .subject(userId.toString())
            .id(UUID.randomUUID().toString()) // 토큰 고유값
            .claim(CATEGORY, category)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(validity)))
            .signWith(secretKey)
            .compact()
    }

    // 사용자 id 불러오기
    private fun getUserId(token: String, category: String): Long {
        val claims = parseClaims(token)
        if (claims.get(CATEGORY, String::class.java) != category) {
            throw BusinessException(AuthErrorCode.INVALID_TOKEN)
        }
        return claims.subject.toLong()
    }

    // 서명 확인
    private fun parseClaims(token: String): Claims {
        try {
            return parser.parseSignedClaims(token).payload
        } catch (e: ExpiredJwtException) {
            throw BusinessException(AuthErrorCode.EXPIRED_TOKEN)
        } catch (e: JwtException) {
            throw BusinessException(AuthErrorCode.INVALID_TOKEN)
        } catch (e: IllegalArgumentException) {
            throw BusinessException(AuthErrorCode.INVALID_TOKEN)
        }
    }

    companion object {
        private const val CATEGORY = "category"
        private const val ACCESS = "access"
        private const val REFRESH = "refresh"
    }
}
