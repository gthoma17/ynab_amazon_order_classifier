package com.ynabauto.web

import com.ynabauto.infrastructure.ynab.YnabClient
import com.ynabauto.service.ConfigService
import com.ynabauto.web.dto.YnabCategoryResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/ynab")
class YnabController(
    private val ynabClient: YnabClient,
    private val configService: ConfigService
) {

    @GetMapping("/categories")
    fun getCategories(): List<YnabCategoryResponse> {
        val token = configService.getValue(ConfigService.YNAB_TOKEN)
            ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "YNAB token not configured")
        val budgetId = configService.getValue(ConfigService.YNAB_BUDGET_ID)
            ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "YNAB budget ID not configured")
        return ynabClient.getCategories(budgetId, token).map {
            YnabCategoryResponse(id = it.id, name = it.name, categoryGroupName = it.categoryGroupName)
        }
    }
}
