package com.budgetsortbot.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

/**
 * Creates a dedicated read-only [DataSource] for the Blacklite log database.
 *
 * Blacklite writes application logs to a separate SQLite file
 * (app.blacklite.path) so that it never contends with Flyway migrations or
 * JPA writes on the primary datasource.  [ApplicationLogService] uses a
 * [JdbcTemplate][org.springframework.jdbc.core.JdbcTemplate] backed by this
 * datasource to query recent log entries.
 */
@Configuration
class LogDataSourceConfig {

    @Value("\${app.blacklite.path:./data/logs.sqlite}")
    private lateinit var blacklitePath: String

    @Bean(name = ["logsDataSource"])
    fun logsDataSource(): DataSource {
        val config = HikariConfig()
        config.jdbcUrl = "jdbc:sqlite:$blacklitePath?busy_timeout=5000"
        config.driverClassName = "org.sqlite.JDBC"
        // SQLite allows only one writer at a time; a single connection is sufficient
        // for the read-only log queries performed by ApplicationLogService.
        config.maximumPoolSize = 1
        return HikariDataSource(config)
    }
}
