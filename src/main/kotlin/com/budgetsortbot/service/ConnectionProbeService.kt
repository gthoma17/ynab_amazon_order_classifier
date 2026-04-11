package com.budgetsortbot.service

import com.budgetsortbot.web.dto.ProbeResult
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient

/**
 * Provides lightweight read-only connection probes for each external integration.
 *
 * Each probe accepts an optional credential override. When an override is supplied
 * (non-blank), it is used directly. Otherwise the credential is read from
 * [ConfigService] (the persisted store). This allows the UI to test unsaved
 * field values without requiring a save-first workflow.
 *
 * Probes never modify remote data:
 *  - FastMail: GET /.well-known/jmap
 *  - YNAB:     GET /budgets
 *  - Gemini:   GET /models (lists available models — no content generated)
 */
@Service
class ConnectionProbeService(
    restClientBuilder: RestClient.Builder,
    private val configService: ConfigService,
    @Value("\${app.fastmail.base-url:https://api.fastmail.com}") private val fastmailBaseUrl: String = "https://api.fastmail.com",
    @Value("\${app.ynab.base-url:https://api.ynab.com/v1}") private val ynabBaseUrl: String = "https://api.ynab.com/v1",
    @Value(
        "\${app.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}",
    ) private val geminiBaseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
) {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    private val client = restClientBuilder.build()

    fun probeFastMail(tokenOverride: String? = null): ProbeResult {
        val token = tokenOverride?.takeIf { it.isNotBlank() }
            ?: configService.getValue(ConfigService.FASTMAIL_API_TOKEN)
        if (token.isNullOrBlank()) {
            return ProbeResult(success = false, message = "FastMail API token not configured")
        }
        log.debug { "Probing FastMail" }
        return probe {
            client
                .get()
                .uri("$fastmailBaseUrl/.well-known/jmap")
                .header("Authorization", "Bearer $token")
                .retrieve()
                .toBodilessEntity()
        }
    }

    fun probeYnab(tokenOverride: String? = null): ProbeResult {
        val token = tokenOverride?.takeIf { it.isNotBlank() }
            ?: configService.getValue(ConfigService.YNAB_TOKEN)
        if (token.isNullOrBlank()) {
            return ProbeResult(success = false, message = "YNAB credentials not configured")
        }
        log.debug { "Probing YNAB" }
        return probe {
            client
                .get()
                .uri("$ynabBaseUrl/budgets")
                .header("Authorization", "Bearer $token")
                .retrieve()
                .toBodilessEntity()
        }
    }

    fun probeGemini(keyOverride: String? = null): ProbeResult {
        val key = keyOverride?.takeIf { it.isNotBlank() }
            ?: configService.getValue(ConfigService.GEMINI_KEY)
        if (key.isNullOrBlank()) {
            return ProbeResult(success = false, message = "Gemini API key not configured")
        }
        log.debug { "Probing Gemini" }
        return probe {
            client
                .get()
                .uri("$geminiBaseUrl/models?key={key}", key)
                .retrieve()
                .toBodilessEntity()
        }
    }

    private fun probe(action: () -> Unit): ProbeResult =
        try {
            action()
            ProbeResult(success = true, message = "Connected")
        } catch (e: HttpClientErrorException.Unauthorized) {
            log.debug { "Probe returned 401 Unauthorized" }
            ProbeResult(success = false, message = "401 Unauthorized — check your credentials")
        } catch (e: ResourceAccessException) {
            log.debug { "Probe network error: ${e.message}" }
            ProbeResult(success = false, message = "Connection timed out — service may be temporarily unavailable")
        } catch (e: HttpClientErrorException) {
            log.debug { "Probe HTTP client error: ${e.statusCode.value()} ${e.statusText}" }
            ProbeResult(success = false, message = "${e.statusCode.value()} ${e.statusText}")
        } catch (e: HttpServerErrorException) {
            log.debug { "Probe server error: ${e.statusCode.value()} ${e.statusText}" }
            ProbeResult(success = false, message = "Service error: ${e.statusCode.value()} ${e.statusText}")
        } catch (e: Exception) {
            log.debug { "Probe unexpected error: ${e.message}" }
            ProbeResult(success = false, message = "Connection failed: ${e.message ?: "unknown error"}")
        }
}
