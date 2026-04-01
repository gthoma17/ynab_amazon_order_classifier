package com.ynabauto.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.ynabauto.domain.AmazonOrder
import com.ynabauto.infrastructure.ai.ClassificationProvider
import com.ynabauto.infrastructure.persistence.CategoryRuleRepository
import org.springframework.stereotype.Service

@Service
class ClassificationService(
    private val classificationProvider: ClassificationProvider,
    private val categoryRuleRepository: CategoryRuleRepository,
    private val configService: ConfigService,
    private val objectMapper: ObjectMapper
) {

    fun classify(order: AmazonOrder): String {
        val apiKey = configService.getValue(ConfigService.GEMINI_KEY)
            ?: throw RuntimeException("Gemini API key not configured")
        val rules = categoryRuleRepository.findAll()
        if (rules.isEmpty()) throw RuntimeException("No category rules configured")
        val items: List<String> = objectMapper.readValue(order.itemsJson, object : TypeReference<List<String>>() {})
        return classificationProvider.classify(items, rules, apiKey)
    }
}
