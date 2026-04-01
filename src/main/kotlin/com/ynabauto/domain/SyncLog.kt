package com.ynabauto.domain

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
    @Column(name = "id")
    val id: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    val source: SyncSource,

    @Column(name = "last_run", nullable = false)
    val lastRun: Instant,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    val status: SyncStatus,

    @Column(name = "message")
    val message: String? = null
)
