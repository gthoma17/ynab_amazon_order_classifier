package com.ynabauto.service

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestTemplate
import java.io.IOException

class ConnectionProbeServiceTest {

    private lateinit var mockServer: MockRestServiceServer
    private lateinit var configService: ConfigService
    private lateinit var probeService: ConnectionProbeService

    @BeforeEach
    fun setup() {
        val restTemplate = RestTemplate()
        mockServer = MockRestServiceServer.createServer(restTemplate)
        configService = mockk()
        probeService = ConnectionProbeService(
            restClientBuilder = RestClient.builder().requestFactory(restTemplate.requestFactory),
            configService = configService
        )
    }

    // --- probeFastMail ---

    @Test
    fun `probeFastMail returns success when 200 response`() {
        every { configService.getValue(ConfigService.FASTMAIL_API_TOKEN) } returns "test-token"

        mockServer.expect(requestTo("https://api.fastmail.com/.well-known/jmap"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer test-token"))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))

        val result = probeService.probeFastMail()

        assertTrue(result.success)
        assertEquals("Connected", result.message)
        mockServer.verify()
    }

    @Test
    fun `probeFastMail returns auth error on 401`() {
        every { configService.getValue(ConfigService.FASTMAIL_API_TOKEN) } returns "bad-token"

        mockServer.expect(requestTo("https://api.fastmail.com/.well-known/jmap"))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED))

        val result = probeService.probeFastMail()

        assertFalse(result.success)
        assertTrue(result.message.contains("401"), "Expected 401 in message: ${result.message}")
        assertTrue(result.message.contains("Unauthorized"), "Expected 'Unauthorized' in message: ${result.message}")
        mockServer.verify()
    }

    @Test
    fun `probeFastMail returns network error on IO failure`() {
        every { configService.getValue(ConfigService.FASTMAIL_API_TOKEN) } returns "test-token"

        mockServer.expect(requestTo("https://api.fastmail.com/.well-known/jmap"))
            .andRespond { _ -> throw IOException("Connection timed out") }

        val result = probeService.probeFastMail()

        assertFalse(result.success)
        assertTrue(result.message.contains("timed out") || result.message.contains("unavailable"),
            "Expected timeout message but got: ${result.message}")
        mockServer.verify()
    }

    @Test
    fun `probeFastMail returns failure without making HTTP call when credentials are empty`() {
        every { configService.getValue(ConfigService.FASTMAIL_API_TOKEN) } returns null

        val result = probeService.probeFastMail()

        assertFalse(result.success)
        mockServer.verify() // verifies no HTTP call was made
    }

    // --- probeYnab ---

    @Test
    fun `probeYnab returns success when 200 response`() {
        every { configService.getValue(ConfigService.YNAB_TOKEN) } returns "ynab-token"

        mockServer.expect(requestTo("https://api.ynab.com/v1/budgets"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer ynab-token"))
            .andRespond(withSuccess("""{"data":{"budgets":[]}}""", MediaType.APPLICATION_JSON))

        val result = probeService.probeYnab()

        assertTrue(result.success)
        assertEquals("Connected", result.message)
        mockServer.verify()
    }

    @Test
    fun `probeYnab returns auth error on 401`() {
        every { configService.getValue(ConfigService.YNAB_TOKEN) } returns "bad-token"

        mockServer.expect(requestTo("https://api.ynab.com/v1/budgets"))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED))

        val result = probeService.probeYnab()

        assertFalse(result.success)
        assertTrue(result.message.contains("401"), "Expected 401 in message: ${result.message}")
        assertTrue(result.message.contains("Unauthorized"), "Expected 'Unauthorized' in message: ${result.message}")
        mockServer.verify()
    }

    @Test
    fun `probeYnab returns failure without making HTTP call when credentials are empty`() {
        every { configService.getValue(ConfigService.YNAB_TOKEN) } returns ""

        val result = probeService.probeYnab()

        assertFalse(result.success)
        mockServer.verify()
    }

    // --- probeGemini ---

    @Test
    fun `probeGemini returns success when 200 response`() {
        every { configService.getValue(ConfigService.GEMINI_KEY) } returns "gemini-api-key"

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models?key=gemini-api-key"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""{"models":[]}""", MediaType.APPLICATION_JSON))

        val result = probeService.probeGemini()

        assertTrue(result.success)
        assertEquals("Connected", result.message)
        mockServer.verify()
    }

    @Test
    fun `probeGemini returns auth error on 400 invalid key`() {
        every { configService.getValue(ConfigService.GEMINI_KEY) } returns "bad-key"

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models?key=bad-key"))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST))

        val result = probeService.probeGemini()

        assertFalse(result.success)
        assertTrue(result.message.contains("400"), "Expected 400 in message: ${result.message}")
        mockServer.verify()
    }

    @Test
    fun `probeGemini returns auth error on 401`() {
        every { configService.getValue(ConfigService.GEMINI_KEY) } returns "bad-key"

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models?key=bad-key"))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED))

        val result = probeService.probeGemini()

        assertFalse(result.success)
        assertTrue(result.message.contains("401"), "Expected 401 in message: ${result.message}")
        assertTrue(result.message.contains("Unauthorized"), "Expected 'Unauthorized' in message: ${result.message}")
        mockServer.verify()
    }

    @Test
    fun `probeGemini returns failure without making HTTP call when credentials are empty`() {
        every { configService.getValue(ConfigService.GEMINI_KEY) } returns null

        val result = probeService.probeGemini()

        assertFalse(result.success)
        mockServer.verify()
    }

    @Test
    fun `probeGemini returns network error on IO failure`() {
        every { configService.getValue(ConfigService.GEMINI_KEY) } returns "test-key"

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models?key=test-key"))
            .andRespond { _ -> throw IOException("Connection reset by peer") }

        val result = probeService.probeGemini()

        assertFalse(result.success)
        assertTrue(result.message.contains("timed out") || result.message.contains("unavailable"),
            "Expected timeout/unavailable message but got: ${result.message}")
        mockServer.verify()
    }
}
