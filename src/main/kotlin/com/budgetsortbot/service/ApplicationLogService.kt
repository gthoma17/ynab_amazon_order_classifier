package com.budgetsortbot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

/**
 * Retrieves recent application log entries from the Blacklite SQLite table.
 *
 * Blacklite writes log entries into an `entries` table in the same SQLite
 * database as the application.  This service queries that table directly via
 * [JdbcTemplate] so that the report assembly logic can include recent log
 * lines without any file-I/O or additional datasource configuration.
 *
 * Graceful degradation: any exception (e.g. table absent on first run, locked
 * file) is caught and logged; the caller receives an empty list instead of a
 * thrown exception.
 */
@Service
class ApplicationLogService(private val jdbcTemplate: JdbcTemplate) {

    /**
     * Returns up to [limit] recent log lines, most-recent-first.
     * Returns `null` if the query fails (e.g. table absent on first run).
     * Returns an empty list if the table exists but contains no entries.
     */
    fun getRecentLogs(limit: Int): List<String>? {
        return try {
            val rows = jdbcTemplate.query(
                "SELECT content FROM entries ORDER BY epoch_secs DESC, nanos DESC LIMIT ?",
                { rs, _ -> rs.getBytes("content") },
                limit
            )
            rows.mapNotNull { bytes -> bytes?.let { String(it, Charsets.UTF_8).trim() } }
                .filter { it.isNotEmpty() }
        } catch (e: Exception) {
            logger.debug(e) { "Could not retrieve application logs from Blacklite table" }
            null
        }
    }
}
