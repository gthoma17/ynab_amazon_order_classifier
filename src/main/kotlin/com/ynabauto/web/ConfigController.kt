package com.ynabauto.web

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.ynabauto.domain.CategoryRule
import com.ynabauto.service.ConfigService
import com.ynabauto.web.dto.ApiKeysRequest
import com.ynabauto.web.dto.ApiKeysResponse
import com.ynabauto.web.dto.CategoryRuleRequest
import com.ynabauto.web.dto.CategoryRuleResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/config")
class ConfigController(
    private val configService: ConfigService
) {

    @GetMapping("/keys")
    fun getKeys(): ApiKeysResponse {
        return ApiKeysResponse(
            ynabToken = configService.getValue(ConfigService.YNAB_TOKEN),
            ynabBudgetId = configService.getValue(ConfigService.YNAB_BUDGET_ID),
            fastmailUser = configService.getValue(ConfigService.FASTMAIL_USER),
            fastmailToken = configService.getValue(ConfigService.FASTMAIL_TOKEN),
            geminiKey = configService.getValue(ConfigService.GEMINI_KEY)
        )
    }

    @PutMapping("/keys")
    fun updateKeys(@RequestBody request: ApiKeysRequest): ResponseEntity<Void> {
        request.ynabToken?.let { configService.setValue(ConfigService.YNAB_TOKEN, it) }
        request.ynabBudgetId?.let { configService.setValue(ConfigService.YNAB_BUDGET_ID, it) }
        request.fastmailUser?.let { configService.setValue(ConfigService.FASTMAIL_USER, it) }
        request.fastmailToken?.let { configService.setValue(ConfigService.FASTMAIL_TOKEN, it) }
        request.geminiKey?.let { configService.setValue(ConfigService.GEMINI_KEY, it) }
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/categories")
    fun getCategories(): List<CategoryRuleResponse> {
        return configService.getAllCategoryRules().map { it.toResponse() }
    }

    @PutMapping("/categories")
    fun updateCategories(@RequestBody rules: List<CategoryRuleRequest>): ResponseEntity<Void> {
        val entities = rules.map { req ->
            CategoryRule(
                ynabCategoryId = req.ynabCategoryId,
                ynabCategoryName = req.ynabCategoryName,
                userDescription = req.userDescription,
                updatedAt = Instant.now()
            )
        }
        configService.saveCategoryRules(entities)
        return ResponseEntity.noContent().build()
    }

    private fun CategoryRule.toResponse() = CategoryRuleResponse(
        id = id,
        ynabCategoryId = ynabCategoryId,
        ynabCategoryName = ynabCategoryName,
        userDescription = userDescription
    )
}
