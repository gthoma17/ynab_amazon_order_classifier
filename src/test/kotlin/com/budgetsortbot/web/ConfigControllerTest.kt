package com.budgetsortbot.web

import com.budgetsortbot.domain.CategoryRule
import com.budgetsortbot.domain.DryRunResult
import com.budgetsortbot.service.ConfigService
import com.budgetsortbot.service.ConnectionProbeService
import com.budgetsortbot.service.DryRunService
import com.budgetsortbot.service.ScheduleConfig
import com.budgetsortbot.service.ScheduleType
import com.budgetsortbot.service.SyncScheduler
import com.budgetsortbot.web.dto.ApiKeysRequest
import com.budgetsortbot.web.dto.ProbeResult
import com.budgetsortbot.web.dto.ProcessingConfigRequest
import com.budgetsortbot.web.dto.ScheduleConfigDto
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
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
import java.math.BigDecimal
import java.time.Instant

@WebMvcTest(ConfigController::class)
class ConfigControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var configService: ConfigService

    @MockkBean
    private lateinit var connectionProbeService: ConnectionProbeService

    @MockkBean
    private lateinit var syncScheduler: SyncScheduler

    @MockkBean
    private lateinit var dryRunService: DryRunService

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `GET api config keys returns all key values`() {
        every { configService.getValue(ConfigService.YNAB_TOKEN) } returns "my-ynab-token"
        every { configService.getValue(ConfigService.YNAB_BUDGET_ID) } returns "budget-123"
        every { configService.getValue(ConfigService.FASTMAIL_API_TOKEN) } returns "fmjt_test-token"
        every { configService.getValue(ConfigService.GEMINI_KEY) } returns null

        mockMvc
            .perform(get("/api/config/keys"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ynabToken").value("my-ynab-token"))
            .andExpect(jsonPath("$.ynabBudgetId").value("budget-123"))
            .andExpect(jsonPath("$.fastmailApiToken").value("fmjt_test-token"))
            .andExpect(jsonPath("$.geminiKey").doesNotExist())
    }

    @Test
    fun `PUT api config keys updates only provided keys`() {
        val request = ApiKeysRequest(ynabToken = "new-token", ynabBudgetId = "new-budget")
        justRun { configService.setValue(any(), any()) }

        mockMvc
            .perform(
                put("/api/config/keys")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isNoContent)

        verify { configService.setValue(ConfigService.YNAB_TOKEN, "new-token") }
        verify { configService.setValue(ConfigService.YNAB_BUDGET_ID, "new-budget") }
    }

    @Test
    fun `GET api config categories returns all category rules`() {
        val rules =
            listOf(
                CategoryRule(
                    id = 1L,
                    ynabCategoryId = "cat-1",
                    ynabCategoryName = "Food",
                    userDescription = "Groceries",
                    updatedAt = Instant.now(),
                ),
                CategoryRule(
                    id = 2L,
                    ynabCategoryId = "cat-2",
                    ynabCategoryName = "Tech",
                    userDescription = "Electronics",
                    updatedAt = Instant.now(),
                ),
            )
        every { configService.getAllCategoryRules() } returns rules

        mockMvc
            .perform(get("/api/config/categories"))
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

        val body =
            """
            [
              {"ynabCategoryId":"cat-1","ynabCategoryName":"Food","userDescription":"Groceries"},
              {"ynabCategoryId":"cat-2","ynabCategoryName":"Tech","userDescription":"Electronics"}
            ]
            """.trimIndent()

        mockMvc
            .perform(
                put("/api/config/categories")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andExpect(status().isNoContent)

        assertEquals(2, capturedRules.captured.size)
        assertEquals("cat-1", capturedRules.captured[0].ynabCategoryId)
        assertEquals("cat-2", capturedRules.captured[1].ynabCategoryId)
    }

    // --- probe endpoints ---

    @Test
    fun `POST api config probe fastmail returns probe result`() {
        every { connectionProbeService.probeFastMail(any()) } returns ProbeResult(success = true, message = "Connected")

        mockMvc
            .perform(
                post("/api/config/probe/fastmail")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"fastmailApiToken":"fmjt_test"}"""),
            )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Connected"))
    }

    @Test
    fun `POST api config probe fastmail forwards token from request body to service`() {
        every { connectionProbeService.probeFastMail(eq("my-unsaved-token")) } returns
            ProbeResult(success = true, message = "Connected")

        mockMvc
            .perform(
                post("/api/config/probe/fastmail")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"fastmailApiToken":"my-unsaved-token"}"""),
            )
            .andExpect(status().isOk)

        verify { connectionProbeService.probeFastMail(eq("my-unsaved-token")) }
    }

    @Test
    fun `POST api config probe fastmail falls back when no body provided`() {
        every { connectionProbeService.probeFastMail(isNull()) } returns
            ProbeResult(success = false, message = "FastMail API token not configured")

        mockMvc
            .perform(post("/api/config/probe/fastmail"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(false))

        verify { connectionProbeService.probeFastMail(isNull()) }
    }

    @Test
    fun `POST api config probe fastmail returns failure result`() {
        every { connectionProbeService.probeFastMail(any()) } returns
            ProbeResult(success = false, message = "401 Unauthorized — check your credentials")

        mockMvc
            .perform(
                post("/api/config/probe/fastmail")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"fastmailApiToken":"bad-token"}"""),
            )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("401 Unauthorized — check your credentials"))
    }

    @Test
    fun `POST api config probe ynab returns probe result`() {
        every { connectionProbeService.probeYnab(any()) } returns ProbeResult(success = true, message = "Connected")

        mockMvc
            .perform(
                post("/api/config/probe/ynab")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"ynabToken":"my-ynab-token"}"""),
            )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Connected"))
    }

    @Test
    fun `POST api config probe ynab forwards token from request body to service`() {
        every { connectionProbeService.probeYnab(eq("my-unsaved-ynab-token")) } returns
            ProbeResult(success = true, message = "Connected")

        mockMvc
            .perform(
                post("/api/config/probe/ynab")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"ynabToken":"my-unsaved-ynab-token"}"""),
            )
            .andExpect(status().isOk)

        verify { connectionProbeService.probeYnab(eq("my-unsaved-ynab-token")) }
    }

    @Test
    fun `POST api config probe gemini returns probe result`() {
        every { connectionProbeService.probeGemini(any()) } returns ProbeResult(success = true, message = "Connected")

        mockMvc
            .perform(
                post("/api/config/probe/gemini")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"geminiKey":"my-gemini-key"}"""),
            )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Connected"))
    }

    @Test
    fun `POST api config probe gemini forwards key from request body to service`() {
        every { connectionProbeService.probeGemini(eq("my-unsaved-gemini-key")) } returns
            ProbeResult(success = true, message = "Connected")

        mockMvc
            .perform(
                post("/api/config/probe/gemini")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"geminiKey":"my-unsaved-gemini-key"}"""),
            )
            .andExpect(status().isOk)

        verify { connectionProbeService.probeGemini(eq("my-unsaved-gemini-key")) }
    }

    // --- processing config ---

    @Test
    fun `GET api config processing returns current settings`() {
        every { configService.getValue(ConfigService.ORDER_CAP) } returns "5"
        every { configService.getValue(ConfigService.START_FROM_DATE) } returns "2024-01-01"
        every { configService.getValue(ConfigService.INSTALLED_AT) } returns "2024-01-01T00:00:00Z"
        every { configService.getValue(ConfigService.SCHEDULE_CONFIG) } returns """{"type":"DAILY","hour":14,"minute":0}"""

        mockMvc
            .perform(get("/api/config/processing"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.orderCap").value(5))
            .andExpect(jsonPath("$.startFromDate").value("2024-01-01"))
            .andExpect(jsonPath("$.scheduleConfig.type").value("DAILY"))
    }

    @Test
    fun `GET api config processing returns secondInterval for EVERY_N_SECONDS schedule`() {
        every { configService.getValue(ConfigService.ORDER_CAP) } returns "0"
        every { configService.getValue(ConfigService.START_FROM_DATE) } returns null
        every { configService.getValue(ConfigService.INSTALLED_AT) } returns null
        every { configService.getValue(ConfigService.SCHEDULE_CONFIG) } returns
            """{"type":"EVERY_N_SECONDS","secondInterval":3}"""

        mockMvc
            .perform(get("/api/config/processing"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.scheduleConfig.type").value("EVERY_N_SECONDS"))
            .andExpect(jsonPath("$.scheduleConfig.secondInterval").value(3))
    }

    @Test
    fun `GET api config processing returns minuteInterval for EVERY_N_MINUTES schedule`() {
        every { configService.getValue(ConfigService.ORDER_CAP) } returns "0"
        every { configService.getValue(ConfigService.START_FROM_DATE) } returns null
        every { configService.getValue(ConfigService.INSTALLED_AT) } returns null
        every { configService.getValue(ConfigService.SCHEDULE_CONFIG) } returns
            """{"type":"EVERY_N_MINUTES","minuteInterval":15}"""

        mockMvc
            .perform(get("/api/config/processing"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.scheduleConfig.type").value("EVERY_N_MINUTES"))
            .andExpect(jsonPath("$.scheduleConfig.minuteInterval").value(15))
    }

    @Test
    fun `PUT api config processing saves order cap and triggers reschedule when schedule changes`() {
        justRun { configService.setValue(any(), any()) }
        justRun { syncScheduler.reschedule() }

        val request =
            ProcessingConfigRequest(
                orderCap = 10,
                startFromDate = "2024-06-01",
                scheduleConfig = ScheduleConfigDto(type = "DAILY", hour = 8, minute = 0),
            )

        mockMvc
            .perform(
                put("/api/config/processing")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isNoContent)

        verify { configService.setValue(ConfigService.ORDER_CAP, "10") }
        verify { configService.setValue(ConfigService.START_FROM_DATE, "2024-06-01") }
        verify { syncScheduler.reschedule() }
    }

    @Test
    fun `PUT api config processing preserves secondInterval so EVERY_N_SECONDS cron is valid`() {
        val savedJson = slot<String>()
        justRun { configService.setValue(any(), capture(savedJson)) }
        justRun { syncScheduler.reschedule() }

        val request =
            ProcessingConfigRequest(
                scheduleConfig = ScheduleConfigDto(type = "EVERY_N_SECONDS", secondInterval = 3),
            )

        mockMvc
            .perform(
                put("/api/config/processing")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isNoContent)

        // The JSON stored in the DB must carry secondInterval so that
        // ScheduleConfig.toCron() can produce a valid "*/3 * * * * *" expression.
        val stored = objectMapper.readValue(savedJson.captured, ScheduleConfig::class.java)
        assertEquals(ScheduleType.EVERY_N_SECONDS, stored.type)
        assertEquals(3, stored.secondInterval)
    }

    // --- dry run ---

    @Test
    fun `POST api config dry-run triggers dry run and returns results`() {
        val result =
            DryRunResult(
                id = 1L,
                orderId = 42L,
                orderDate = Instant.parse("2024-01-15T00:00:00Z"),
                totalAmount = BigDecimal("49.99"),
                itemsJson = """["Keyboard"]""",
                ynabTransactionId = "txn-1",
                proposedCategoryId = "cat-tech",
                proposedCategoryName = "Technology",
                runAt = Instant.parse("2024-01-20T00:00:00Z"),
            )
        justRun { dryRunService.runDryRun(any()) }
        every { dryRunService.getResults() } returns listOf(result)

        mockMvc
            .perform(
                post("/api/config/dry-run")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"startFromDate":"2024-01-01"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].ynabTransactionId").value("txn-1"))
            .andExpect(jsonPath("$[0].proposedCategoryName").value("Technology"))
    }

    @Test
    fun `GET api config dry-run results returns stored results`() {
        every { dryRunService.getResults() } returns emptyList()

        mockMvc
            .perform(get("/api/config/dry-run/results"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }
}
