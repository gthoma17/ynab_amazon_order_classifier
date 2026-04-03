package com.ynabauto.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.DisposableBean
import org.springframework.scheduling.support.CronTrigger
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.stereotype.Component
import java.util.concurrent.ScheduledFuture

/**
 * Manages the dynamic sync schedule. On startup and whenever [reschedule] is called,
 * reads [ConfigService.SCHEDULE_CONFIG] from the DB and schedules both
 * [EmailIngestionService.ingest] and [YnabSyncService.sync] accordingly.
 *
 * Uses a single-thread [ThreadPoolTaskScheduler] to keep memory overhead minimal
 * on Pi 3 hardware (512 MB JVM heap constraint).
 */
@Component
class SyncScheduler(
    private val emailIngestionService: EmailIngestionService,
    private val ynabSyncService: YnabSyncService,
    private val configService: ConfigService,
    private val objectMapper: ObjectMapper
) : DisposableBean {

    companion object {
        private val log = KotlinLogging.logger {}
        /** Fallback cron when no schedule is configured: every 5 hours. */
        internal const val DEFAULT_CRON = "0 0 */5 * * *"
    }

    private val taskScheduler: ThreadPoolTaskScheduler

    init {
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.poolSize = 1
        scheduler.setThreadNamePrefix("sync-scheduler-")
        scheduler.initialize()
        taskScheduler = scheduler
    }

    private var scheduledFuture: ScheduledFuture<*>? = null

    @PostConstruct
    fun init() {
        reschedule()
    }

    /**
     * Cancels any active schedule and re-reads the configuration from the DB to
     * start a new schedule. Safe to call at runtime when the user saves new
     * schedule settings.
     */
    fun reschedule() {
        scheduledFuture?.cancel(false)
        scheduledFuture = null

        val cronExpression = buildCronFromConfig()
        if (cronExpression != null) {
            log.info { "Scheduling sync with cron: $cronExpression" }
            scheduledFuture = taskScheduler.schedule({ runSync() }, CronTrigger(cronExpression))
        } else {
            log.warn { "No valid schedule configuration found; sync will not run automatically" }
        }
    }

    override fun destroy() {
        scheduledFuture?.cancel(true)
        taskScheduler.destroy()
    }

    private fun runSync() {
        emailIngestionService.ingest()
        ynabSyncService.sync()
    }

    /**
     * Reads [ConfigService.SCHEDULE_CONFIG] from the DB and converts it to a cron
     * string. Returns the default cron when the key is absent and null when the
     * stored value cannot be parsed (so the caller can fall back to no schedule
     * rather than crashing the app).
     */
    internal fun buildCronFromConfig(): String? {
        return try {
            val json = configService.getValue(ConfigService.SCHEDULE_CONFIG)
                ?: return DEFAULT_CRON
            val config = objectMapper.readValue(json, ScheduleConfig::class.java)
            config.toCron()
        } catch (e: Exception) {
            log.error(e) { "Failed to parse schedule config; sync will not run automatically" }
            null
        }
    }
}
