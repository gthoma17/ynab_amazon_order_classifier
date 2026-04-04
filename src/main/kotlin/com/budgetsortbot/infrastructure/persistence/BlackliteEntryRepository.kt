package com.budgetsortbot.infrastructure.persistence

import com.budgetsortbot.domain.BlackliteEntry
import com.budgetsortbot.domain.BlackliteEntryId
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.Repository

/**
 * Read-only Spring Data repository for [BlackliteEntry].
 *
 * Extends the base [Repository] marker interface (not [JpaRepository]) so
 * that no mutation methods (save, delete, etc.) are exposed.  Only the
 * single [findAll] query method is available to callers.
 *
 * Blacklite auto-creates the [entries] table on first write; this
 * repository gracefully returns an empty list until that happens (the
 * [ApplicationLogService] layer catches any exception for further safety).
 */
interface BlackliteEntryRepository : Repository<BlackliteEntry, BlackliteEntryId> {

    fun findAll(pageable: Pageable): List<BlackliteEntry>
}
