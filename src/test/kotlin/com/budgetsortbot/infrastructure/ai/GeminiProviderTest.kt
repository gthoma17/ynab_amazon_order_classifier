package com.budgetsortbot.infrastructure.ai

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.budgetsortbot.domain.CategoryRule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.mock.http.client.MockClientHttpRequest
import org.springframework.test.web.client.MockRestServiceServer
import java.nio.charset.StandardCharsets
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestTemplate
import java.time.Instant

class GeminiProviderTest {

    private lateinit var mockServer: MockRestServiceServer
    private lateinit var geminiProvider: GeminiProvider

    @BeforeEach
    fun setup() {
        val restTemplate = RestTemplate()
        mockServer = MockRestServiceServer.createServer(restTemplate)
        val objectMapper = jacksonObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        geminiProvider = GeminiProvider(RestClient.builder().requestFactory(restTemplate.requestFactory), objectMapper)
    }

    private val sampleRules = listOf(
        CategoryRule(
            id = 1L,
            ynabCategoryId = "cat-electronics",
            ynabCategoryName = "Electronics",
            userDescription = "Tech gadgets, cables, phones, computers",
            updatedAt = Instant.now()
        ),
        CategoryRule(
            id = 2L,
            ynabCategoryId = "cat-household",
            ynabCategoryName = "Household",
            userDescription = "Cleaning supplies, kitchen items, home goods",
            updatedAt = Instant.now()
        )
    )

    @Test
    fun `classify sends POST to Gemini and returns trimmed category ID from response`() {
        mockServer.expect(requestTo("${GeminiProvider.BASE_URL}/models/${GeminiProvider.MODEL}:generateContent?key=gemini-api-key"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(
                """{"candidates":[{"content":{"parts":[{"text":"cat-electronics\n"}]}}]}""",
                MediaType.APPLICATION_JSON
            ))

        val result = geminiProvider.classify(
            items = listOf("USB Cable", "Phone Case"),
            rules = sampleRules,
            apiKey = "gemini-api-key"
        )

        assertEquals("cat-electronics", result)
        mockServer.verify()
    }

    @Test
    fun `classify includes items and category rules in prompt`() {
        mockServer.expect(requestTo("${GeminiProvider.BASE_URL}/models/${GeminiProvider.MODEL}:generateContent?key=my-key"))
            .andExpect(method(HttpMethod.POST))
            .andExpect { request ->
                val body = (request as MockClientHttpRequest).bodyAsString
                assert(body.contains("USB Cable")) { "Prompt should contain item 'USB Cable'" }
                assert(body.contains("cat-electronics")) { "Prompt should contain category ID 'cat-electronics'" }
                assert(body.contains("Electronics")) { "Prompt should contain category name 'Electronics'" }
                assert(body.contains("Tech gadgets")) { "Prompt should contain user description" }
            }
            .andRespond(withSuccess(
                """{"candidates":[{"content":{"parts":[{"text":"cat-electronics"}]}}]}""",
                MediaType.APPLICATION_JSON
            ))

        geminiProvider.classify(
            items = listOf("USB Cable"),
            rules = sampleRules,
            apiKey = "my-key"
        )

        mockServer.verify()
    }

    @Test
    fun `classify throws RuntimeException when Gemini returns empty candidates`() {
        mockServer.expect(requestTo("${GeminiProvider.BASE_URL}/models/${GeminiProvider.MODEL}:generateContent?key=my-key"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""{"candidates":[]}""", MediaType.APPLICATION_JSON))

        assertThrows(RuntimeException::class.java) {
            geminiProvider.classify(
                items = listOf("Item"),
                rules = sampleRules,
                apiKey = "my-key"
            )
        }
        mockServer.verify()
    }

    @Test
    fun `classify throws RuntimeException when Gemini returns no parts in content`() {
        mockServer.expect(requestTo("${GeminiProvider.BASE_URL}/models/${GeminiProvider.MODEL}:generateContent?key=my-key"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(
                """{"candidates":[{"content":{"parts":[]}}]}""",
                MediaType.APPLICATION_JSON
            ))

        assertThrows(RuntimeException::class.java) {
            geminiProvider.classify(
                items = listOf("Item"),
                rules = sampleRules,
                apiKey = "my-key"
            )
        }
        mockServer.verify()
    }
}
