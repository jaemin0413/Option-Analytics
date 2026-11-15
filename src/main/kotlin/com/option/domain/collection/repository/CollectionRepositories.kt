package com.option.domain.collection.repository

import com.option.domain.collection.entity.BackfillTarget
import com.option.domain.collection.entity.RawFile
import org.springframework.data.jpa.repository.JpaRepository

interface RawFileRepository : JpaRepository<RawFile, Long>

interface BackfillTargetRepository : JpaRepository<BackfillTarget, Long> {
    fun countByIdLessThanEqual(id: Long): Long
}
