package com.option.global.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("jwt")
class JwtProperties(
    val secret: String,
    val accessTokenValidity: Duration,
    val refreshTokenValidity: Duration,
)
