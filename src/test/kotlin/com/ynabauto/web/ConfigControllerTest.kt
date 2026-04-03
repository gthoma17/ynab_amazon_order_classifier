package com.ynabauto.web

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.ynabauto.domain.CategoryRule
import com.ynabauto.service.ConfigService
import com.ynabauto.service.ConnectionProbeService
import com.ynabauto.web.dto.ApiKeysRequest
import com.ynabauto.web.dto.ProbeResult
import io.mockk.every
import io.mockk.justRun
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

@WebMvcTest(ConfigController::class)
class ConfigControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var configService: ConfigService

    @MockkBean
    private lateinit var connectionProbeService: ConnectionProbeService

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `GET api config keys returns all key values`() {
        every { configService.getValue(ConfigService.YNAB_TOKEN) } returns "my-ynab-token"
        every { configService.getValue(ConfigService.YNAB_BUDGET_ID) } returns "budget-123"
        every { configService.getValue(ConfigService.FASTMAIL_USER) } returns "user@fastmail.com"
        every { configService.getValue(ConfigService.FASTMAIL_TOKEN) } returns null
        every { configService.getValue(ConfigService.GEMINI_KEY) } returns null

        mockMvc.perform(get("/api/config/keys"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ynabToken").value("my-ynab-token"))
            .andExpect(jsonPath("$.ynabBudgetId").value("budget-123"))
            .andExpect(jsonPath("$.fastmailUser").value("user@fastmail.com"))
            .andExpect(jsonPath("$.fastmailToken").doesNotExist())
            .andExpect(jsonPath("$.geminiKey").doesNotExist())
    }

    @Test
    fun `PUT api config keys updates only provided keys`() {
        val request = ApiKeysRequest(ynabToken = "new-token", ynabBudgetId = "new-budget")
        justRun { configService.setValue(any(), any()) }

        mockMvc.perform(
            put("/api/config/keys")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isNoContent)

        verify { configService.setValue(ConfigService.YNAB_TOKEN, "new-token") }
        verify { configService.setValue(ConfigService.YNAB_BUDGET_ID, "new-budget") }
    }

    @Test
    fun `GET api config categories returns all category rules`() {
        val rules = listOf(
            CategoryRule(id = 1L, ynabCategoryId = "cat-1", ynabCategoryName = "Food", userDescription = "Groceries", updatedAt = Instant.now()),
            CategoryRule(id = 2L, ynabCategoryId = "cat-2", ynabCategoryName = "Tech", userDescription = "Electronics", updatedAt = Instant.now())
        )
        every { configService.getAllCategoryRules() } returns rules

        mockMvc.perform(get("/api/config/categories"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].ynabCategoryId").value("cat-1"))
            .andExpect(jsonPath("$[0].ynabCategoryName").value("Food"))
            .andExpect(jsonPath("$[1].ynabCategoryId").value("cat-2"))
    }

    @Test
    fun `PUT api config categories saves category rules`() {
        val capturedRules = slot<List<CategoryRule>>()
        justRun { configService.saveCategoryRules(capture(capturedRules)) }

        val body = """
            [
              {"ynabCategoryId":"cat-1","ynabCategoryName":"Food","userDescription":"Groceries"},
              {"ynabCategoryId":"cat-2","ynabCategoryName":"Tech","userDescription":"Electronics"}
            ]
        """.trimIndent()

        mockMvc.perform(
            put("/api/config/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(status().isNoContent)

        assertEquals(2, capturedRules.captured.size)
        assertEquals("cat-1", capturedRules.captured[0].ynabCategoryId)
        assertEquals("cat-2", capturedRules.captured[1].ynabCategoryId)
    }

    // --- probe endpoints ---

    @Test
    fun `POST api config probe fastmail returns probe result`() {
        every { connectionProbeService.probeFastMail() } returns ProbeResult(success = true, message = "Connected")

        mockMvc.perform(post("/api/config/probe/fastmail"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Connected"))
    }

    @Test
    fun `POST api config probe fastmail returns failure result`() {
        every { connectionProbeService.probeFastMail() } returns ProbeResult(success = false, message = "401 Unauthorized — check your credentials")

        mockMvc.perform(post("/api/config/probe/fastmail"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("401 Unauthorized — check your credentials"))
    }

    @Test
    fun `POST api config probe ynab returns probe result`() {
        every { connectionProbeService.probeYnab() } returns ProbeResult(success = true, message = "Connected")

        mockMvc.perform(post("/api/config/probe/ynab"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Connected"))
    }

    @Test
    fun `POST api config probe gemini returns probe result`() {
        every { connectionProbeService.probeGemini() } returns ProbeResult(success = true, message = "Connected")

        mockMvc.perform(post("/api/config/probe/gemini"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Connected"))
    }
}
