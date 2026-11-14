package com.option.domain.collection.job

import com.option.domain.collection.service.RunContext
import com.option.domain.collection.vo.DiskRule
import org.springframework.util.ClassUtils

// 작업 규칙
interface Job {
    // 정시 작업이 밀리지 않게 스레드 따로 뺄 건지
    val background: Boolean

    // 디스크 부족 여부
    val diskRule: DiskRule

    fun run(context: RunContext)

    val name: String
        get() = ClassUtils.getUserClass(javaClass).simpleName
            .removeSuffix("Job")
            .replace(Regex("([a-z])([A-Z])"), "$1-$2")
            .lowercase()
}
