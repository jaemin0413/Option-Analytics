package com.option.global.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class ClockConfig {

    // 테스트에서 시간을 바꿀 수 있게 시간을 빈으로
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
