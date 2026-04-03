package com.budgetsortbot.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ScheduleConfigTest {

    @Test
    fun `EVERY_N_SECONDS with interval 3 produces correct cron`() {
        val config = ScheduleConfig(type = ScheduleType.EVERY_N_SECONDS, secondInterval = 3)
        assertEquals("*/3 * * * * *", config.toCron())
    }

    @Test
    fun `EVERY_N_SECONDS with null interval returns null`() {
        val config = ScheduleConfig(type = ScheduleType.EVERY_N_SECONDS, secondInterval = null)
        assertNull(config.toCron())
    }

    @Test
    fun `EVERY_N_MINUTES with interval 15 produces correct cron`() {
        val config = ScheduleConfig(type = ScheduleType.EVERY_N_MINUTES, minuteInterval = 15)
        assertEquals("0 */15 * * * *", config.toCron())
    }

    @Test
    fun `EVERY_N_MINUTES with interval 1 produces correct cron`() {
        val config = ScheduleConfig(type = ScheduleType.EVERY_N_MINUTES, minuteInterval = 1)
        assertEquals("0 */1 * * * *", config.toCron())
    }

    @Test
    fun `EVERY_N_MINUTES with null interval returns null`() {
        val config = ScheduleConfig(type = ScheduleType.EVERY_N_MINUTES, minuteInterval = null)
        assertNull(config.toCron())
    }

    @Test
    fun `HOURLY produces correct cron expression`() {
        val config = ScheduleConfig(type = ScheduleType.HOURLY)
        assertEquals("0 0 * * * *", config.toCron())
    }

    @Test
    fun `EVERY_N_HOURS with interval 5 produces correct cron`() {
        val config = ScheduleConfig(type = ScheduleType.EVERY_N_HOURS, hourInterval = 5)
        assertEquals("0 0 */5 * * *", config.toCron())
    }

    @Test
    fun `EVERY_N_HOURS with interval 1 produces correct cron`() {
        val config = ScheduleConfig(type = ScheduleType.EVERY_N_HOURS, hourInterval = 1)
        assertEquals("0 0 */1 * * *", config.toCron())
    }

    @Test
    fun `EVERY_N_HOURS with null interval returns null`() {
        val config = ScheduleConfig(type = ScheduleType.EVERY_N_HOURS, hourInterval = null)
        assertNull(config.toCron())
    }

    @Test
    fun `EVERY_N_HOURS with zero interval returns null`() {
        val config = ScheduleConfig(type = ScheduleType.EVERY_N_HOURS, hourInterval = 0)
        assertNull(config.toCron())
    }

    @Test
    fun `DAILY at 14 00 produces correct cron`() {
        val config = ScheduleConfig(type = ScheduleType.DAILY, hour = 14, minute = 0)
        assertEquals("0 0 14 * * *", config.toCron())
    }

    @Test
    fun `DAILY at 09 30 produces correct cron`() {
        val config = ScheduleConfig(type = ScheduleType.DAILY, hour = 9, minute = 30)
        assertEquals("0 30 9 * * *", config.toCron())
    }

    @Test
    fun `DAILY with null hour returns null`() {
        val config = ScheduleConfig(type = ScheduleType.DAILY, hour = null, minute = 0)
        assertNull(config.toCron())
    }

    @Test
    fun `WEEKLY on MON at 08 00 produces correct cron`() {
        val config = ScheduleConfig(type = ScheduleType.WEEKLY, hour = 8, minute = 0, dayOfWeek = "MON")
        assertEquals("0 0 8 * * MON", config.toCron())
    }

    @Test
    fun `WEEKLY with null dayOfWeek returns null`() {
        val config = ScheduleConfig(type = ScheduleType.WEEKLY, hour = 8, minute = 0, dayOfWeek = null)
        assertNull(config.toCron())
    }

    @Test
    fun `WEEKLY with null hour returns null`() {
        val config = ScheduleConfig(type = ScheduleType.WEEKLY, hour = null, minute = 0, dayOfWeek = "FRI")
        assertNull(config.toCron())
    }
}
