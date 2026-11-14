package com.option.domain.user.entity

import com.option.global.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, unique = true)
    val email: String,

    // 해시한 값만 저장한다.
    @Column(nullable = false)
    var password: String,

    // 마지막으로 발급한 리프레시 토큰. 재발급할 때 이것과 같아야 한다.
    @Column(name = "refresh_token")
    var refreshToken: String? = null,
) : BaseEntity()
