package com.option.domain.model.repository

import com.option.domain.model.entity.HypothesisSignal
import com.option.domain.model.entity.MarketSentiment
import com.option.domain.model.entity.Publication
import com.option.domain.model.entity.Score
import com.option.domain.model.entity.TickerDailyMetric
import com.option.domain.model.vo.PublicationStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.time.LocalDate

interface PublicationRepository : JpaRepository<Publication, Long> {
    fun findFirstBySessionDateAndStatusOrderByPublicationIdDesc(session: LocalDate, status: PublicationStatus): Publication?

    fun existsBySessionDateAndStatus(session: LocalDate, status: PublicationStatus): Boolean

    @Query(
        """
        select p from Publication p
        where p.status = com.option.domain.model.vo.PublicationStatus.PUBLISHED and p.publishedAt <= :now
        order by p.sessionDate desc limit 1
        """,
    )
    fun findLatestVisible(now: Instant): Publication?
}

interface TickerDailyMetricRepository : JpaRepository<TickerDailyMetric, Long> {
    fun findByPublicationIdAndSymbol(publicationId: Long, symbol: String): TickerDailyMetric?

    fun findByPublicationIdAndSymbolIn(publicationId: Long, symbols: Collection<String>): List<TickerDailyMetric>

    @Query(
        """
        select m from TickerDailyMetric m
        where (m.targetSession = :session or m.weekTargetSession = :session)
          and m.publicationId in (
              select p.publicationId from Publication p
              where p.status in (com.option.domain.model.vo.PublicationStatus.PUBLISHED,
                                 com.option.domain.model.vo.PublicationStatus.BACKFILL))
        """,
    )
    fun findPublishedByTarget(session: LocalDate): List<TickerDailyMetric>

    @Query(
        nativeQuery = true,
        value = """
        SELECT DISTINCT ON (m.base_session) m.pcr_volume
        FROM ticker_daily_metric m
        JOIN publication p ON p.publication_id = m.publication_id
        WHERE m.symbol = :symbol AND m.base_session < :before AND m.pcr_volume IS NOT NULL
          AND p.status IN ('PUBLISHED', 'BACKFILL')
        ORDER BY m.base_session DESC
        LIMIT :limit
        """,
    )
    fun findPcrHistory(
        @Param("symbol") symbol: String,
        @Param("before") before: LocalDate,
        @Param("limit") limit: Int,
    ): List<Double>
}

interface HypothesisSignalRepository : JpaRepository<HypothesisSignal, Long> {
    fun findByPublicationIdAndSymbol(publicationId: Long, symbol: String): List<HypothesisSignal>

    @Query(
        """
        select s from HypothesisSignal s
        where s.targetSession = :session and s.publicationId in (
            select p.publicationId from Publication p
            where p.status in (com.option.domain.model.vo.PublicationStatus.PUBLISHED,
                               com.option.domain.model.vo.PublicationStatus.BACKFILL))
        """,
    )
    fun findPublishedByTarget(session: LocalDate): List<HypothesisSignal>
}

interface MarketSentimentRepository : JpaRepository<MarketSentiment, Long> {
    fun findByPublicationId(publicationId: Long): MarketSentiment?
}

interface ScoreRepository : JpaRepository<Score, Long> {
    fun findByTargetSession(session: LocalDate): List<Score>
}
