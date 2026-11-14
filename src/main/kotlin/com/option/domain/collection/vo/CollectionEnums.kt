package com.option.domain.collection.vo

// 디스크가 모자랄 때 멈추는 기준이다. 백필이 먼저 멈추고, 그다음 본 수집이 멈춘다. 계산과 채점은 안 멈춘다.
enum class DiskRule { BACKGROUND, COLLECTION, NONE }

enum class FailureCategory { TIMEOUT, NETWORK, RATE_LIMIT, SERVER_ERROR, CLIENT_ERROR, PARSE, DB_CONSTRAINT, DISK, OTHER }
