package com.option.global.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration
class AsyncConfig {

    // 백필은 하나의 스레드에서 작업
    @Bean
    fun backgroundExecutor(): ThreadPoolTaskExecutor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 1
        executor.maxPoolSize = 1
        executor.setThreadNamePrefix("background-")
        return executor
    }

    // 수동 실행용
    @Bean
    fun manualExecutor(): ThreadPoolTaskExecutor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 2
        executor.maxPoolSize = 2
        executor.setThreadNamePrefix("manual-")
        return executor
    }
}
