package com.budgetsortbot.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SyncSchedulerTest {
    private val emailIngestionService = mockk<EmailIngestionService>(relaxed = true)
    private val ynabSyncService = mockk<YnabSyncService>(relaxed = true)
    private val configService = mockk<ConfigService>()
    private val objectMapper = jacksonObjectMapper()
    private lateinit var syncScheduler: SyncScheduler

    @BeforeEach
    fun setup() {
        syncScheduler = SyncScheduler(emailIngestionService, ynabSyncService, configService, objectMapper)
    }

    @AfterEach
    fun tearDown() {
        syncScheduler.destroy()
    }

    @Test
    fun `buildCronFromConfig returns default cron when no schedule configured`() {
        every { configService.getValue(ConfigService.SCHEDULE_CONFIG) } returns null

        val cron = syncScheduler.buildCronFromConfig()

        assertEquals(SyncScheduler.DEFAULT_CRON, cron)
    }

    @Test
    fun `buildCronFromConfig parses EVERY_N_SECONDS schedule correctly`() {
        every { configService.getValue(ConfigService.SCHEDULE_CONFIG) } returns """{"type":"EVERY_N_SECONDS","secondInterval":3}"""

        val cron = syncScheduler.buildCronFromConfig()

        assertEquals("*/3 * * * * *", cron)
    }

    @Test
    fun `buildCronFromConfig parses EVERY_N_MINUTES schedule correctly`() {
        every { configService.getValue(ConfigService.SCHEDULE_CONFIG) } returns """{"type":"EVERY_N_MINUTES","minuteInterval":15}"""

        val cron = syncScheduler.buildCronFromConfig()

        assertEquals("0 */15 * * * *", cron)
    }

    @Test
    fun `buildCronFromConfig parses HOURLY schedule correctly`() {
        every { configService.getValue(ConfigService.SCHEDULE_CONFIG) } returns """{"type":"HOURLY"}"""

        val cron = syncScheduler.buildCronFromConfig()

        assertEquals("0 0 * * * *", cron)
    }

    @Test
    fun `buildCronFromConfig parses EVERY_N_HOURS schedule correctly`() {
        every { configService.getValue(ConfigService.SCHEDULE_CONFIG) } returns """{"type":"EVERY_N_HOURS","hourInterval":3}"""

        val cron = syncScheduler.buildCronFromConfig()

        assertEquals("0 0 */3 * * *", cron)
    }

    @Test
    fun `buildCronFromConfig parses DAILY schedule correctly`() {
        every { configService.getValue(ConfigService.SCHEDULE_CONFIG) } returns """{"type":"DAILY","hour":14,"minute":30}"""

        val cron = syncScheduler.buildCronFromConfig()

        assertEquals("0 30 14 * * *", cron)
    }

    @Test
    fun `buildCronFromConfig parses WEEKLY schedule correctly`() {
        every { configService.getValue(ConfigService.SCHEDULE_CONFIG) } returns
            """{"type":"WEEKLY","hour":9,"minute":0,"dayOfWeek":"MON"}"""

        val cron = syncScheduler.buildCronFromConfig()

        assertEquals("0 0 9 * * MON", cron)
    }

    @Test
    fun `buildCronFromConfig returns null for malformed JSON`() {
        every { configService.getValue(ConfigService.SCHEDULE_CONFIG) } returns "not-valid-json"

        val cron = syncScheduler.buildCronFromConfig()

        assertNull(cron)
    }
}
