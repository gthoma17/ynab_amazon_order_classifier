package com.ynabauto.infrastructure.ai

import com.ynabauto.domain.CategoryRule

interface ClassificationProvider {
    fun classify(items: List<String>, rules: List<CategoryRule>, apiKey: String): String
}
