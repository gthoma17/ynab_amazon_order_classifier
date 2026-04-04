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
import java.net.URLDecoder
import java.net.URLEncoder

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

        // Number of recent app-log lines to fetch before applying the budget cap
        private const val APP_LOG_FETCH_LIMIT = 100

        // Full GitHub new-issue base URL including the query param key
        private const val GITHUB_BASE_URL =
            "https://github.com/gthoma17/budget-sortbot/issues/new?body="

        // Maximum total GitHub URL length accepted by github.com (empirical)
        private const val MAX_GITHUB_URL_LENGTH = 8192

        // Budget reserved for the truncation note the frontend appends when truncated=true.
        // encodeURIComponent("\n\n_[Log content truncated — use `docker logs` for full output]_")
        // is ≈ 130 characters; 200 gives extra headroom.
        private const val TRUNCATION_NOTE_BUDGET = 200

        // Maximum encoded body length the frontend is allowed to append to GITHUB_BASE_URL
        // before the truncation note pushes the total over the limit.
        private val MAX_ENCODED_BODY_LENGTH =
            MAX_GITHUB_URL_LENGTH - GITHUB_BASE_URL.length - TRUNCATION_NOTE_BUDGET
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
                bodyBuilder.appendLine(appLogs.joinToString("\n"))
                bodyBuilder.appendLine("```")
            }
            bodyBuilder.appendLine()
        }

        val (sanitizedBody, wasSanitized) = reportSanitizationService.sanitize(bodyBuilder.toString())

        // URL-encode the sanitized body (matching JS encodeURIComponent: spaces → %20, not +)
        val encoded = URLEncoder.encode(sanitizedBody, Charsets.UTF_8).replace("+", "%20")

        val truncated = encoded.length > MAX_ENCODED_BODY_LENGTH
        val finalBody = if (truncated) {
            // Trim encoded string to the allowed budget, stripping any partial %XX sequence
            val trimmed = encoded.take(MAX_ENCODED_BODY_LENGTH).replace(Regex("%[0-9A-Fa-f]{0,1}$"), "")
            URLDecoder.decode(trimmed, Charsets.UTF_8)
        } else {
            sanitizedBody
        }

        return HelpReportResponse(body = finalBody, sanitized = wasSanitized, truncated = truncated)
    }
}

