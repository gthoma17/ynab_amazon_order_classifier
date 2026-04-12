package com.budgetsortbot.service

import com.budgetsortbot.infrastructure.persistence.AppConfigRepository
import org.springframework.stereotype.Service

@Service
class ReportSanitizationService(
    private val appConfigRepository: AppConfigRepository,
) {
    companion object {
        /**
         * Config keys whose values are NOT sensitive and should be left as-is in issue reports.
         * Everything else (including any future keys) is redacted by default — safer than
         * maintaining an opt-in allowlist of sensitive keys.
         */
        val NON_SENSITIVE_KEYS: Set<String> =
            setOf(
                ConfigService.YNAB_BUDGET_ID,
                ConfigService.ORDER_CAP,
                ConfigService.SCHEDULE_CONFIG,
                ConfigService.START_FROM_DATE,
                ConfigService.INSTALLED_AT,
            )
    }

    /**
     * Replaces any occurrence of a sensitive app_config value in [text] with [REDACTED].
     * All config keys are considered sensitive except those in [NON_SENSITIVE_KEYS], so any
     * newly added key is redacted by default.
     * Returns the sanitized string and a flag indicating whether any replacement was made.
     * Blank config values are ignored to avoid spurious replacements.
     */
    fun sanitize(text: String): Pair<String, Boolean> {
        val sensitiveValues =
            appConfigRepository
                .findAll()
                .filter { it.key !in NON_SENSITIVE_KEYS }
                .map { it.value }
                .filter { it.isNotBlank() }
                .sortedByDescending { it.length }

        if (sensitiveValues.isEmpty()) return text to false

        var result = text
        var wasSanitized = false
        for (value in sensitiveValues) {
            if (result.contains(value)) {
                result = result.replace(value, "[REDACTED]")
                wasSanitized = true
            }
        }
        return result to wasSanitized
    }
}
