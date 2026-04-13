package com.budgetsortbot.web

import com.budgetsortbot.infrastructure.persistence.SyncLogRepository
import com.budgetsortbot.service.ApplicationLogService
import com.budgetsortbot.service.ConfigService
import com.budgetsortbot.service.ReportSanitizationService
import com.budgetsortbot.web.dto.ConfigStateEntryDto
import com.budgetsortbot.web.dto.HelpReportRequest
import com.budgetsortbot.web.dto.HelpReportResponse
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URLDecoder

@RestController
@RequestMapping("/api/help")
class HelpController(
    private val syncLogRepository: SyncLogRepository,
    private val applicationLogService: ApplicationLogService,
    private val reportSanitizationService: ReportSanitizationService,
    private val configService: ConfigService,
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

        /**
         * Placeholder shown for every sensitive config key in the config-state panel.
         * The raw secret value is NEVER included in the API response (server-side redaction).
         * Add new sensitive keys to [ReportSanitizationService.NON_SENSITIVE_KEYS]'s inverse —
         * i.e. omit them from NON_SENSITIVE_KEYS — to have them automatically covered here.
         */
        const val SENSITIVE_PLACEHOLDER = "SENSITIVE VALUES REMOVED"

        /**
         * All known app_config keys, in display order for the config-state panel.
         * When a new key is added to [ConfigService], add it here too.
         */
        private val KNOWN_CONFIG_KEYS = listOf(
            ConfigService.YNAB_TOKEN,
            ConfigService.YNAB_BUDGET_ID,
            ConfigService.FASTMAIL_API_TOKEN,
            ConfigService.GEMINI_KEY,
            ConfigService.ORDER_CAP,
            ConfigService.SCHEDULE_CONFIG,
            ConfigService.START_FROM_DATE,
            ConfigService.INSTALLED_AT,
        )

        /**
         * Encodes [value] exactly as JavaScript's `encodeURIComponent` does:
         * every byte is percent-encoded except the unreserved characters
         * A-Z a-z 0-9 - _ . ! ~ * ' ( )
         */
        fun encodeURIComponent(value: String): String {
            val sb = StringBuilder(value.length * 2)
            for (byte in value.toByteArray(Charsets.UTF_8)) {
                val c = byte.toInt() and 0xFF
                if (c in 0x41..0x5A ||
                    c in 0x61..0x7A ||
                    c in 0x30..0x39 ||
                    c == 0x2D ||
                    c == 0x5F ||
                    c == 0x2E ||
                    // - _ .
                    c == 0x21 ||
                    c == 0x7E ||
                    c == 0x2A ||
                    c == 0x27 ||
                    c == 0x28 ||
                    c == 0x29 // ! ~ * ' ( )
                ) {
                    sb.append(c.toChar())
                } else {
                    sb.append("%%%02X".format(c))
                }
            }
            return sb.toString()
        }
    }

    /**
     * Returns the current app_config state with sensitive values replaced by
     * [SENSITIVE_PLACEHOLDER]. The raw secret is never transmitted over the wire.
     * Sensitive keys with no value set still return [SENSITIVE_PLACEHOLDER] to
     * avoid leaking whether a credential has been configured.
     */
    @GetMapping("/config-state")
    fun getConfigState(): List<ConfigStateEntryDto> =
        KNOWN_CONFIG_KEYS.map { key ->
            val isSensitive = key !in ReportSanitizationService.NON_SENSITIVE_KEYS
            ConfigStateEntryDto(
                key = key,
                displayValue =
                    if (isSensitive) {
                        SENSITIVE_PLACEHOLDER
                    } else {
                        configService.getValue(key) ?: ""
                    },
            )
        }

    @PostMapping("/report")
    fun createReport(
        @RequestBody request: HelpReportRequest,
    ): HelpReportResponse {
        val bodyBuilder = StringBuilder()

        bodyBuilder.appendLine("## Problem Description")
        bodyBuilder.appendLine()
        bodyBuilder.appendLine(request.description.trim())
        bodyBuilder.appendLine()

        if (request.includeSyncLogs) {
            bodyBuilder.appendLine("## Recent Sync Logs")
            bodyBuilder.appendLine()

            val logs =
                syncLogRepository
                    .findAll(
                        PageRequest.of(0, SYNC_LOG_LIMIT, Sort.by(Sort.Direction.DESC, "lastRun")),
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

        // URL-encode the sanitized body using the same algorithm as JS encodeURIComponent
        // so the length estimate here exactly matches what the frontend will produce.
        val encoded = encodeURIComponent(sanitizedBody)

        val truncated = encoded.length > MAX_ENCODED_BODY_LENGTH
        val finalBody =
            if (truncated) {
                // Trim encoded string to the allowed budget, stripping any partial %XX sequence
                val trimmed = encoded.take(MAX_ENCODED_BODY_LENGTH).replace(Regex("%[0-9A-Fa-f]{0,1}$"), "")
                URLDecoder.decode(trimmed, Charsets.UTF_8)
            } else {
                sanitizedBody
            }

        return HelpReportResponse(body = finalBody, sanitized = wasSanitized, truncated = truncated)
    }
}
