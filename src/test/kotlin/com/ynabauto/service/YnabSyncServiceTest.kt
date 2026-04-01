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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/** Returns a non-null placeholder so Kotlin's null-safety doesn't interfere with Mockito matchers. */
private fun anyLocalDate(): LocalDate = Mockito.any(LocalDate::class.java) ?: LocalDate.EPOCH

@ExtendWith(MockitoExtension::class)
class YnabSyncServiceTest {

    @Mock
    private lateinit var ynabClient: YnabClient

    @Mock
    private lateinit var amazonOrderRepository: AmazonOrderRepository

    @Mock
    private lateinit var syncLogRepository: SyncLogRepository

    @Mock
    private lateinit var configService: ConfigService

    @Mock
    private lateinit var classificationService: ClassificationService

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
        `when`(configService.getValue(ConfigService.YNAB_TOKEN)).thenReturn(null)

        ynabSyncService.sync()

        verifyNoInteractions(ynabClient)
        verifyNoInteractions(syncLogRepository)
    }

    @Test
    fun `sync saves SUCCESS log when no pending orders`() {
        `when`(configService.getValue(ConfigService.YNAB_TOKEN)).thenReturn("ynab-token")
        `when`(configService.getValue(ConfigService.YNAB_BUDGET_ID)).thenReturn("budget-1")
        `when`(amazonOrderRepository.findByStatus(OrderStatus.PENDING)).thenReturn(emptyList())

        ynabSyncService.sync()

        val captor = ArgumentCaptor.forClass(SyncLog::class.java)
        verify(syncLogRepository).save(captor.capture())
        assertEquals(SyncSource.YNAB, captor.value.source)
        assertEquals(SyncStatus.SUCCESS, captor.value.status)
        assertNull(captor.value.message)
    }

    @Test
    fun `sync saves FAIL log when YNAB client throws`() {
        `when`(configService.getValue(ConfigService.YNAB_TOKEN)).thenReturn("ynab-token")
        `when`(configService.getValue(ConfigService.YNAB_BUDGET_ID)).thenReturn("budget-1")
        `when`(amazonOrderRepository.findByStatus(OrderStatus.PENDING)).thenReturn(listOf(makeOrder(1L, "49.99")))
        Mockito.doThrow(RuntimeException("YNAB API error")).`when`(ynabClient).getTransactions(anyString(), anyString(), anyLocalDate())

        ynabSyncService.sync()

        val captor = ArgumentCaptor.forClass(SyncLog::class.java)
        verify(syncLogRepository).save(captor.capture())
        assertEquals(SyncStatus.FAIL, captor.value.status)
        assertEquals("YNAB API error", captor.value.message)
    }

    // --- processOrder ---

    @Test
    fun `processOrder skips when no matching YNAB transaction found`() {
        val order = makeOrder(1L, "49.99")
        val txns = listOf(makeTxn("txn-1", -99990L, LocalDate.of(2024, 1, 15)))

        ynabSyncService.processOrder(order, txns, "budget-1", "token")

        verifyNoInteractions(amazonOrderRepository)
    }

    @Test
    fun `processOrder marks order MATCHED and then COMPLETED when match found`() {
        val order = makeOrder(1L, "49.99")
        val txn = makeTxn("txn-match", -49990L, LocalDate.of(2024, 1, 15))
        val matchedOrder = order.copy(status = OrderStatus.MATCHED, ynabTransactionId = "txn-match")

        `when`(amazonOrderRepository.save(matchedOrder)).thenReturn(matchedOrder)
        `when`(classificationService.classify(matchedOrder)).thenReturn("cat-electronics")

        ynabSyncService.processOrder(order, listOf(txn), "budget-1", "token")

        val captor = ArgumentCaptor.forClass(AmazonOrder::class.java)
        verify(amazonOrderRepository, atLeastOnce()).save(captor.capture())
        val allSaved = captor.allValues
        assertEquals(OrderStatus.MATCHED, allSaved[0].status)
        assertEquals("txn-match", allSaved[0].ynabTransactionId)
        assertEquals(OrderStatus.COMPLETED, allSaved[1].status)
        assertEquals("cat-electronics", allSaved[1].ynabCategoryId)
    }

    @Test
    fun `processOrder calls ynabClient updateTransaction after classification`() {
        val order = makeOrder(1L, "49.99")
        val txn = makeTxn("txn-update", -49990L, LocalDate.of(2024, 1, 15))
        val matchedOrder = order.copy(status = OrderStatus.MATCHED, ynabTransactionId = "txn-update")

        `when`(amazonOrderRepository.save(matchedOrder)).thenReturn(matchedOrder)
        `when`(classificationService.classify(matchedOrder)).thenReturn("cat-food")

        ynabSyncService.processOrder(order, listOf(txn), "budget-1", "token")

        verify(ynabClient).updateTransaction(
            "budget-1", "txn-update", "token", "Amazon order (id=1)", "cat-food"
        )
    }

    @Test
    fun `processOrder does not mark order COMPLETED when classification fails`() {
        val order = makeOrder(1L, "49.99")
        val txn = makeTxn("txn-1", -49990L, LocalDate.of(2024, 1, 15))
        val matchedOrder = order.copy(status = OrderStatus.MATCHED, ynabTransactionId = "txn-1")

        `when`(amazonOrderRepository.save(matchedOrder)).thenReturn(matchedOrder)
        `when`(classificationService.classify(matchedOrder)).thenThrow(RuntimeException("Gemini error"))

        ynabSyncService.processOrder(order, listOf(txn), "budget-1", "token")

        val captor = ArgumentCaptor.forClass(AmazonOrder::class.java)
        verify(amazonOrderRepository).save(captor.capture())
        assertEquals(OrderStatus.MATCHED, captor.value.status)
    }
}

