package com.ynabauto.service

import com.ynabauto.domain.AmazonOrder
import com.ynabauto.domain.OrderStatus
import com.ynabauto.domain.SyncLog
import com.ynabauto.domain.SyncSource
import com.ynabauto.domain.SyncStatus
import com.ynabauto.infrastructure.persistence.AmazonOrderRepository
import com.ynabauto.infrastructure.persistence.SyncLogRepository
import com.ynabauto.infrastructure.ynab.YnabClient
import com.ynabauto.infrastructure.ynab.YnabTransaction
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class YnabSyncServiceTest {

    private val ynabClient = mockk<YnabClient>()
    private val amazonOrderRepository = mockk<AmazonOrderRepository>()
    private val syncLogRepository = mockk<SyncLogRepository>()
    private val configService = mockk<ConfigService>()
    private val classificationService = mockk<ClassificationService>()
    private lateinit var ynabSyncService: YnabSyncService

    @BeforeEach
    fun setup() {
        ynabSyncService = YnabSyncService(
            ynabClient, amazonOrderRepository, syncLogRepository, configService, classificationService
        )
    }

    private fun makeOrder(id: Long, amount: String, status: OrderStatus = OrderStatus.PENDING) = AmazonOrder(
        id = id,
        emailMessageId = "msg-$id@amazon.com",
        orderDate = Instant.parse("2024-01-15T10:00:00Z"),
        totalAmount = BigDecimal(amount),
        itemsJson = """["Keyboard"]""",
        status = status,
        createdAt = Instant.now()
    )

    private fun makeTxn(id: String, amount: Long, date: LocalDate) = YnabTransaction(
        id = id, date = date, amount = amount, memo = null, categoryId = null, payeeName = "Amazon.com"
    )

    // --- sync ---

    @Test
    fun `sync skips when YNAB credentials are not configured`() {
        every { configService.getValue(ConfigService.YNAB_TOKEN) } returns null
        every { configService.getValue(ConfigService.YNAB_BUDGET_ID) } returns null

        ynabSyncService.sync()

        verify { ynabClient wasNot io.mockk.Called }
        verify { syncLogRepository wasNot io.mockk.Called }
    }

    @Test
    fun `sync skips when YNAB budget ID is not configured`() {
        every { configService.getValue(ConfigService.YNAB_TOKEN) } returns "ynab-token"
        every { configService.getValue(ConfigService.YNAB_BUDGET_ID) } returns null

        ynabSyncService.sync()

        verify { ynabClient wasNot io.mockk.Called }
        verify { syncLogRepository wasNot io.mockk.Called }
    }

    @Test
    fun `sync saves SUCCESS log when no pending orders`() {
        every { configService.getValue(ConfigService.YNAB_TOKEN) } returns "ynab-token"
        every { configService.getValue(ConfigService.YNAB_BUDGET_ID) } returns "budget-1"
        every { amazonOrderRepository.findByStatus(OrderStatus.PENDING) } returns emptyList()
        val syncLogSlot = slot<SyncLog>()
        every { syncLogRepository.save(capture(syncLogSlot)) } answers { firstArg() }

        ynabSyncService.sync()

        assertEquals(SyncSource.YNAB, syncLogSlot.captured.source)
        assertEquals(SyncStatus.SUCCESS, syncLogSlot.captured.status)
        assertNull(syncLogSlot.captured.message)
    }

    @Test
    fun `sync saves FAIL log when YNAB client throws`() {
        every { configService.getValue(ConfigService.YNAB_TOKEN) } returns "ynab-token"
        every { configService.getValue(ConfigService.YNAB_BUDGET_ID) } returns "budget-1"
        every { amazonOrderRepository.findByStatus(OrderStatus.PENDING) } returns listOf(makeOrder(1L, "49.99"))
        every { ynabClient.getTransactions(any(), any(), any()) } throws RuntimeException("YNAB API error")
        val syncLogSlot = slot<SyncLog>()
        every { syncLogRepository.save(capture(syncLogSlot)) } answers { firstArg() }

        ynabSyncService.sync()

        assertEquals(SyncStatus.FAIL, syncLogSlot.captured.status)
        assertEquals("YNAB API error", syncLogSlot.captured.message)
    }

    // --- processOrder ---

    @Test
    fun `processOrder skips when no matching YNAB transaction found`() {
        val order = makeOrder(1L, "49.99")
        val txns = listOf(makeTxn("txn-1", -99990L, LocalDate.of(2024, 1, 15)))

        ynabSyncService.processOrder(order, txns, "budget-1", "token")

        verify { amazonOrderRepository wasNot io.mockk.Called }
    }

    @Test
    fun `processOrder marks order MATCHED and then COMPLETED when match found`() {
        val order = makeOrder(1L, "49.99")
        val txn = makeTxn("txn-match", -49990L, LocalDate.of(2024, 1, 15))
        val matchedOrder = order.copy(status = OrderStatus.MATCHED, ynabTransactionId = "txn-match")
        val captures = mutableListOf<AmazonOrder>()
        every { amazonOrderRepository.save(capture(captures)) } answers { firstArg() }
        every { classificationService.classify(matchedOrder) } returns "cat-electronics"
        justRun { ynabClient.updateTransaction(any(), any(), any(), any(), any()) }

        ynabSyncService.processOrder(order, listOf(txn), "budget-1", "token")

        assertEquals(OrderStatus.MATCHED, captures[0].status)
        assertEquals("txn-match", captures[0].ynabTransactionId)
        assertEquals(OrderStatus.COMPLETED, captures[1].status)
        assertEquals("cat-electronics", captures[1].ynabCategoryId)
    }

    @Test
    fun `processOrder calls ynabClient updateTransaction after classification`() {
        val order = makeOrder(1L, "49.99")
        val txn = makeTxn("txn-update", -49990L, LocalDate.of(2024, 1, 15))
        val matchedOrder = order.copy(status = OrderStatus.MATCHED, ynabTransactionId = "txn-update")
        every { amazonOrderRepository.save(any()) } answers { firstArg() }
        every { classificationService.classify(matchedOrder) } returns "cat-food"
        justRun { ynabClient.updateTransaction(any(), any(), any(), any(), any()) }

        ynabSyncService.processOrder(order, listOf(txn), "budget-1", "token")

        verify { ynabClient.updateTransaction("budget-1", "txn-update", "token", "Amazon order (id=1)", "cat-food") }
    }

    @Test
    fun `processOrder does not mark order COMPLETED when classification fails`() {
        val order = makeOrder(1L, "49.99")
        val txn = makeTxn("txn-1", -49990L, LocalDate.of(2024, 1, 15))
        val matchedOrder = order.copy(status = OrderStatus.MATCHED, ynabTransactionId = "txn-1")
        val savedSlot = slot<AmazonOrder>()
        every { amazonOrderRepository.save(capture(savedSlot)) } answers { firstArg() }
        every { classificationService.classify(matchedOrder) } throws RuntimeException("Gemini error")

        ynabSyncService.processOrder(order, listOf(txn), "budget-1", "token")

        assertEquals(OrderStatus.MATCHED, savedSlot.captured.status)
    }
}
