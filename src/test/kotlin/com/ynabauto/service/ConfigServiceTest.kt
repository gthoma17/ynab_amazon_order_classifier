package com.ynabauto.service

import com.ynabauto.domain.AppConfig
import com.ynabauto.domain.CategoryRule
import com.ynabauto.infrastructure.persistence.AppConfigRepository
import com.ynabauto.infrastructure.persistence.CategoryRuleRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional

class ConfigServiceTest {

    private val appConfigRepository = mockk<AppConfigRepository>()
    private val categoryRuleRepository = mockk<CategoryRuleRepository>()
    private lateinit var configService: ConfigService

    @BeforeEach
    fun setup() {
        configService = ConfigService(appConfigRepository, categoryRuleRepository)
    }

    @Test
    fun `getValue returns value when key exists`() {
        val config = AppConfig(key = ConfigService.YNAB_TOKEN, value = "my-ynab-token", updatedAt = Instant.now())
        every { appConfigRepository.findById(ConfigService.YNAB_TOKEN) } returns Optional.of(config)

        val result = configService.getValue(ConfigService.YNAB_TOKEN)

        assertEquals("my-ynab-token", result)
    }

    @Test
    fun `getValue returns null when key does not exist`() {
        every { appConfigRepository.findById(ConfigService.YNAB_TOKEN) } returns Optional.empty()

        val result = configService.getValue(ConfigService.YNAB_TOKEN)

        assertNull(result)
    }

    @Test
    fun `setValue saves AppConfig with correct key and value`() {
        val savedSlot = slot<AppConfig>()
        every { appConfigRepository.save(capture(savedSlot)) } answers { firstArg() }

        configService.setValue(ConfigService.FASTMAIL_API_TOKEN, "fmjt_test-token")

        assertEquals(ConfigService.FASTMAIL_API_TOKEN, savedSlot.captured.key)
        assertEquals("fmjt_test-token", savedSlot.captured.value)
    }

    @Test
    fun `getAllCategoryRules returns all rules from repository`() {
        val rules = listOf(
            CategoryRule(id = 1L, ynabCategoryId = "cat-1", ynabCategoryName = "Food", userDescription = "Groceries", updatedAt = Instant.now()),
            CategoryRule(id = 2L, ynabCategoryId = "cat-2", ynabCategoryName = "Tech", userDescription = "Electronics", updatedAt = Instant.now())
        )
        every { categoryRuleRepository.findAll() } returns rules

        val result = configService.getAllCategoryRules()

        assertEquals(2, result.size)
        assertEquals("cat-1", result[0].ynabCategoryId)
    }

    @Test
    fun `saveCategoryRules delegates to repository saveAll`() {
        val rules = listOf(
            CategoryRule(id = null, ynabCategoryId = "cat-1", ynabCategoryName = "Food", userDescription = "Groceries", updatedAt = Instant.now())
        )
        every { categoryRuleRepository.saveAll(rules) } returns rules

        configService.saveCategoryRules(rules)

        verify { categoryRuleRepository.saveAll(rules) }
    }

    @Test
    fun `key constants have expected values`() {
        assertEquals("YNAB_TOKEN", ConfigService.YNAB_TOKEN)
        assertEquals("YNAB_BUDGET_ID", ConfigService.YNAB_BUDGET_ID)
        assertEquals("FASTMAIL_API_TOKEN", ConfigService.FASTMAIL_API_TOKEN)
        assertEquals("GEMINI_KEY", ConfigService.GEMINI_KEY)
    }
}
