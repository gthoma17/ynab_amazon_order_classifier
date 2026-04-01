package com.ynabauto.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.ynabauto.domain.AmazonOrder
import com.ynabauto.domain.OrderStatus
import com.ynabauto.domain.SyncLog
import com.ynabauto.domain.SyncSource
import com.ynabauto.domain.SyncStatus
import com.ynabauto.infrastructure.email.EmailOrder
import com.ynabauto.infrastructure.email.EmailProviderClient
import com.ynabauto.infrastructure.persistence.AmazonOrderRepository
import com.ynabauto.infrastructure.persistence.SyncLogRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class EmailIngestionService(
    private val emailProviderClient: EmailProviderClient,
    private val amazonOrderRepository: AmazonOrderRepository,
    private val syncLogRepository: SyncLogRepository,
    private val configService: ConfigService,
    private val objectMapper: ObjectMapper
) {

    companion object {
        private val log = KotlinLogging.logger {}
        private val AMOUNT_PATTERN = Regex("""(?:Order Total|Grand Total|Total|Subtotal)\s*:?\s*\$?([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE)
        private val ITEM_PATTERN = Regex("""^\d+\s+of\s*:\s*(.+)$""", RegexOption.MULTILINE)
        private val DEFAULT_LOOKBACK_DAYS = 30L
    }

    @Scheduled(fixedDelayString = "\${app.email.poll-interval-ms:300000}")
    fun ingest() {
        val user = configService.getValue(ConfigService.FASTMAIL_USER)
        val token = configService.getValue(ConfigService.FASTMAIL_TOKEN)
        if (user == null || token == null) {
            log.warn { "FastMail credentials not configured, skipping email ingestion" }
            return
        }

        var status = SyncStatus.SUCCESS
        var message: String? = null

        try {
            val sinceDate = syncLogRepository
                .findTopBySourceOrderByLastRunDesc(SyncSource.EMAIL)
                ?.lastRun
                ?: Instant.now().minus(DEFAULT_LOOKBACK_DAYS, ChronoUnit.DAYS)

            val emails = emailProviderClient.searchOrders(user, token, sinceDate)
            log.info { "Email ingestion: found ${emails.size} order email(s) since $sinceDate" }

            for (email in emails) {
                processEmail(email)
            }
        } catch (e: Exception) {
            log.error(e) { "Email ingestion failed" }
            status = SyncStatus.FAIL
            message = e.message
        }

        syncLogRepository.save(
            SyncLog(source = SyncSource.EMAIL, lastRun = Instant.now(), status = status, message = message)
        )
    }

    internal fun processEmail(email: EmailOrder) {
        if (amazonOrderRepository.findAll().any { it.emailMessageId == email.messageId }) {
            log.debug { "Skipping duplicate email messageId=${email.messageId}" }
            return
        }

        val parsed = parseOrderBody(email) ?: run {
            log.warn { "Failed to parse order body for messageId=${email.messageId}, skipping" }
            return
        }

        val order = AmazonOrder(
            emailMessageId = email.messageId,
            orderDate = email.receivedAt,
            totalAmount = parsed.totalAmount,
            itemsJson = objectMapper.writeValueAsString(parsed.items),
            status = OrderStatus.PENDING,
            createdAt = Instant.now()
        )
        amazonOrderRepository.save(order)
        log.info { "Saved new order from email messageId=${email.messageId}, amount=${parsed.totalAmount}" }
    }

    internal fun parseOrderBody(email: EmailOrder): ParsedOrder? {
        val amountMatch = AMOUNT_PATTERN.find(email.bodyText) ?: return null
        val amountStr = amountMatch.groupValues[1].replace(",", "")
        val totalAmount = amountStr.toBigDecimalOrNull() ?: return null

        val items = ITEM_PATTERN.findAll(email.bodyText)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() }
            .toList()
            .ifEmpty { listOf("Amazon Order") }

        return ParsedOrder(totalAmount = totalAmount, items = items)
    }
}

data class ParsedOrder(val totalAmount: BigDecimal, val items: List<String>)
