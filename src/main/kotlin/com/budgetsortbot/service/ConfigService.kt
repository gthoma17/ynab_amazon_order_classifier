package com.budgetsortbot.service

import com.budgetsortbot.domain.AppConfig
import com.budgetsortbot.domain.CategoryRule
import com.budgetsortbot.infrastructure.persistence.AppConfigRepository
import com.budgetsortbot.infrastructure.persistence.CategoryRuleRepository
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class ConfigService(
    private val appConfigRepository: AppConfigRepository,
    private val categoryRuleRepository: CategoryRuleRepository
) {

    companion object {
        const val YNAB_TOKEN = "YNAB_TOKEN"
        const val YNAB_BUDGET_ID = "YNAB_BUDGET_ID"
        const val FASTMAIL_API_TOKEN = "FASTMAIL_API_TOKEN"
        const val GEMINI_KEY = "GEMINI_KEY"
        const val ORDER_CAP = "ORDER_CAP"
        const val SCHEDULE_CONFIG = "SCHEDULE_CONFIG"
        const val START_FROM_DATE = "START_FROM_DATE"
        const val INSTALLED_AT = "INSTALLED_AT"
    }

    fun getValue(key: String): String? =
        appConfigRepository.findById(key).map { it.value }.orElse(null)

    fun setValue(key: String, value: String) {
        appConfigRepository.save(AppConfig(key = key, value = value, updatedAt = Instant.now()))
    }

    fun getAllCategoryRules(): List<CategoryRule> = categoryRuleRepository.findAll()

    fun saveCategoryRules(rules: List<CategoryRule>) {
        categoryRuleRepository.saveAll(rules)
    }
}
