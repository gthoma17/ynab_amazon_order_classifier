package com.budgetsortbot.infrastructure.ai

import com.budgetsortbot.domain.CategoryRule

interface ClassificationProvider {
    fun classify(items: List<String>, rules: List<CategoryRule>, apiKey: String): String
}
