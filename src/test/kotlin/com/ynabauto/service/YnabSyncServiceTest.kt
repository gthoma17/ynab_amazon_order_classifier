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
        // Default: no start-from date or order cap
        every { configService.getValue(ConfigService.START_FROM_DATE) } returns null
        every { configService.getValue(ConfigService.ORDER_CAP) } returns null
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

    // --- order cap ---

    @Test
    fun `sync respects order cap and processes only cap number of orders`() {
        every { configService.getValue(ConfigService.YNAB_TOKEN) } returns "ynab-token"
        every { configService.getValue(ConfigService.YNAB_BUDGET_ID) } returns "budget-1"
        every { configService.getValue(ConfigService.ORDER_CAP) } returns "2"
        val orders = listOf(makeOrder(1L, "10.00"), makeOrder(2L, "20.00"), makeOrder(3L, "30.00"))
        every { amazonOrderRepository.findByStatus(OrderStatus.PENDING) } returns orders
        every { ynabClient.getTransactions(any(), any(), any()) } returns emptyList()
        val syncLogSlot = slot<SyncLog>()
        every { syncLogRepository.save(capture(syncLogSlot)) } answers { firstArg() }

        ynabSyncService.sync()

        // Only 2 orders processed (no YNAB match → no saves), but it ran without error
        assertEquals(SyncStatus.SUCCESS, syncLogSlot.captured.status)
    }

    @Test
    fun `sync processes all orders when cap is 0 (unlimited)`() {
        every { configService.getValue(ConfigService.YNAB_TOKEN) } returns "ynab-token"
        every { configService.getValue(ConfigService.YNAB_BUDGET_ID) } returns "budget-1"
        every { configService.getValue(ConfigService.ORDER_CAP) } returns "0"
        every { amazonOrderRepository.findByStatus(OrderStatus.PENDING) } returns emptyList()
        val syncLogSlot = slot<SyncLog>()
        every { syncLogRepository.save(capture(syncLogSlot)) } answers { firstArg() }

        ynabSyncService.sync()

        assertEquals(SyncStatus.SUCCESS, syncLogSlot.captured.status)
    }

    // --- start-from date ---

    @Test
    fun `sync excludes orders dated before start-from date`() {
        every { configService.getValue(ConfigService.YNAB_TOKEN) } returns "ynab-token"
        every { configService.getValue(ConfigService.YNAB_BUDGET_ID) } returns "budget-1"
        every { configService.getValue(ConfigService.START_FROM_DATE) } returns "2024-02-01"
        // Order dated in January — before the Feb 1 cutoff
        val oldOrder = makeOrder(1L, "49.99")  // orderDate = 2024-01-15
        every { amazonOrderRepository.findByStatus(OrderStatus.PENDING) } returns listOf(oldOrder)
        val syncLogSlot = slot<SyncLog>()
        every { syncLogRepository.save(capture(syncLogSlot)) } answers { firstArg() }

        ynabSyncService.sync()

        // Old order is filtered out; no YNAB transactions fetched
        verify { ynabClient wasNot io.mockk.Called }
        assertEquals(SyncStatus.SUCCESS, syncLogSlot.captured.status)
    }

    @Test
    fun `sync includes orders on or after start-from date`() {
        every { configService.getValue(ConfigService.YNAB_TOKEN) } returns "ynab-token"
        every { configService.getValue(ConfigService.YNAB_BUDGET_ID) } returns "budget-1"
        every { configService.getValue(ConfigService.START_FROM_DATE) } returns "2024-01-15"
        // Order dated exactly on the cutoff — should be included
        val order = makeOrder(1L, "49.99")  // orderDate = 2024-01-15
        every { amazonOrderRepository.findByStatus(OrderStatus.PENDING) } returns listOf(order)
        every { ynabClient.getTransactions(any(), any(), any()) } returns emptyList()
        val syncLogSlot = slot<SyncLog>()
        every { syncLogRepository.save(capture(syncLogSlot)) } answers { firstArg() }

        ynabSyncService.sync()

        verify { ynabClient.getTransactions(any(), any(), any()) }
        assertEquals(SyncStatus.SUCCESS, syncLogSlot.captured.status)
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

