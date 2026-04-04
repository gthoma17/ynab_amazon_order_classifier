package com.budgetsortbot.service

import com.budgetsortbot.domain.AppConfig
import com.budgetsortbot.domain.CategoryRule
import com.budgetsortbot.infrastructure.persistence.AppConfigRepository
import com.budgetsortbot.infrastructure.persistence.CategoryRuleRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.time.Instant

private val log = KotlinLogging.logger {}

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
        log.info { "Saved config: key=$key value=$value" }
    }

    fun getAllCategoryRules(): List<CategoryRule> = categoryRuleRepository.findAll()

    fun saveCategoryRules(rules: List<CategoryRule>) {
        categoryRuleRepository.saveAll(rules)
    }
}
