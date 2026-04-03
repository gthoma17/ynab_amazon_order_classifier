package com.ynabauto.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.ActiveProfiles
import com.ynabauto.infrastructure.persistence.AppConfigRepository
import java.time.Instant

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AppConfigEntityTest {

    @Autowired
    private lateinit var appConfigRepository: AppConfigRepository

    @Test
    fun `can save and retrieve an AppConfig entry`() {
        val config = AppConfig(
            key = "YNAB_TOKEN",
            value = "secret-token-123",
            updatedAt = Instant.now()
        )

        val saved = appConfigRepository.save(config)

        assertNotNull(saved.key)
        assertEquals("YNAB_TOKEN", saved.key)
        assertEquals("secret-token-123", saved.value)
    }

    @Test
    fun `can find AppConfig by key`() {
        val config = AppConfig(
            key = "FASTMAIL_API_TOKEN",
            value = "fmjt_test-token",
            updatedAt = Instant.now()
        )
        appConfigRepository.save(config)

        val found = appConfigRepository.findById("FASTMAIL_API_TOKEN")

        assertTrue(found.isPresent)
        assertEquals("fmjt_test-token", found.get().value)
    }

    @Test
    fun `can update an existing AppConfig value`() {
        val config = AppConfig(
            key = "GEMINI_KEY",
            value = "original-key",
            updatedAt = Instant.now()
        )
        appConfigRepository.save(config)

        val updated = config.copy(value = "updated-key")
        appConfigRepository.save(updated)

        val found = appConfigRepository.findById("GEMINI_KEY")
        assertTrue(found.isPresent)
        assertEquals("updated-key", found.get().value)
    }

    @Test
    fun `can delete an AppConfig entry`() {
        val config = AppConfig(
            key = "BUDGET_ID",
            value = "budget-abc",
            updatedAt = Instant.now()
        )
        appConfigRepository.save(config)
        appConfigRepository.deleteById("BUDGET_ID")

        val found = appConfigRepository.findById("BUDGET_ID")
        assertFalse(found.isPresent)
    }
}
