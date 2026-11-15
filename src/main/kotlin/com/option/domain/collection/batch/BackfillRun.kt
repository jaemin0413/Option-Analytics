package com.option.domain.collection.batch

import com.option.domain.collection.service.RunContext
import org.springframework.batch.core.JobParameters
import org.springframework.stereotype.Component
import java.time.LocalDate

// 런컨텍스트와 잡파라미터
@Component
class BackfillRun {
    @Volatile private var context: RunContext? = null
    @Volatile private var parameters: JobParameters? = null

    fun begin(context: RunContext, parameters: JobParameters) {
        this.context = context
        this.parameters = parameters
    }

    fun context(): RunContext = checkNotNull(context) { "백필이 시작되지 않았습니다" }

    fun asOf(): LocalDate = checkNotNull(parameters?.getLocalDate(AS_OF))

    fun end(): LocalDate = checkNotNull(parameters?.getLocalDate(END))

    companion object {
        const val AS_OF = "asOf"
        const val END = "end"
    }
}
