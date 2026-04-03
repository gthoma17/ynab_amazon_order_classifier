package com.budgetsortbot.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.budgetsortbot.domain.AmazonOrder
import com.budgetsortbot.domain.CategoryRule
import com.budgetsortbot.domain.DryRunResult
import com.budgetsortbot.domain.OrderStatus
import com.budgetsortbot.domain.SyncLog
import com.budgetsortbot.domain.SyncSource
import com.budgetsortbot.domain.SyncStatus
import com.budgetsortbot.infrastructure.email.EmailOrder
import com.budgetsortbot.infrastructure.email.EmailProviderClient
import com.budgetsortbot.infrastructure.persistence.AmazonOrderRepository
import com.budgetsortbot.infrastructure.persistence.DryRunResultRepository
import com.budgetsortbot.infrastructure.persistence.SyncLogRepository
import com.budgetsortbot.infrastructure.ynab.YnabClient
import com.budgetsortbot.infrastructure.ynab.YnabTransaction
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class DryRunServiceTest {

    private val emailProviderClient = mockk<EmailProviderClient>()
    private val amazonOrderRepository = mockk<AmazonOrderRepository>()
    private val dryRunResultRepository = mockk<DryRunResultRepository>()
    private val syncLogRepository = mockk<SyncLogRepository>()
    private val configService = mockk<ConfigService>()
    private val classificationService = mockk<ClassificationService>()
    private val ynabClient = mockk<YnabClient>()
    private val objectMapper = jacksonObjectMapper()

    private lateinit var dryRunService: DryRunService

    @BeforeEach
    fun setup() {
        dryRunService = DryRunService(
            emailProviderClient, amazonOrderRepository, dryRunResultRepository,
            syncLogRepository, configService, classificationService, ynabClient, objectMapper
        )
        // Common defaults
        every { configService.getValue(ConfigService.YNAB_TOKEN) } returns "ynab-token"
        every { configService.getValue(ConfigService.YNAB_BUDGET_ID) } returns "budget-1"
        every { configService.getValue(ConfigService.FASTMAIL_API_TOKEN) } returns null
        every { configService.getValue(ConfigService.ORDER_CAP) } returns null
        every { configService.getAllCategoryRules() } returns emptyList()
        justRun { dryRunResultRepository.deleteAll() }
        every { dryRunResultRepository.saveAll(any<List<DryRunResult>>()) } answers { firstArg() }
        every { syncLogRepository.save(any()) } answers { firstArg() }
    }

    private fun makeOrder(id: Long, amount: String, daysAgo: Long = 5) = AmazonOrder(
        id = id,
        emailMessageId = "msg-$id@amazon.com",
        orderDate = Instant.now().minusSeconds(daysAgo * 86400),
        totalAmount = BigDecimal(amount),
        itemsJson = """["Keyboard"]""",
        status = OrderStatus.PENDING,
        createdAt = Instant.now()
    )

    private fun makeTxn(id: String, amount: Long, date: LocalDate) =
        YnabTransaction(id = id, date = date, amount = amount, memo = null, categoryId = null, payeeName = "Amazon.com")

    @Test
    fun `runDryRun logs FAIL and returns when YNAB credentials not configured`() {
        every { configService.getValue(ConfigService.YNAB_TOKEN) } returns null
        every { configService.getValue(ConfigService.YNAB_BUDGET_ID) } returns null
        val logSlot = slot<SyncLog>()
        every { syncLogRepository.save(capture(logSlot)) } answers { firstArg() }

        dryRunService.runDryRun()

        assertEquals(SyncSource.DRY_RUN, logSlot.captured.source)
        assertEquals(SyncStatus.FAIL, logSlot.captured.status)
        verify { ynabClient wasNot io.mockk.Called }
    }

    @Test
    fun `runDryRun does not call YNAB write endpoint`() {
        every { amazonOrderRepository.findByStatus(OrderStatus.PENDING) } returns emptyList()
        val logSlot = slot<SyncLog>()
        every { syncLogRepository.save(capture(logSlot)) } answers { firstArg() }

        dryRunService.runDryRun()

        verify(exactly = 0) { ynabClient.updateTransaction(any(), any(), any(), any(), any()) }
        assertEquals(SyncSource.DRY_RUN, logSlot.captured.source)
    }

    @Test
    fun `runDryRun clears previous results before running`() {
        every { amazonOrderRepository.findByStatus(OrderStatus.PENDING) } returns emptyList()
        every { syncLogRepository.save(any()) } answers { firstArg() }

        dryRunService.runDryRun()

        verify { dryRunResultRepository.deleteAll() }
    }

    @Test
    fun `runDryRun calls Gemini classification for matched orders`() {
        val order = makeOrder(1L, "49.99")
        val txn = makeTxn("txn-1", -49990L, LocalDate.now().minusDays(5))
        every { amazonOrderRepository.findByStatus(OrderStatus.PENDING) } returns listOf(order)
        every { ynabClient.getTransactions(any(), any(), any()) } returns listOf(txn)
        every { classificationService.classify(order) } returns "cat-electronics"
        val savedResults = slot<List<DryRunResult>>()
        every { dryRunResultRepository.saveAll(capture(savedResults)) } answers { firstArg() }
        every { syncLogRepository.save(any()) } answers { firstArg() }

        dryRunService.runDryRun()

        verify { classificationService.classify(order) }
        assertEquals(1, savedResults.captured.size)
        assertEquals("cat-electronics", savedResults.captured[0].proposedCategoryId)
    }

    @Test
    fun `runDryRun records unmatched order with null transaction and category`() {
        val order = makeOrder(1L, "49.99")
        // Transaction amount does not match
        val txn = makeTxn("txn-1", -99990L, LocalDate.now().minusDays(5))
        every { amazonOrderRepository.findByStatus(OrderStatus.PENDING) } returns listOf(order)
        every { ynabClient.getTransactions(any(), any(), any()) } returns listOf(txn)
        val savedResults = slot<List<DryRunResult>>()
        every { dryRunResultRepository.saveAll(capture(savedResults)) } answers { firstArg() }
        every { syncLogRepository.save(any()) } answers { firstArg() }

        dryRunService.runDryRun()

        assertNull(savedResults.captured[0].ynabTransactionId)
        assertNull(savedResults.captured[0].proposedCategoryId)
    }

    @Test
    fun `runDryRun logs Gemini failure but continues processing`() {
        val order = makeOrder(1L, "49.99")
        val txn = makeTxn("txn-1", -49990L, LocalDate.now().minusDays(5))
        every { amazonOrderRepository.findByStatus(OrderStatus.PENDING) } returns listOf(order)
        every { ynabClient.getTransactions(any(), any(), any()) } returns listOf(txn)
        every { classificationService.classify(order) } throws RuntimeException("Gemini down")
        val savedResults = slot<List<DryRunResult>>()
        every { dryRunResultRepository.saveAll(capture(savedResults)) } answers { firstArg() }
        val logSlot = slot<SyncLog>()
        every { syncLogRepository.save(capture(logSlot)) } answers { firstArg() }

        dryRunService.runDryRun()

        // Result saved with error, but the overall run succeeds
        assertEquals("Gemini down", savedResults.captured[0].errorMessage)
        assertEquals(SyncStatus.SUCCESS, logSlot.captured.status)
    }

    @Test
    fun `runDryRun applies order cap`() {
        every { configService.getValue(ConfigService.ORDER_CAP) } returns "1"
        val orders = listOf(makeOrder(1L, "10.00"), makeOrder(2L, "20.00"), makeOrder(3L, "30.00"))
        every { amazonOrderRepository.findByStatus(OrderStatus.PENDING) } returns orders
        every { ynabClient.getTransactions(any(), any(), any()) } returns emptyList()
        val savedResults = slot<List<DryRunResult>>()
        every { dryRunResultRepository.saveAll(capture(savedResults)) } answers { firstArg() }
        every { syncLogRepository.save(any()) } answers { firstArg() }

        dryRunService.runDryRun()

        assertEquals(1, savedResults.captured.size)
    }

    @Test
    fun `runDryRun filters orders before dry-run start date`() {
        val recentOrder = makeOrder(1L, "49.99", daysAgo = 5)
        val oldOrder = makeOrder(2L, "29.99", daysAgo = 60)
        every { amazonOrderRepository.findByStatus(OrderStatus.PENDING) } returns listOf(recentOrder, oldOrder)
        every { ynabClient.getTransactions(any(), any(), any()) } returns emptyList()
        val savedResults = slot<List<DryRunResult>>()
        every { dryRunResultRepository.saveAll(capture(savedResults)) } answers { firstArg() }
        every { syncLogRepository.save(any()) } answers { firstArg() }

        // Start from 30 days ago — only recent order (5 days ago) should be included
        dryRunService.runDryRun(LocalDate.now().minusDays(30))

        assertEquals(1, savedResults.captured.size)
    }

    @Test
    fun `runDryRun uses category name from rules when classifying`() {
        val order = makeOrder(1L, "49.99")
        val txn = makeTxn("txn-1", -49990L, LocalDate.now().minusDays(5))
        val rule = CategoryRule(
            id = 1L, ynabCategoryId = "cat-tech", ynabCategoryName = "Technology",
            userDescription = "Electronics", updatedAt = Instant.now()
        )
        every { amazonOrderRepository.findByStatus(OrderStatus.PENDING) } returns listOf(order)
        every { ynabClient.getTransactions(any(), any(), any()) } returns listOf(txn)
        every { classificationService.classify(order) } returns "cat-tech"
        every { configService.getAllCategoryRules() } returns listOf(rule)
        val savedResults = slot<List<DryRunResult>>()
        every { dryRunResultRepository.saveAll(capture(savedResults)) } answers { firstArg() }
        every { syncLogRepository.save(any()) } answers { firstArg() }

        dryRunService.runDryRun()

        assertEquals("Technology", savedResults.captured[0].proposedCategoryName)
    }

    @Test
    fun `runDryRun records DRY_RUN source in sync log`() {
        every { amazonOrderRepository.findByStatus(OrderStatus.PENDING) } returns emptyList()
        val logSlot = slot<SyncLog>()
        every { syncLogRepository.save(capture(logSlot)) } answers { firstArg() }

        dryRunService.runDryRun()

        assertEquals(SyncSource.DRY_RUN, logSlot.captured.source)
    }

    @Test
    fun `getResults delegates to repository`() {
        val expected = listOf(
            DryRunResult(
                id = 1L, orderId = 10L,
                orderDate = Instant.now(), totalAmount = BigDecimal("49.99"),
                itemsJson = """["Keyboard"]""", runAt = Instant.now()
            )
        )
        every { dryRunResultRepository.findAllByOrderByRunAtDesc() } returns expected

        val result = dryRunService.getResults()

        assertEquals(expected, result)
    }
}
