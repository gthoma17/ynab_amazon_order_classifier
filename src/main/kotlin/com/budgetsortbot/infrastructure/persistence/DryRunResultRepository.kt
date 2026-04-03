package com.budgetsortbot.infrastructure.persistence

import com.budgetsortbot.domain.DryRunResult
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.transaction.annotation.Transactional

interface DryRunResultRepository : JpaRepository<DryRunResult, Long> {
    fun findAllByOrderByRunAtDesc(): List<DryRunResult>

    @Modifying
    @Transactional
    override fun deleteAll()
}
