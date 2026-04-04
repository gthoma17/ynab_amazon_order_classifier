package com.budgetsortbot.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import javax.sql.DataSource

private val logger = KotlinLogging.logger {}

/**
 * Retrieves recent application log entries from the Blacklite [entries] table
 * using a dedicated JDBC datasource that reads from the Blacklite log file.
 *
 * The log database is intentionally separate from the primary application
 * datasource (see [LogDataSourceConfig]) to prevent write-lock conflicts
 * between Blacklite and Flyway/JPA on startup.
 *
 * Graceful degradation: any exception (e.g. table absent on first run before
 * any log has been written) is caught; the caller receives `null` to signal
 * that logs are unavailable.
 */
@Service
class ApplicationLogService(@Qualifier("logsDataSource") logsDataSource: DataSource) {

    private val jdbc = JdbcTemplate(logsDataSource)

    /**
     * Returns up to [limit] recent log lines decoded from the `content` BLOB,
     * ordered most-recent-first.
     * Returns `null` if the query fails (e.g. table absent on first run).
     * Returns an empty list if the table exists but contains no entries.
     */
    fun getRecentLogs(limit: Int): List<String>? {
        return try {
            jdbc.query(
                "SELECT content FROM entries ORDER BY epoch_secs DESC, nanos DESC LIMIT ?",
                { rs, _ -> rs.getBytes("content")?.let { String(it, Charsets.UTF_8).trim() } ?: "" },
                limit
            ).filter { it.isNotEmpty() }
        } catch (e: Exception) {
            logger.debug(e) { "Could not retrieve application logs from Blacklite table" }
            null
        }
    }
}


