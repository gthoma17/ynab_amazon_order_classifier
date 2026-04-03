package com.budgetsortbot.infrastructure.persistence

import com.budgetsortbot.domain.SyncLog
import com.budgetsortbot.domain.SyncSource
import org.springframework.data.jpa.repository.JpaRepository

interface SyncLogRepository : JpaRepository<SyncLog, Long> {
    fun findBySource(source: SyncSource): List<SyncLog>
    fun findTopBySourceOrderByLastRunDesc(source: SyncSource): SyncLog?
}
