package com.ynabauto.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ynabauto.domain.AmazonOrder
import com.ynabauto.domain.CategoryRule
import com.ynabauto.domain.OrderStatus
import com.ynabauto.infrastructure.ai.ClassificationProvider
import com.ynabauto.infrastructure.persistence.CategoryRuleRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.math.BigDecimal
import java.time.Instant

@ExtendWith(MockitoExtension::class)
class ClassificationServiceTest {

    @Mock
    private lateinit var classificationProvider: ClassificationProvider

    @Mock
    private lateinit var categoryRuleRepository: CategoryRuleRepository

    @Mock
    private lateinit var configService: ConfigService

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
        `when`(configService.getValue(ConfigService.GEMINI_KEY)).thenReturn("gemini-key")
        `when`(categoryRuleRepository.findAll()).thenReturn(sampleRules)
        `when`(classificationProvider.classify(listOf("USB Cable"), sampleRules, "gemini-key")).thenReturn("cat-electronics")

        val result = classificationService.classify(makeOrder("""["USB Cable"]"""))

        assertEquals("cat-electronics", result)
    }

    @Test
    fun `classify passes parsed items list to provider`() {
        `when`(configService.getValue(ConfigService.GEMINI_KEY)).thenReturn("gemini-key")
        `when`(categoryRuleRepository.findAll()).thenReturn(sampleRules)
        `when`(classificationProvider.classify(listOf("Item A", "Item B"), sampleRules, "gemini-key")).thenReturn("cat-household")

        val result = classificationService.classify(makeOrder("""["Item A","Item B"]"""))

        assertEquals("cat-household", result)
    }

    @Test
    fun `classify throws RuntimeException when Gemini key is not configured`() {
        `when`(configService.getValue(ConfigService.GEMINI_KEY)).thenReturn(null)

        assertThrows<RuntimeException> {
            classificationService.classify(makeOrder("""["USB Cable"]"""))
        }
    }

    @Test
    fun `classify throws RuntimeException when no category rules are configured`() {
        `when`(configService.getValue(ConfigService.GEMINI_KEY)).thenReturn("gemini-key")
        `when`(categoryRuleRepository.findAll()).thenReturn(emptyList())

        assertThrows<RuntimeException> {
            classificationService.classify(makeOrder("""["USB Cable"]"""))
        }
    }
}
