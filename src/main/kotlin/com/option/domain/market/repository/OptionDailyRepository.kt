package com.option.domain.market.repository

import com.option.domain.market.entity.OptionDaily
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate

interface OptionDailyRepository : JpaRepository<OptionDaily, Long> {
    fun findByUnderlyingAndSessionDateAndFeed(underlying: String, sessionDate: LocalDate, feed: String): List<OptionDaily>

    @Query(
        """
        select max(d.sessionDate) from OptionDaily d
        where d.underlying = :underlying and d.feed = :feed and d.sessionDate < :before and d.openInterest is not null
        """,
    )
    fun findLatestOiSessionBefore(underlying: String, feed: String, before: LocalDate): LocalDate?

    @Query("select distinct d.sessionDate from OptionDaily d where d.feed = :feed order by d.sessionDate")
    fun findSessions(feed: String): List<LocalDate>
}
