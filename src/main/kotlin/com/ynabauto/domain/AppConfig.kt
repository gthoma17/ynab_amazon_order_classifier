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
    @Column(name = "key")
    val key: String,

    @Column(name = "value", nullable = false)
    val value: String,

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant
)
