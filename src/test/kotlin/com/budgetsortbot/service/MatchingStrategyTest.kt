package com.budgetsortbot.service

import com.budgetsortbot.domain.AmazonOrder
import com.budgetsortbot.domain.OrderStatus
import com.budgetsortbot.infrastructure.ynab.YnabTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class MatchingStrategyTest {

    private fun makeOrder(amount: String, orderDate: Instant = Instant.parse("2024-01-15T10:00:00Z")) = AmazonOrder(
        id = 1L,
        emailMessageId = "msg@amazon.com",
        orderDate = orderDate,
        totalAmount = BigDecimal(amount),
        itemsJson = """["Item"]""",
        status = OrderStatus.PENDING,
        createdAt = Instant.now()
    )

    private fun makeTxn(id: String, amount: Long, date: LocalDate) = YnabTransaction(
        id = id,
        date = date,
        amount = amount,
        memo = null,
        categoryId = null,
        payeeName = "Amazon.com"
    )

    @Test
    fun `match returns transaction when amount and date match exactly`() {
        val order = makeOrder("49.99")
        val txn = makeTxn("txn-1", -49990L, LocalDate.of(2024, 1, 15))

        val result = MatchingStrategy.match(order, listOf(txn))

        assertEquals("txn-1", result?.id)
    }

    @Test
    fun `match returns null when amount does not match`() {
        val order = makeOrder("49.99")
        val txn = makeTxn("txn-1", -29990L, LocalDate.of(2024, 1, 15))

        val result = MatchingStrategy.match(order, listOf(txn))

        assertNull(result)
    }

    @Test
    fun `match returns null when date is outside window`() {
        val order = makeOrder("49.99")
        val txn = makeTxn("txn-1", -49990L, LocalDate.of(2024, 1, 20))

        val result = MatchingStrategy.match(order, listOf(txn))

        assertNull(result)
    }

    @Test
    fun `match returns transaction when date is within 3-day window`() {
        val order = makeOrder("49.99", Instant.parse("2024-01-15T00:00:00Z"))
        val txn = makeTxn("txn-1", -49990L, LocalDate.of(2024, 1, 18))

        val result = MatchingStrategy.match(order, listOf(txn))

        assertEquals("txn-1", result?.id)
    }

    @Test
    fun `match handles YNAB milliunits amount with tolerance`() {
        val order = makeOrder("49.99")
        // YNAB $49.99 = 49990 milliunits; allow up to 10 milliunits tolerance
        val txn = makeTxn("txn-1", -49995L, LocalDate.of(2024, 1, 15))

        val result = MatchingStrategy.match(order, listOf(txn))

        assertEquals("txn-1", result?.id)
    }

    @Test
    fun `match returns first matching transaction from multiple candidates`() {
        val order = makeOrder("25.00")
        val txn1 = makeTxn("txn-no-match", -99990L, LocalDate.of(2024, 1, 15))
        val txn2 = makeTxn("txn-match", -25000L, LocalDate.of(2024, 1, 15))

        val result = MatchingStrategy.match(order, listOf(txn1, txn2))

        assertEquals("txn-match", result?.id)
    }

    @Test
    fun `match returns null when transactions list is empty`() {
        val order = makeOrder("49.99")

        val result = MatchingStrategy.match(order, emptyList())

        assertNull(result)
    }
}
