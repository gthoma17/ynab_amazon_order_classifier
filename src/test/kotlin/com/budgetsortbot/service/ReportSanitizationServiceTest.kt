package com.budgetsortbot.service

import com.budgetsortbot.domain.AppConfig
import com.budgetsortbot.infrastructure.persistence.AppConfigRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class ReportSanitizationServiceTest {

    private val appConfigRepository = mockk<AppConfigRepository>()
    private lateinit var service: ReportSanitizationService

    @BeforeEach
    fun setup() {
        service = ReportSanitizationService(appConfigRepository)
    }

    @Test
    fun `sanitize returns original text when app_config is empty`() {
        every { appConfigRepository.findAll() } returns emptyList()

        val (result, wasSanitized) = service.sanitize("Some log text with no secrets")

        assertEquals("Some log text with no secrets", result)
        assertFalse(wasSanitized)
    }

    @Test
    fun `sanitize redacts a single matching config value`() {
        every { appConfigRepository.findAll() } returns listOf(
            AppConfig(key = "YNAB_TOKEN", value = "secret-token-123", updatedAt = Instant.now())
        )

        val (result, wasSanitized) = service.sanitize("Token: secret-token-123 was used")

        assertEquals("Token: [REDACTED] was used", result)
        assertTrue(wasSanitized)
    }

    @Test
    fun `sanitize redacts multiple matching config values`() {
        every { appConfigRepository.findAll() } returns listOf(
            AppConfig(key = "YNAB_TOKEN", value = "secret-token", updatedAt = Instant.now()),
            AppConfig(key = "GEMINI_KEY", value = "api-key-xyz", updatedAt = Instant.now())
        )

        val (result, wasSanitized) = service.sanitize("token=secret-token key=api-key-xyz")

        assertEquals("token=[REDACTED] key=[REDACTED]", result)
        assertTrue(wasSanitized)
    }

    @Test
    fun `sanitize redacts value appearing in the user description`() {
        every { appConfigRepository.findAll() } returns listOf(
            AppConfig(key = "FASTMAIL_API_TOKEN", value = "fmjt_supersecret", updatedAt = Instant.now())
        )

        val (result, wasSanitized) = service.sanitize("My API token is fmjt_supersecret")

        assertEquals("My API token is [REDACTED]", result)
        assertTrue(wasSanitized)
    }

    @Test
    fun `sanitize returns false when no config value matches`() {
        every { appConfigRepository.findAll() } returns listOf(
            AppConfig(key = "YNAB_TOKEN", value = "secret-token", updatedAt = Instant.now())
        )

        val (result, wasSanitized) = service.sanitize("No sensitive data here")

        assertEquals("No sensitive data here", result)
        assertFalse(wasSanitized)
    }

    @Test
    fun `sanitize does not redact non-sensitive config values like schedule or order cap`() {
        every { appConfigRepository.findAll() } returns listOf(
            AppConfig(key = "SCHEDULE_CONFIG", value = """{"type":"HOURLY"}""", updatedAt = Instant.now()),
            AppConfig(key = "ORDER_CAP", value = "10", updatedAt = Instant.now()),
            AppConfig(key = "START_FROM_DATE", value = "2024-01-01", updatedAt = Instant.now()),
            AppConfig(key = "INSTALLED_AT", value = "2024-01-01T00:00:00Z", updatedAt = Instant.now())
        )

        val text = """Schedule: {"type":"HOURLY"} cap=10 since=2024-01-01"""
        val (result, wasSanitized) = service.sanitize(text)

        assertEquals(text, result)
        assertFalse(wasSanitized)
    }

    @Test
    fun `sanitize redacts unknown future keys by default`() {
        every { appConfigRepository.findAll() } returns listOf(
            AppConfig(key = "FUTURE_SECRET_KEY", value = "brand-new-secret", updatedAt = Instant.now())
        )

        val (result, wasSanitized) = service.sanitize("value=brand-new-secret")

        assertEquals("value=[REDACTED]", result)
        assertTrue(wasSanitized)
    }

    @Test
    fun `sanitize ignores blank config values`() {
        every { appConfigRepository.findAll() } returns listOf(
            AppConfig(key = "YNAB_TOKEN", value = "", updatedAt = Instant.now()),
            AppConfig(key = "GEMINI_KEY", value = "   ", updatedAt = Instant.now())
        )

        val (result, wasSanitized) = service.sanitize("Some text")

        assertEquals("Some text", result)
        assertFalse(wasSanitized)
    }
}
