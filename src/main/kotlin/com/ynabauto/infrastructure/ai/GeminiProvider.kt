package com.ynabauto.infrastructure.ai

import com.ynabauto.domain.CategoryRule
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class GeminiProvider(restClientBuilder: RestClient.Builder) : ClassificationProvider {

    companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        const val MODEL = "gemini-1.5-flash"
    }

    private val client = restClientBuilder
        .baseUrl(BASE_URL)
        .build()

    override fun classify(items: List<String>, rules: List<CategoryRule>, apiKey: String): String {
        val prompt = buildPrompt(items, rules)
        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt))))
        )

        val response = client.post()
            .uri("/models/$MODEL:generateContent?key={key}", apiKey)
            .body(request)
            .retrieve()
            .body(GeminiResponse::class.java)

        return response?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
            ?: throw RuntimeException("No classification response from Gemini")
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
