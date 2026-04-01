package com.ynabauto.infrastructure.ynab

import java.time.LocalDate

interface YnabClient {
    fun getTransactions(budgetId: String, token: String, sinceDate: LocalDate? = null): List<YnabTransaction>
    fun getCategories(budgetId: String, token: String): List<YnabCategory>
    fun updateTransaction(budgetId: String, transactionId: String, token: String, memo: String, categoryId: String)
}

data class YnabTransaction(
    val id: String,
    val date: LocalDate,
    val amount: Long,
    val memo: String?,
    val categoryId: String?,
    val payeeName: String?
)

data class YnabCategory(
    val id: String,
    val name: String,
    val categoryGroupName: String
)
