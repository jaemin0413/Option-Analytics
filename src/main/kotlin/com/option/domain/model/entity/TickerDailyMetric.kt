package com.option.domain.model.entity

import com.option.domain.model.vo.GexDetail
import com.option.global.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDate

// 종목별 계산 결과
@Entity
@Table(
    name = "ticker_daily_metric",
    uniqueConstraints = [UniqueConstraint(name = "uk_metric", columnNames = ["publication_id", "symbol"])],
)
class TickerDailyMetric(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "publication_id", nullable = false)
    val publicationId: Long,

    @Column(nullable = false)
    val symbol: String,

    @Column(name = "base_session", nullable = false)
    val baseSession: LocalDate,

    @Column(name = "target_session", nullable = false)
    val targetSession: LocalDate,

    @Column(name = "week_target_session", nullable = false)
    val weekTargetSession: LocalDate,

    @Column(name = "base_close")
    val baseClose: Double? = null,

    @Column(name = "oi_as_of_date")
    val oiAsOfDate: LocalDate? = null,

    @Column(name = "oi_delay_days")
    val oiDelayDays: Int? = null,

    @Column(name = "day_iv")
    val dayIv: Double? = null,

    @Column(name = "day_iv_expiry")
    val dayIvExpiry: LocalDate? = null,

    @Column(name = "day_iv_fallback")
    val dayIvFallback: Boolean? = null,

    @Column(name = "day_width")
    val dayWidth: Double? = null,

    @Column(name = "week_iv")
    val weekIv: Double? = null,

    @Column(name = "week_iv_expiry")
    val weekIvExpiry: LocalDate? = null,

    @Column(name = "week_iv_fallback")
    val weekIvFallback: Boolean? = null,

    @Column(name = "week_width")
    val weekWidth: Double? = null,

    @Column(name = "day_rv_width")
    val dayRvWidth: Double? = null,

    @Column(name = "week_rv_width")
    val weekRvWidth: Double? = null,

    @Column(name = "maxpain_expiry")
    val maxpainExpiry: LocalDate? = null,

    @Column(name = "maxpain_strike")
    val maxpainStrike: Double? = null,

    @Column(name = "maxpain_nearest_strike")
    val maxpainNearestStrike: Double? = null,

    @Column(name = "call_wall_strike")
    val callWallStrike: Double? = null,

    @Column(name = "put_wall_strike")
    val putWallStrike: Double? = null,

    @Column(name = "monthly_expiry")
    val monthlyExpiry: LocalDate? = null,

    @Column(name = "monthly_maxpain_strike")
    val monthlyMaxpainStrike: Double? = null,

    @Column(name = "monthly_call_wall_strike")
    val monthlyCallWallStrike: Double? = null,

    @Column(name = "monthly_put_wall_strike")
    val monthlyPutWallStrike: Double? = null,

    @Column(name = "gex_day")
    val gexDay: Double? = null,

    @Column(name = "gex_week")
    val gexWeek: Double? = null,

    @Column(name = "gex_all")
    val gexAll: Double? = null,

    @Column(name = "gex_contracts")
    val gexContracts: Int? = null,

    @Column(name = "gex_contract_missing_rate")
    val gexContractMissingRate: Double? = null,

    @Column(name = "gex_oi_missing_rate")
    val gexOiMissingRate: Double? = null,

    @Column(name = "gex_atm_missing")
    val gexAtmMissing: Boolean? = null,

    @Column(name = "gex_official", nullable = false)
    val gexOfficial: Boolean = false,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gex_detail", columnDefinition = "jsonb")
    val gexDetail: GexDetail? = null,

    @Column(name = "pcr_volume")
    val pcrVolume: Double? = null,

    @Column(name = "pcr_oi")
    val pcrOi: Double? = null,

    @Column(name = "pcr_volume_week")
    val pcrVolumeWeek: Double? = null,

    @Column(name = "pcr_oi_week")
    val pcrOiWeek: Double? = null,

    @Column(name = "pcr_percentile")
    val pcrPercentile: Double? = null,

    @Column(name = "day_event", nullable = false)
    val dayEvent: Boolean = false,

    @Column(name = "week_event", nullable = false)
    val weekEvent: Boolean = false,
) : BaseEntity()
