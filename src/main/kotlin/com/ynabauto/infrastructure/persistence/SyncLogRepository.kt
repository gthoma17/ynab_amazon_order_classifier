package com.ynabauto.infrastructure.persistence

import com.ynabauto.domain.SyncLog
import com.ynabauto.domain.SyncSource
import org.springframework.data.jpa.repository.JpaRepository

interface SyncLogRepository : JpaRepository<SyncLog, Long> {
    fun findBySource(source: SyncSource): List<SyncLog>
    fun findTopBySourceOrderByLastRunDesc(source: SyncSource): SyncLog?
}
