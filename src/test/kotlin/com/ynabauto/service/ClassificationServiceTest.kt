package com.ynabauto.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ynabauto.domain.AmazonOrder
import com.ynabauto.domain.CategoryRule
import com.ynabauto.domain.OrderStatus
import com.ynabauto.infrastructure.ai.ClassificationProvider
import com.ynabauto.infrastructure.persistence.CategoryRuleRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.Instant

class ClassificationServiceTest {

    private val classificationProvider = mockk<ClassificationProvider>()
    private val categoryRuleRepository = mockk<CategoryRuleRepository>()
    private val configService = mockk<ConfigService>()
    private lateinit var classificationService: ClassificationService

    @BeforeEach
    fun setup() {
        classificationService = ClassificationService(
            classificationProvider, categoryRuleRepository, configService, jacksonObjectMapper()
        )
    }

    private val sampleRules = listOf(
        CategoryRule(id = 1L, ynabCategoryId = "cat-electronics", ynabCategoryName = "Electronics", userDescription = "Gadgets", updatedAt = Instant.now()),
        CategoryRule(id = 2L, ynabCategoryId = "cat-household", ynabCategoryName = "Household", userDescription = "Home items", updatedAt = Instant.now())
    )

    private fun makeOrder(itemsJson: String) = AmazonOrder(
        id = 1L,
        emailMessageId = "msg-1@amazon.com",
        orderDate = Instant.now(),
        totalAmount = BigDecimal("49.99"),
        itemsJson = itemsJson,
        status = OrderStatus.MATCHED,
        createdAt = Instant.now()
    )

    @Test
    fun `classify returns category ID from provider`() {
        every { configService.getValue(ConfigService.GEMINI_KEY) } returns "gemini-key"
        every { categoryRuleRepository.findAll() } returns sampleRules
        every { classificationProvider.classify(listOf("USB Cable"), sampleRules, "gemini-key") } returns "cat-electronics"

        val result = classificationService.classify(makeOrder("""["USB Cable"]"""))

        assertEquals("cat-electronics", result)
    }

    @Test
    fun `classify passes parsed items list to provider`() {
        every { configService.getValue(ConfigService.GEMINI_KEY) } returns "gemini-key"
        every { categoryRuleRepository.findAll() } returns sampleRules
        every { classificationProvider.classify(listOf("Item A", "Item B"), sampleRules, "gemini-key") } returns "cat-household"

        val result = classificationService.classify(makeOrder("""["Item A","Item B"]"""))

        assertEquals("cat-household", result)
    }

    @Test
    fun `classify throws RuntimeException when Gemini key is not configured`() {
        every { configService.getValue(ConfigService.GEMINI_KEY) } returns null

        assertThrows<RuntimeException> {
            classificationService.classify(makeOrder("""["USB Cable"]"""))
        }
    }

    @Test
    fun `classify throws RuntimeException when no category rules are configured`() {
        every { configService.getValue(ConfigService.GEMINI_KEY) } returns "gemini-key"
        every { categoryRuleRepository.findAll() } returns emptyList()

        assertThrows<RuntimeException> {
            classificationService.classify(makeOrder("""["USB Cable"]"""))
        }
    }
}
