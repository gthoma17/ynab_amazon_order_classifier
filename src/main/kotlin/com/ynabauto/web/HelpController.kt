package com.ynabauto.web

import com.ynabauto.infrastructure.persistence.SyncLogRepository
import com.ynabauto.service.ReportSanitizationService
import com.ynabauto.web.dto.HelpReportRequest
import com.ynabauto.web.dto.HelpReportResponse
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/help")
class HelpController(
    private val syncLogRepository: SyncLogRepository,
    private val reportSanitizationService: ReportSanitizationService
) {

    companion object {
        private const val SYNC_LOG_LIMIT = 50
        private const val MAX_BODY_CHARACTERS = 4000
        private const val TRUNCATION_NOTE =
            "\n\n_[Log content truncated — use `docker logs` for full output]_"
    }

    @PostMapping("/report")
    fun createReport(@RequestBody request: HelpReportRequest): HelpReportResponse {
        val bodyBuilder = StringBuilder()

        bodyBuilder.appendLine("## Problem Description")
        bodyBuilder.appendLine()
        bodyBuilder.appendLine(request.description.trim())
        bodyBuilder.appendLine()

        if (request.includeSyncLogs) {
            bodyBuilder.appendLine("## Recent Sync Logs")
            bodyBuilder.appendLine()

            val logs = syncLogRepository.findAll(
                PageRequest.of(0, SYNC_LOG_LIMIT, Sort.by(Sort.Direction.DESC, "lastRun"))
            ).content

            if (logs.isEmpty()) {
                bodyBuilder.appendLine("_No sync log entries found._")
            } else {
                bodyBuilder.appendLine("| Source | Last Run | Status | Message |")
                bodyBuilder.appendLine("| --- | --- | --- | --- |")
                for (log in logs) {
                    val message = log.message?.replace("|", "\\|") ?: ""
                    bodyBuilder.appendLine("| ${log.source} | ${log.lastRun} | ${log.status} | $message |")
                }
            }
            bodyBuilder.appendLine()
        }

        var body = bodyBuilder.toString()
        if (body.length > MAX_BODY_CHARACTERS) {
            body = body.take(MAX_BODY_CHARACTERS - TRUNCATION_NOTE.length) + TRUNCATION_NOTE
        }

        val (sanitizedBody, wasSanitized) = reportSanitizationService.sanitize(body)

        return HelpReportResponse(body = sanitizedBody, sanitized = wasSanitized)
    }
}
