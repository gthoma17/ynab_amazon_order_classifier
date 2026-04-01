package com.ynabauto.service

import com.ynabauto.domain.AmazonOrder
import com.ynabauto.infrastructure.ynab.YnabTransaction
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs

object MatchingStrategy {

    private val AMOUNT_TOLERANCE_MILLIUNITS = 10L
    private val DATE_WINDOW_DAYS = 3L

    /**
     * Attempts to match an Amazon order against a list of YNAB transactions.
     *
     * Matching criteria:
     * - Amount: abs(ynab.amount) within 10 milliunits of (amazonOrder.totalAmount * 1000)
     * - Date: ynab.date within ±3 days of amazonOrder.orderDate
     *
     * Returns the first matching transaction, or null if none found.
     */
    fun match(order: AmazonOrder, transactions: List<YnabTransaction>): YnabTransaction? {
        val orderDate = order.orderDate.atZone(ZoneOffset.UTC).toLocalDate()
        val expectedMilliunits = order.totalAmount.multiply(BigDecimal(1000)).toLong()

        return transactions.firstOrNull { txn ->
            val amountMatches = abs(abs(txn.amount) - expectedMilliunits) <= AMOUNT_TOLERANCE_MILLIUNITS
            val daysDiff = abs(txn.date.toEpochDay() - orderDate.toEpochDay())
            val dateMatches = daysDiff <= DATE_WINDOW_DAYS
            amountMatches && dateMatches
        }
    }
}
