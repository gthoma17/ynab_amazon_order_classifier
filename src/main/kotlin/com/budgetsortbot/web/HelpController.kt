package com.budgetsortbot.web

import com.budgetsortbot.infrastructure.persistence.SyncLogRepository
import com.budgetsortbot.service.ApplicationLogService
import com.budgetsortbot.service.ReportSanitizationService
import com.budgetsortbot.web.dto.HelpReportRequest
import com.budgetsortbot.web.dto.HelpReportResponse
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
    private val applicationLogService: ApplicationLogService,
    private val reportSanitizationService: ReportSanitizationService
) {

    companion object {
        // 3–4 most recent sync runs, ordered most-recent-first
        private const val SYNC_LOG_LIMIT = 4

        // GitHub new-issue URL body limit researched empirically: the total URL
        // accepted by github.com is ~8 192 bytes.  With the base URL (~65 chars)
        // and URL-encoding overhead for newlines/spaces (≈ 1.5× expansion for
        // typical log content), a raw body of 8 000 characters fits comfortably
        // within that budget for the vast majority of reports.
        private const val MAX_BODY_CHARACTERS = 8000

        private const val TRUNCATION_NOTE =
            "\n\n_[Log content truncated — use `docker logs` for full output]_"

        // Number of recent app-log lines to fetch before applying the budget cap
        private const val APP_LOG_FETCH_LIMIT = 100
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

        if (request.includeAppLogs) {
            bodyBuilder.appendLine("## Application Logs")
            bodyBuilder.appendLine()

            val appLogs = applicationLogService.getRecentLogs(APP_LOG_FETCH_LIMIT)

            if (appLogs == null) {
                bodyBuilder.appendLine("_Application logs unavailable._")
            } else if (appLogs.isEmpty()) {
                bodyBuilder.appendLine("_No application log entries found._")
            } else {
                bodyBuilder.appendLine("```")
                val remaining = (MAX_BODY_CHARACTERS - bodyBuilder.length - TRUNCATION_NOTE.length - 4)
                    .coerceAtLeast(0)
                val combined = appLogs.joinToString("\n")
                bodyBuilder.appendLine(combined.take(remaining))
                bodyBuilder.appendLine("```")
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
