package com.ynabauto.infrastructure.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.ynabauto.domain.CategoryRule
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class GeminiProvider(restClientBuilder: RestClient.Builder, private val objectMapper: ObjectMapper) : ClassificationProvider {

    companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        const val MODEL = "gemini-2.5-flash-lite"
        private val log = KotlinLogging.logger {}
    }

    private val client = restClientBuilder
        .baseUrl(BASE_URL)
        .build()

    override fun classify(items: List<String>, rules: List<CategoryRule>, apiKey: String): String {
        val prompt = buildPrompt(items, rules)
        log.debug { "Gemini prompt: $prompt" }
        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt))))
        )

        val rawBody = client.post()
            .uri("/models/$MODEL:generateContent?key={key}", apiKey)
            .body(request)
            .retrieve()
            .body(String::class.java)

        log.debug { "Gemini response: $rawBody" }

        val response = rawBody?.let { objectMapper.readValue(it, GeminiResponse::class.java) }
        val result = response?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
            ?: throw RuntimeException("No classification response from Gemini")
        log.debug { "Gemini classified items=$items → categoryId=$result" }
        return result
    }

    private fun buildPrompt(items: List<String>, rules: List<CategoryRule>): String {
        return buildString {
            appendLine("You are a YNAB budget assistant. Given the following Amazon order items, select the best matching YNAB budget category.")
            appendLine()
            appendLine("Order items: ${items.joinToString(", ")}")
            appendLine()
            appendLine("Available categories:")
            rules.forEach { rule ->
                appendLine("- ID: ${rule.ynabCategoryId} | Name: ${rule.ynabCategoryName} | Description: ${rule.userDescription}")
            }
            appendLine()
            append("Respond with only the category ID of the best match, nothing else.")
        }
    }
}

private data class GeminiRequest(val contents: List<GeminiContent>)

private data class GeminiContent(val parts: List<GeminiPart>)

private data class GeminiPart(val text: String)

private data class GeminiResponse(val candidates: List<GeminiCandidate>)

private data class GeminiCandidate(val content: GeminiContent)
