package com.ynabauto.infrastructure.ynab

import com.fasterxml.jackson.annotation.JsonProperty
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.LocalDate

@Component
class YnabRestClient(restClientBuilder: RestClient.Builder) : YnabClient {

    companion object {
        const val BASE_URL = "https://api.ynab.com/v1"
        private val log = KotlinLogging.logger {}
    }

    private val client = restClientBuilder
        .baseUrl(BASE_URL)
        .build()

    override fun getTransactions(budgetId: String, token: String, sinceDate: LocalDate?): List<YnabTransaction> {
        val response = client.get()
            .uri { builder ->
                val uriBuilder = builder.path("/budgets/{budgetId}/transactions")
                if (sinceDate != null) uriBuilder.queryParam("since_date", sinceDate)
                uriBuilder.build(budgetId)
            }
            .header("Authorization", "Bearer $token")
            .retrieve()
            .body(YnabTransactionsResponse::class.java)
        val transactions = response?.data?.transactions?.map { it.toYnabTransaction() } ?: emptyList()
        log.debug { "getTransactions budgetId=$budgetId sinceDate=$sinceDate returned ${transactions.size} transactions" }
        return transactions
    }

    override fun getCategories(budgetId: String, token: String): List<YnabCategory> {
        val response = client.get()
            .uri("/budgets/{budgetId}/categories", budgetId)
            .header("Authorization", "Bearer $token")
            .retrieve()
            .body(YnabCategoriesResponse::class.java)
        val categories = response?.data?.categoryGroups
            ?.filter { !it.deleted }
            ?.flatMap { group ->
                group.categories
                    .filter { !it.deleted }
                    .map { YnabCategory(it.id, it.name, group.name) }
            }
            ?: emptyList()
        log.debug { "getCategories budgetId=$budgetId returned ${categories.size} categories" }
        return categories
    }

    override fun updateTransaction(budgetId: String, transactionId: String, token: String, memo: String, categoryId: String) {
        log.debug { "updateTransaction budgetId=$budgetId transactionId=$transactionId categoryId=$categoryId" }
        client.put()
            .uri("/budgets/{budgetId}/transactions/{transactionId}", budgetId, transactionId)
            .header("Authorization", "Bearer $token")
            .body(YnabUpdateRequest(YnabTransactionPatch(memo = memo, categoryId = categoryId)))
            .retrieve()
            .toBodilessEntity()
    }
}

private fun YnabTransactionDto.toYnabTransaction() = YnabTransaction(
    id = id,
    date = LocalDate.parse(date),
    amount = amount,
    memo = memo,
    categoryId = categoryId,
    payeeName = payeeName
)

private data class YnabTransactionsResponse(val data: YnabTransactionsData)

private data class YnabTransactionsData(val transactions: List<YnabTransactionDto>)

private data class YnabTransactionDto(
    val id: String,
    val date: String,
    val amount: Long,
    val memo: String?,
    @JsonProperty("category_id") val categoryId: String?,
    @JsonProperty("payee_name") val payeeName: String?
)

private data class YnabCategoriesResponse(val data: YnabCategoryGroupsData)

private data class YnabCategoryGroupsData(
    @JsonProperty("category_groups") val categoryGroups: List<YnabCategoryGroupDto>
)

private data class YnabCategoryGroupDto(
    val id: String,
    val name: String,
    val deleted: Boolean,
    val categories: List<YnabCategoryDto>
)

private data class YnabCategoryDto(val id: String, val name: String, val deleted: Boolean)

private data class YnabUpdateRequest(val transaction: YnabTransactionPatch)

private data class YnabTransactionPatch(
    val memo: String,
    @JsonProperty("category_id") val categoryId: String
)
