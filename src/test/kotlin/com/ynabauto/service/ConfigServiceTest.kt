package com.ynabauto.service

import com.ynabauto.domain.AppConfig
import com.ynabauto.domain.CategoryRule
import com.ynabauto.infrastructure.persistence.AppConfigRepository
import com.ynabauto.infrastructure.persistence.CategoryRuleRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.time.Instant
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class ConfigServiceTest {

    @Mock
    private lateinit var appConfigRepository: AppConfigRepository

    @Mock
    private lateinit var categoryRuleRepository: CategoryRuleRepository

    private lateinit var configService: ConfigService

    @BeforeEach
    fun setup() {
        configService = ConfigService(appConfigRepository, categoryRuleRepository)
    }

    @Test
    fun `getValue returns value when key exists`() {
        val config = AppConfig(key = ConfigService.YNAB_TOKEN, value = "my-ynab-token", updatedAt = Instant.now())
        `when`(appConfigRepository.findById(ConfigService.YNAB_TOKEN)).thenReturn(Optional.of(config))

        val result = configService.getValue(ConfigService.YNAB_TOKEN)

        assertEquals("my-ynab-token", result)
    }

    @Test
    fun `getValue returns null when key does not exist`() {
        `when`(appConfigRepository.findById(ConfigService.YNAB_TOKEN)).thenReturn(Optional.empty())

        val result = configService.getValue(ConfigService.YNAB_TOKEN)

        assertNull(result)
    }

    @Test
    fun `setValue saves AppConfig with correct key and value`() {
        configService.setValue(ConfigService.FASTMAIL_USER, "user@fastmail.com")

        val captor = ArgumentCaptor.forClass(AppConfig::class.java)
        verify(appConfigRepository).save(captor.capture())
        assertEquals(ConfigService.FASTMAIL_USER, captor.value.key)
        assertEquals("user@fastmail.com", captor.value.value)
    }

    @Test
    fun `getAllCategoryRules returns all rules from repository`() {
        val rules = listOf(
            CategoryRule(id = 1L, ynabCategoryId = "cat-1", ynabCategoryName = "Food", userDescription = "Groceries", updatedAt = Instant.now()),
            CategoryRule(id = 2L, ynabCategoryId = "cat-2", ynabCategoryName = "Tech", userDescription = "Electronics", updatedAt = Instant.now())
        )
        `when`(categoryRuleRepository.findAll()).thenReturn(rules)

        val result = configService.getAllCategoryRules()

        assertEquals(2, result.size)
        assertEquals("cat-1", result[0].ynabCategoryId)
    }

    @Test
    fun `saveCategoryRules delegates to repository saveAll`() {
        val rules = listOf(
            CategoryRule(id = null, ynabCategoryId = "cat-1", ynabCategoryName = "Food", userDescription = "Groceries", updatedAt = Instant.now())
        )

        configService.saveCategoryRules(rules)

        verify(categoryRuleRepository).saveAll(rules)
    }

    @Test
    fun `key constants have expected values`() {
        assertEquals("YNAB_TOKEN", ConfigService.YNAB_TOKEN)
        assertEquals("YNAB_BUDGET_ID", ConfigService.YNAB_BUDGET_ID)
        assertEquals("FASTMAIL_USER", ConfigService.FASTMAIL_USER)
        assertEquals("FASTMAIL_TOKEN", ConfigService.FASTMAIL_TOKEN)
        assertEquals("GEMINI_KEY", ConfigService.GEMINI_KEY)
    }
}
