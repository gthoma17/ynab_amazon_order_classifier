package com.budgetsortbot.infrastructure.ynab

import java.time.LocalDate

interface YnabClient {
    fun getTransactions(budgetId: String, token: String, sinceDate: LocalDate? = null): List<YnabTransaction>
    fun getCategories(budgetId: String, token: String): List<YnabCategory>
    fun getBudgets(token: String): List<YnabBudget>
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

data class YnabBudget(val id: String, val name: String)

data class YnabCategory(
    val id: String,
    val name: String,
    val categoryGroupName: String
)
