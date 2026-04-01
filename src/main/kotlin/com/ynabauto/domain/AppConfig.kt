package com.ynabauto.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "app_config")
data class AppConfig(
    @Id
    val key: String,

    @Column(nullable = false)
    val value: String,

    @Column(nullable = false)
    val updatedAt: Instant
)
