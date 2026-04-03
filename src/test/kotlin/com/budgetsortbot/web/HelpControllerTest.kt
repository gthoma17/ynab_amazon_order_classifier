package com.budgetsortbot.web

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.budgetsortbot.domain.SyncLog
import com.budgetsortbot.domain.SyncSource
import com.budgetsortbot.domain.SyncStatus
import com.budgetsortbot.infrastructure.persistence.SyncLogRepository
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
    private lateinit var reportSanitizationService: ReportSanitizationService

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `POST api help report returns assembled body with description`() {
        every { syncLogRepository.findAll(any<Pageable>()) } returns PageImpl(emptyList())
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
        every { reportSanitizationService.sanitize(any()) } answers { firstArg<String>() to false }

        val longDescription = "x".repeat(5000)
        val request = HelpReportRequest(description = longDescription, includeSyncLogs = false)

        mockMvc.perform(
            post("/api/help/report")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.body").value(containsString("truncated")))
    }
}
