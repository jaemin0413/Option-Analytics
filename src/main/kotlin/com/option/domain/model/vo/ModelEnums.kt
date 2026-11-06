package com.option.domain.model.vo

enum class PublicationStatus { COMPUTING, COMPUTED, PUBLISHED, BACKFILL }

enum class Horizon { NEXT_DAY, WEEK }

enum class Signal { UP, DOWN, FLAT, ABSTAIN, NO_DATA, POSITIVE, NEGATIVE, IN_RANGE }

enum class IvSource { PROVIDER, BACKFILL }

object ModelVersions {
    const val EXPECTED_MOVE = "em-v1"
    const val GEX = "gex-v1"
    const val PCR = "pcr-v1"
    const val SENTIMENT = "sent-v1"
    const val MAXPAIN_PULL = "maxpain-pull-v1"
    const val PCR_CONTRARIAN = "pcr-contrarian-v1"
    const val IV_BACKFILL = "iv-backfill-v1"

    val LIVE = listOf(EXPECTED_MOVE, GEX, PCR, SENTIMENT, MAXPAIN_PULL, PCR_CONTRARIAN).joinToString(",")
    val BACKFILL = listOf(IV_BACKFILL, EXPECTED_MOVE, PCR, PCR_CONTRARIAN).joinToString(",")
}

object ScoreSubjects {
    const val ALWAYS_UP = "baseline-always-up"
    const val PREV_DIRECTION = "baseline-prev-direction"
    const val EXPECTED_MOVE = "em-range"
    const val RV_RANGE = "rv-range"
    const val MAXPAIN = "maxpain"
    const val MAXPAIN_BASE_SPOT = "maxpain-base-spot"
    const val MAXPAIN_BASE_STRIKE = "maxpain-base-strike"
    const val GEX_REGIME = "gex-regime"
    const val JOURNAL = "journal"
}
