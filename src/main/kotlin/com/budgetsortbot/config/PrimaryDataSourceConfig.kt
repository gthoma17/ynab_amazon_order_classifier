package com.budgetsortbot.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import javax.sql.DataSource

/**
 * Explicitly declares the primary application [DataSource].
 *
 * Spring Boot's [DataSourceAutoConfiguration] backs off when *any*
 * [DataSource] bean is present in the context — including the
 * [LogDataSourceConfig.logsDataSource] bean.  Without this class, only
 * the logs datasource would be registered, causing Flyway and JPA to
 * migrate and operate against the wrong database.
 *
 * Declaring a `@Primary DataSource` bean here restores the expected
 * behaviour: Flyway, JPA, and the transaction manager all target the
 * primary application database, while [ApplicationLogService] separately
 * queries the Blacklite log file via the `logsDataSource` qualifier.
 */
@Configuration
class PrimaryDataSourceConfig {
    @Value("\${spring.datasource.url}")
    private lateinit var url: String

    @Bean
    @Primary
    fun dataSource(): DataSource {
        val config = HikariConfig()
        config.jdbcUrl = url
        config.driverClassName = "org.sqlite.JDBC"
        // SQLite serialises writes; a pool of 1 avoids contention and
        // mirrors what Spring Boot's Hikari auto-configuration would use
        // for a single-writer SQLite file.
        config.maximumPoolSize = 1
        return HikariDataSource(config)
    }
}
