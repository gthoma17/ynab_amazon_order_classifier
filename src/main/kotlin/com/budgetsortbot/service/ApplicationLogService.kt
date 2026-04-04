package com.budgetsortbot.service

import com.budgetsortbot.infrastructure.persistence.BlackliteEntryRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

/**
 * Retrieves recent application log entries from the Blacklite [entries] table
 * using a read-only JPA repository.
 *
 * Blacklite writes log entries into an `entries` table in the same SQLite
 * database as the application datasource.  Querying via the JPA layer keeps
 * access consistent with the rest of the persistence tier and avoids raw
 * JDBC usage.
 *
 * Graceful degradation: any exception (e.g. table absent on first run before
 * any log has been written) is caught; the caller receives `null` to signal
 * that logs are unavailable.
 */
@Service
class ApplicationLogService(private val blackliteEntryRepository: BlackliteEntryRepository) {

    /**
     * Returns up to [limit] recent log lines decoded from the `content` BLOB,
     * ordered most-recent-first.
     * Returns `null` if the query fails (e.g. table absent on first run).
     * Returns an empty list if the table exists but contains no entries.
     */
    fun getRecentLogs(limit: Int): List<String>? {
        return try {
            blackliteEntryRepository.findAll(
                PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "id.epochSecs", "id.nanos"))
            )
                .mapNotNull { entry -> entry.content?.let { String(it, Charsets.UTF_8).trim() } }
                .filter { it.isNotEmpty() }
        } catch (e: Exception) {
            logger.debug(e) { "Could not retrieve application logs from Blacklite table" }
            null
        }
    }
}

