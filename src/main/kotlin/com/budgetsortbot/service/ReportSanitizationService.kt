package com.budgetsortbot.service

import com.budgetsortbot.infrastructure.persistence.AppConfigRepository
import org.springframework.stereotype.Service

@Service
class ReportSanitizationService(
    private val appConfigRepository: AppConfigRepository
) {

    /**
     * Replaces any occurrence of a stored app_config value in [text] with [REDACTED].
     * Returns the sanitized string and a flag indicating whether any replacement was made.
     * Blank config values are ignored to avoid spurious replacements.
     */
    fun sanitize(text: String): Pair<String, Boolean> {
        val sensitiveValues = appConfigRepository.findAll()
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
