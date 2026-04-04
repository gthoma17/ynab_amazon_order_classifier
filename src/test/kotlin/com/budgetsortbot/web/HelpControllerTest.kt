package com.budgetsortbot.web

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.budgetsortbot.domain.SyncLog
import com.budgetsortbot.domain.SyncSource
import com.budgetsortbot.domain.SyncStatus
import com.budgetsortbot.infrastructure.persistence.SyncLogRepository
import com.budgetsortbot.service.ApplicationLogService
import com.budgetsortbot.service.ReportSanitizationService
import com.budgetsortbot.web.dto.HelpReportRequest
import io.mockk.every
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

@WebMvcTest(HelpController::class)
class HelpControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var syncLogRepository: SyncLogRepository

    @MockkBean
    private lateinit var applicationLogService: ApplicationLogService

    @MockkBean
    private lateinit var reportSanitizationService: ReportSanitizationService

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `POST api help report returns assembled body with description`() {
        every { syncLogRepository.findAll(any<Pageable>()) } returns PageImpl(emptyList())
        every { applicationLogService.getRecentLogs(any()) } returns emptyList()
        every { reportSanitizationService.sanitize(any()) } answers { firstArg<String>() to false }

        val request = HelpReportRequest(description = "Something broke", includeSyncLogs = false)

        mockMvc.perform(
            post("/api/help/report")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.body").value(containsString("Something broke")))
            .andExpect(jsonPath("$.sanitized").value(false))
    }

    @Test
    fun `POST api help report includes sync logs when includeSyncLogs is true`() {
        val log = SyncLog(
            id = 1L,
            source = SyncSource.EMAIL,
            lastRun = Instant.parse("2024-01-15T10:00:00Z"),
            status = SyncStatus.SUCCESS,
            message = null
        )
        every { syncLogRepository.findAll(any<Pageable>()) } returns PageImpl(listOf(log))
        every { applicationLogService.getRecentLogs(any()) } returns emptyList()
        every { reportSanitizationService.sanitize(any()) } answers { firstArg<String>() to false }

        val request = HelpReportRequest(description = "Something broke", includeSyncLogs = true)

        mockMvc.perform(
            post("/api/help/report")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.body").value(containsString("Recent Sync Logs")))
            .andExpect(jsonPath("$.body").value(containsString("EMAIL")))
    }

    @Test
    fun `POST api help report includes empty log note when sync_logs table is empty`() {
        every { syncLogRepository.findAll(any<Pageable>()) } returns PageImpl(emptyList())
        every { applicationLogService.getRecentLogs(any()) } returns emptyList()
        every { reportSanitizationService.sanitize(any()) } answers { firstArg<String>() to false }

        val request = HelpReportRequest(description = "Something broke", includeSyncLogs = true)

        mockMvc.perform(
            post("/api/help/report")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.body").value(containsString("No sync log entries found")))
    }

    @Test
    fun `POST api help report returns sanitized true when sensitive values were redacted`() {
        every { syncLogRepository.findAll(any<Pageable>()) } returns PageImpl(emptyList())
        every { applicationLogService.getRecentLogs(any()) } returns emptyList()
        every { reportSanitizationService.sanitize(any()) } answers {
            firstArg<String>().replace("secret-token", "[REDACTED]") to true
        }

        val request = HelpReportRequest(description = "Token is secret-token", includeSyncLogs = false)

        mockMvc.perform(
            post("/api/help/report")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.sanitized").value(true))
            .andExpect(jsonPath("$.body").value(containsString("[REDACTED]")))
    }

    @Test
    fun `POST api help report does not include sync logs section when includeSyncLogs is false`() {
        every { syncLogRepository.findAll(any<Pageable>()) } returns PageImpl(emptyList())
        every { applicationLogService.getRecentLogs(any()) } returns emptyList()
        every { reportSanitizationService.sanitize(any()) } answers { firstArg<String>() to false }

        val request = HelpReportRequest(description = "Something broke", includeSyncLogs = false)

        mockMvc.perform(
            post("/api/help/report")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.body").value(not(containsString("Recent Sync Logs"))))
    }

    @Test
    fun `POST api help report truncates body when assembled content exceeds max characters`() {
        every { syncLogRepository.findAll(any<Pageable>()) } returns PageImpl(emptyList())
        every { applicationLogService.getRecentLogs(any()) } returns emptyList()
        every { reportSanitizationService.sanitize(any()) } answers { firstArg<String>() to false }

        val longDescription = "x".repeat(9000)
        val request = HelpReportRequest(description = longDescription, includeSyncLogs = false)

        mockMvc.perform(
            post("/api/help/report")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.body").value(containsString("truncated")))
    }

    @Test
    fun `POST api help report includes sync logs limited to 4 most recent entries`() {
        val logs = (1..6).map { i ->
            SyncLog(
                id = i.toLong(),
                source = SyncSource.EMAIL,
                lastRun = Instant.parse("2024-01-${10 + i}T10:00:00Z"),
                status = SyncStatus.SUCCESS,
                message = null
            )
        }
        every { syncLogRepository.findAll(any<Pageable>()) } answers {
            val pageable = firstArg<Pageable>()
            // Only return up to the requested page size to simulate the limit
            PageImpl(logs.take(pageable.pageSize))
        }
        every { applicationLogService.getRecentLogs(any()) } returns emptyList()
        every { reportSanitizationService.sanitize(any()) } answers { firstArg<String>() to false }

        val request = HelpReportRequest(description = "Something broke", includeSyncLogs = true)

        mockMvc.perform(
            post("/api/help/report")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.body").value(containsString("Recent Sync Logs")))
    }

    @Test
    fun `POST api help report includes app logs section when includeAppLogs is true`() {
        every { syncLogRepository.findAll(any<Pageable>()) } returns PageImpl(emptyList())
        every { applicationLogService.getRecentLogs(any()) } returns listOf(
            "2024-01-15 10:00:01.000 DEBUG --- [main] com.budgetsortbot.App : Starting application",
            "2024-01-15 10:00:02.000 DEBUG --- [main] com.budgetsortbot.App : Application started"
        )
        every { reportSanitizationService.sanitize(any()) } answers { firstArg<String>() to false }

        val request = HelpReportRequest(
            description = "Something broke",
            includeSyncLogs = false,
            includeAppLogs = true
        )

        mockMvc.perform(
            post("/api/help/report")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.body").value(containsString("Application Logs")))
            .andExpect(jsonPath("$.body").value(containsString("Starting application")))
    }

    @Test
    fun `POST api help report does not include app logs section when includeAppLogs is false`() {
        every { syncLogRepository.findAll(any<Pageable>()) } returns PageImpl(emptyList())
        every { applicationLogService.getRecentLogs(any()) } returns emptyList()
        every { reportSanitizationService.sanitize(any()) } answers { firstArg<String>() to false }

        val request = HelpReportRequest(
            description = "Something broke",
            includeSyncLogs = false,
            includeAppLogs = false
        )

        mockMvc.perform(
            post("/api/help/report")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.body").value(not(containsString("Application Logs"))))
    }

    @Test
    fun `POST api help report shows unavailable note when app log retrieval fails`() {
        every { syncLogRepository.findAll(any<Pageable>()) } returns PageImpl(emptyList())
        every { applicationLogService.getRecentLogs(any()) } returns null
        every { reportSanitizationService.sanitize(any()) } answers { firstArg<String>() to false }

        val request = HelpReportRequest(
            description = "Something broke",
            includeSyncLogs = false,
            includeAppLogs = true
        )

        mockMvc.perform(
            post("/api/help/report")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.body").value(containsString("Application Logs")))
            .andExpect(jsonPath("$.body").value(containsString("Application logs unavailable")))
    }

    @Test
    fun `POST api help report shows empty note when app log table has no entries`() {
        every { syncLogRepository.findAll(any<Pageable>()) } returns PageImpl(emptyList())
        every { applicationLogService.getRecentLogs(any()) } returns emptyList()
        every { reportSanitizationService.sanitize(any()) } answers { firstArg<String>() to false }

        val request = HelpReportRequest(
            description = "Something broke",
            includeSyncLogs = false,
            includeAppLogs = true
        )

        mockMvc.perform(
            post("/api/help/report")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.body").value(containsString("Application Logs")))
            .andExpect(jsonPath("$.body").value(containsString("No application log entries found")))
    }
}
