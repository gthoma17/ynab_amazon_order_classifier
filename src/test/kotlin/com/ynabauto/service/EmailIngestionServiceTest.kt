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
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class EmailIngestionServiceTest {

    private val emailProviderClient = mockk<EmailProviderClient>()
    private val amazonOrderRepository = mockk<AmazonOrderRepository>()
    private val syncLogRepository = mockk<SyncLogRepository>()
    private val configService = mockk<ConfigService>()
    private lateinit var emailIngestionService: EmailIngestionService

    @BeforeEach
    fun setup() {
        emailIngestionService = EmailIngestionService(
            emailProviderClient, amazonOrderRepository, syncLogRepository, configService, jacksonObjectMapper()
        )
        // Default: no start-from date
        every { configService.getValue(ConfigService.START_FROM_DATE) } returns null
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

    @Test
    fun `parseOrderBody handles Grand Total on next line with currency suffix`() {
        val body = """
            * TOTO® WASHLET® C2 Electronic Bidet Toilet Seat
              Quantity: 1
              426 USD

            Grand Total:
            426.00 USD
        """.trimIndent()
        val email = EmailOrder(messageId = "msg-6", receivedAt = Instant.now(), bodyText = body)

        val result = emailIngestionService.parseOrderBody(email)

        assertNotNull(result)
        assertEquals(BigDecimal("426.00"), result!!.totalAmount)
    }

    @Test
    fun `parseOrderBody extracts items from bullet-point format`() {
        val body = """
            * TOTO® WASHLET® C2 Electronic Bidet Toilet Seat
              Quantity: 1
              426 USD

            Grand Total:
            426.00 USD
        """.trimIndent()
        val email = EmailOrder(messageId = "msg-7", receivedAt = Instant.now(), bodyText = body)

        val result = emailIngestionService.parseOrderBody(email)

        assertNotNull(result)
        assertEquals(listOf("TOTO® WASHLET® C2 Electronic Bidet Toilet Seat"), result!!.items)
        assertEquals(BigDecimal("426.00"), result.totalAmount)
    }

    @Test
    fun `parseOrderBody handles real Amazon email format with multiple items`() {
        val body = """
            Thanks for your order, Greg!

            * USB Cable
              Quantity: 2
              19.99 USD

            * Phone Case
              Quantity: 1
              14.99 USD

            Grand Total:
            54.97 USD
        """.trimIndent()
        val email = EmailOrder(messageId = "msg-8", receivedAt = Instant.now(), bodyText = body)

        val result = emailIngestionService.parseOrderBody(email)

        assertNotNull(result)
        assertEquals(BigDecimal("54.97"), result!!.totalAmount)
        assertEquals(listOf("USB Cable", "Phone Case"), result.items)
    }

    // --- ingest ---

    @Test
    fun `ingest skips when FastMail credentials are not configured`() {
        every { configService.getValue(ConfigService.FASTMAIL_USER) } returns null
        every { configService.getValue(ConfigService.FASTMAIL_TOKEN) } returns null

        emailIngestionService.ingest()

        verify { emailProviderClient wasNot io.mockk.Called }
        verify { syncLogRepository wasNot io.mockk.Called }
    }

    @Test
    fun `ingest saves SUCCESS sync log after processing emails`() {
        every { configService.getValue(ConfigService.FASTMAIL_USER) } returns "user@fastmail.com"
        every { configService.getValue(ConfigService.FASTMAIL_TOKEN) } returns "my-token"
        every { syncLogRepository.findTopBySourceOrderByLastRunDesc(SyncSource.EMAIL) } returns null
        every { emailProviderClient.searchOrders(any(), any(), any()) } returns emptyList()
        val syncLogSlot = slot<SyncLog>()
        every { syncLogRepository.save(capture(syncLogSlot)) } answers { firstArg() }

        emailIngestionService.ingest()

        assertEquals(SyncSource.EMAIL, syncLogSlot.captured.source)
        assertEquals(SyncStatus.SUCCESS, syncLogSlot.captured.status)
        assertNull(syncLogSlot.captured.message)
    }

    @Test
    fun `ingest saves FAIL sync log when email provider throws`() {
        every { configService.getValue(ConfigService.FASTMAIL_USER) } returns "user@fastmail.com"
        every { configService.getValue(ConfigService.FASTMAIL_TOKEN) } returns "my-token"
        every { syncLogRepository.findTopBySourceOrderByLastRunDesc(SyncSource.EMAIL) } returns null
        every { emailProviderClient.searchOrders(any(), any(), any()) } throws RuntimeException("Connection failed")
        val syncLogSlot = slot<SyncLog>()
        every { syncLogRepository.save(capture(syncLogSlot)) } answers { firstArg() }

        emailIngestionService.ingest()

        assertEquals(SyncStatus.FAIL, syncLogSlot.captured.status)
        assertEquals("Connection failed", syncLogSlot.captured.message)
    }

    @Test
    fun `ingest uses last successful sync time as since date`() {
        val lastRun = Instant.parse("2024-01-10T00:00:00Z")
        val lastLog = SyncLog(id = 1L, source = SyncSource.EMAIL, lastRun = lastRun, status = SyncStatus.SUCCESS)
        every { configService.getValue(ConfigService.FASTMAIL_USER) } returns "user@fastmail.com"
        every { configService.getValue(ConfigService.FASTMAIL_TOKEN) } returns "my-token"
        every { syncLogRepository.findTopBySourceOrderByLastRunDesc(SyncSource.EMAIL) } returns lastLog
        val sinceDateSlot = slot<Instant>()
        every { emailProviderClient.searchOrders(any(), any(), capture(sinceDateSlot)) } returns emptyList()
        every { syncLogRepository.save(any()) } answers { firstArg() }

        emailIngestionService.ingest()

        assertEquals(lastRun, sinceDateSlot.captured)
    }

    @Test
    fun `ingest uses start-from date when it is later than last sync time`() {
        val lastRun = Instant.parse("2024-01-10T00:00:00Z")
        val lastLog = SyncLog(id = 1L, source = SyncSource.EMAIL, lastRun = lastRun, status = SyncStatus.SUCCESS)
        every { configService.getValue(ConfigService.FASTMAIL_USER) } returns "user@fastmail.com"
        every { configService.getValue(ConfigService.FASTMAIL_TOKEN) } returns "my-token"
        every { configService.getValue(ConfigService.START_FROM_DATE) } returns "2024-02-01"
        every { syncLogRepository.findTopBySourceOrderByLastRunDesc(SyncSource.EMAIL) } returns lastLog
        val sinceDateSlot = slot<Instant>()
        every { emailProviderClient.searchOrders(any(), any(), capture(sinceDateSlot)) } returns emptyList()
        every { syncLogRepository.save(any()) } answers { firstArg() }

        emailIngestionService.ingest()

        // Feb 1 is after Jan 10, so start-from date wins
        val expected = LocalDate.parse("2024-02-01")
            .atStartOfDay(ZoneOffset.UTC).toInstant()
        assertEquals(expected, sinceDateSlot.captured)
    }

    @Test
    fun `ingest uses last sync time when it is later than start-from date`() {
        val lastRun = Instant.parse("2024-03-15T00:00:00Z")
        val lastLog = SyncLog(id = 1L, source = SyncSource.EMAIL, lastRun = lastRun, status = SyncStatus.SUCCESS)
        every { configService.getValue(ConfigService.FASTMAIL_USER) } returns "user@fastmail.com"
        every { configService.getValue(ConfigService.FASTMAIL_TOKEN) } returns "my-token"
        every { configService.getValue(ConfigService.START_FROM_DATE) } returns "2024-02-01"
        every { syncLogRepository.findTopBySourceOrderByLastRunDesc(SyncSource.EMAIL) } returns lastLog
        val sinceDateSlot = slot<Instant>()
        every { emailProviderClient.searchOrders(any(), any(), capture(sinceDateSlot)) } returns emptyList()
        every { syncLogRepository.save(any()) } answers { firstArg() }

        emailIngestionService.ingest()

        // March 15 is after Feb 1, so last sync time wins
        assertEquals(lastRun, sinceDateSlot.captured)
    }

    @Test
    fun `processEmail skips duplicate messageId`() {
        val existing = AmazonOrder(
            id = 1L, emailMessageId = "msg-dup@amazon.com",
            orderDate = Instant.now(), totalAmount = BigDecimal("10.00"),
            itemsJson = """["Item"]""", status = OrderStatus.PENDING, createdAt = Instant.now()
        )
        every { amazonOrderRepository.findAll() } returns listOf(existing)

        emailIngestionService.processEmail(
            EmailOrder(messageId = "msg-dup@amazon.com", receivedAt = Instant.now(), bodyText = "Order Total: \$10.00")
        )

        verify(exactly = 0) { amazonOrderRepository.save(any()) }
    }

    @Test
    fun `processEmail saves order when body is parseable and messageId is new`() {
        every { amazonOrderRepository.findAll() } returns emptyList()
        val email = EmailOrder(
            messageId = "msg-new@amazon.com",
            receivedAt = Instant.parse("2024-01-15T10:00:00Z"),
            bodyText = "1 of: Keyboard\nOrder Total: \$79.99"
        )
        val savedSlot = slot<AmazonOrder>()
        every { amazonOrderRepository.save(capture(savedSlot)) } answers { firstArg() }

        emailIngestionService.processEmail(email)

        val saved = savedSlot.captured
        assertEquals("msg-new@amazon.com", saved.emailMessageId)
        assertEquals(BigDecimal("79.99"), saved.totalAmount)
        assertEquals(OrderStatus.PENDING, saved.status)
    }

    @Test
    fun `processEmail skips when body cannot be parsed`() {
        every { amazonOrderRepository.findAll() } returns emptyList()
        val email = EmailOrder(
            messageId = "msg-unparseable@amazon.com",
            receivedAt = Instant.now(),
            bodyText = "This is not an order confirmation email."
        )

        emailIngestionService.processEmail(email)

        verify(exactly = 0) { amazonOrderRepository.save(any()) }
    }
}
