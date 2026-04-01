package com.ynabauto.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ynabauto.domain.AmazonOrder
import com.ynabauto.domain.OrderStatus
import com.ynabauto.domain.SyncLog
import com.ynabauto.domain.SyncSource
import com.ynabauto.domain.SyncStatus
import com.ynabauto.infrastructure.email.EmailOrder
import com.ynabauto.infrastructure.email.EmailProviderClient
import com.ynabauto.infrastructure.persistence.AmazonOrderRepository
import com.ynabauto.infrastructure.persistence.SyncLogRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.math.BigDecimal
import java.time.Instant

/** Returns a non-null placeholder so Kotlin's null-safety doesn't interfere with Mockito matchers. */
private fun anyInstant(): Instant = Mockito.any(Instant::class.java) ?: Instant.EPOCH

@ExtendWith(MockitoExtension::class)
class EmailIngestionServiceTest {

    @Mock
    private lateinit var emailProviderClient: EmailProviderClient

    @Mock
    private lateinit var amazonOrderRepository: AmazonOrderRepository

    @Mock
    private lateinit var syncLogRepository: SyncLogRepository

    @Mock
    private lateinit var configService: ConfigService

    private lateinit var emailIngestionService: EmailIngestionService

    @BeforeEach
    fun setup() {
        emailIngestionService = EmailIngestionService(
            emailProviderClient, amazonOrderRepository, syncLogRepository, configService, jacksonObjectMapper()
        )
    }

    // --- parseOrderBody ---

    @Test
    fun `parseOrderBody extracts total amount and items from typical body`() {
        val body = """
            Your order has been placed.
            1 of: USB Cable
            1 of: Phone Case
            Order Total: ${'$'}26.98
        """.trimIndent()
        val email = EmailOrder(messageId = "msg-1", receivedAt = Instant.now(), bodyText = body)

        val result = emailIngestionService.parseOrderBody(email)

        assertNotNull(result)
        assertEquals(BigDecimal("26.98"), result!!.totalAmount)
        assertEquals(listOf("USB Cable", "Phone Case"), result.items)
    }

    @Test
    fun `parseOrderBody returns null when no amount pattern is found`() {
        val body = "No amount here, just some text about an order."
        val email = EmailOrder(messageId = "msg-2", receivedAt = Instant.now(), bodyText = body)

        val result = emailIngestionService.parseOrderBody(email)

        assertNull(result)
    }

    @Test
    fun `parseOrderBody falls back to Amazon Order item when no item pattern matches`() {
        val body = "Order Total: \$49.99"
        val email = EmailOrder(messageId = "msg-3", receivedAt = Instant.now(), bodyText = body)

        val result = emailIngestionService.parseOrderBody(email)

        assertNotNull(result)
        assertEquals(BigDecimal("49.99"), result!!.totalAmount)
        assertEquals(listOf("Amazon Order"), result.items)
    }

    @Test
    fun `parseOrderBody handles Grand Total pattern`() {
        val body = """
            1 of: Keyboard
            Grand Total: ${'$'}79.99
        """.trimIndent()
        val email = EmailOrder(messageId = "msg-4", receivedAt = Instant.now(), bodyText = body)

        val result = emailIngestionService.parseOrderBody(email)

        assertNotNull(result)
        assertEquals(BigDecimal("79.99"), result!!.totalAmount)
    }

    @Test
    fun `parseOrderBody handles amounts with commas`() {
        val body = "Order Total: \$1,234.56"
        val email = EmailOrder(messageId = "msg-5", receivedAt = Instant.now(), bodyText = body)

        val result = emailIngestionService.parseOrderBody(email)

        assertNotNull(result)
        assertEquals(BigDecimal("1234.56"), result!!.totalAmount)
    }

    // --- ingest ---

    @Test
    fun `ingest skips when FastMail credentials are not configured`() {
        `when`(configService.getValue(ConfigService.FASTMAIL_USER)).thenReturn(null)

        emailIngestionService.ingest()

        verifyNoInteractions(emailProviderClient)
        verifyNoInteractions(syncLogRepository)
    }

    @Test
    fun `ingest saves SUCCESS sync log after processing emails`() {
        `when`(configService.getValue(ConfigService.FASTMAIL_USER)).thenReturn("user@fastmail.com")
        `when`(configService.getValue(ConfigService.FASTMAIL_TOKEN)).thenReturn("my-token")
        `when`(syncLogRepository.findTopBySourceOrderByLastRunDesc(SyncSource.EMAIL)).thenReturn(null)
        Mockito.doReturn(emptyList<EmailOrder>()).`when`(emailProviderClient).searchOrders(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            anyInstant()
        )

        emailIngestionService.ingest()

        val captor = ArgumentCaptor.forClass(SyncLog::class.java)
        verify(syncLogRepository).save(captor.capture())
        assertEquals(SyncSource.EMAIL, captor.value.source)
        assertEquals(SyncStatus.SUCCESS, captor.value.status)
        assertNull(captor.value.message)
    }

    @Test
    fun `ingest saves FAIL sync log when email provider throws`() {
        `when`(configService.getValue(ConfigService.FASTMAIL_USER)).thenReturn("user@fastmail.com")
        `when`(configService.getValue(ConfigService.FASTMAIL_TOKEN)).thenReturn("my-token")
        `when`(syncLogRepository.findTopBySourceOrderByLastRunDesc(SyncSource.EMAIL)).thenReturn(null)
        Mockito.doThrow(RuntimeException("Connection failed")).`when`(emailProviderClient).searchOrders(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            anyInstant()
        )

        emailIngestionService.ingest()

        val captor = ArgumentCaptor.forClass(SyncLog::class.java)
        verify(syncLogRepository).save(captor.capture())
        assertEquals(SyncStatus.FAIL, captor.value.status)
        assertEquals("Connection failed", captor.value.message)
    }

    @Test
    fun `ingest uses last successful sync time as since date`() {
        val lastRun = Instant.parse("2024-01-10T00:00:00Z")
        val lastLog = SyncLog(id = 1L, source = SyncSource.EMAIL, lastRun = lastRun, status = SyncStatus.SUCCESS)
        `when`(configService.getValue(ConfigService.FASTMAIL_USER)).thenReturn("user@fastmail.com")
        `when`(configService.getValue(ConfigService.FASTMAIL_TOKEN)).thenReturn("my-token")
        `when`(syncLogRepository.findTopBySourceOrderByLastRunDesc(SyncSource.EMAIL)).thenReturn(lastLog)
        var capturedSinceDate: Instant? = null
        Mockito.doAnswer { inv ->
            capturedSinceDate = inv.getArgument(2)
            emptyList<EmailOrder>()
        }.`when`(emailProviderClient).searchOrders(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            anyInstant()
        )

        emailIngestionService.ingest()

        assertEquals(lastRun, capturedSinceDate)
    }

    @Test
    fun `processEmail skips duplicate messageId`() {
        val existing = AmazonOrder(
            id = 1L, emailMessageId = "msg-dup@amazon.com",
            orderDate = Instant.now(), totalAmount = BigDecimal("10.00"),
            itemsJson = """["Item"]""", status = OrderStatus.PENDING, createdAt = Instant.now()
        )
        `when`(amazonOrderRepository.findAll()).thenReturn(listOf(existing))

        emailIngestionService.processEmail(
            EmailOrder(messageId = "msg-dup@amazon.com", receivedAt = Instant.now(), bodyText = "Order Total: \$10.00")
        )

        verify(amazonOrderRepository, never()).save(org.mockito.ArgumentMatchers.any(AmazonOrder::class.java))
    }

    @Test
    fun `processEmail saves order when body is parseable and messageId is new`() {
        `when`(amazonOrderRepository.findAll()).thenReturn(emptyList())
        val email = EmailOrder(
            messageId = "msg-new@amazon.com",
            receivedAt = Instant.parse("2024-01-15T10:00:00Z"),
            bodyText = "1 of: Keyboard\nOrder Total: \$79.99"
        )

        emailIngestionService.processEmail(email)

        val captor = ArgumentCaptor.forClass(AmazonOrder::class.java)
        verify(amazonOrderRepository).save(captor.capture())
        val saved = captor.value
        assertEquals("msg-new@amazon.com", saved.emailMessageId)
        assertEquals(BigDecimal("79.99"), saved.totalAmount)
        assertEquals(OrderStatus.PENDING, saved.status)
    }

    @Test
    fun `processEmail skips when body cannot be parsed`() {
        `when`(amazonOrderRepository.findAll()).thenReturn(emptyList())
        val email = EmailOrder(
            messageId = "msg-unparseable@amazon.com",
            receivedAt = Instant.now(),
            bodyText = "This is not an order confirmation email."
        )

        emailIngestionService.processEmail(email)

        verify(amazonOrderRepository, never()).save(org.mockito.ArgumentMatchers.any(AmazonOrder::class.java))
    }
}


