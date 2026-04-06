package com.budgetsortbot.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "sync_logs")
data class SyncLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val source: SyncSource,
    @Column(nullable = false)
    val lastRun: Instant,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: SyncStatus,
    val message: String? = null,
)
