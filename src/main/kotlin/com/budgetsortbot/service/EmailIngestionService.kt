package com.budgetsortbot.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.budgetsortbot.domain.AmazonOrder
import com.budgetsortbot.domain.OrderStatus
import com.budgetsortbot.domain.SyncLog
import com.budgetsortbot.domain.SyncSource
import com.budgetsortbot.domain.SyncStatus
import com.budgetsortbot.infrastructure.email.EmailOrder
import com.budgetsortbot.infrastructure.email.EmailProviderClient
import com.budgetsortbot.infrastructure.persistence.AmazonOrderRepository
import com.budgetsortbot.infrastructure.persistence.SyncLogRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
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
        private val AMOUNT_PATTERN = Regex("""(?:Order Total|Grand Total|Total|Subtotal)\s*:?\s*\$?\s*([\d,]+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
        private val ITEM_PATTERN = Regex("""^(?:\d+\s+of\s*:\s*|\*\s+)(.+)$""", RegexOption.MULTILINE)
        private val DEFAULT_LOOKBACK_DAYS = 30L
    }

    fun ingest() {
        val token = configService.getValue(ConfigService.FASTMAIL_API_TOKEN)
        if (token == null) {
            log.warn { "FastMail API token not configured, skipping email ingestion" }
            return
        }

        val startFromDate = configService.getValue(ConfigService.START_FROM_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val startFromInstant = startFromDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()

        var status = SyncStatus.SUCCESS
        var message: String? = null

        try {
            val lastSyncInstant = syncLogRepository
                .findTopBySourceOrderByLastRunDesc(SyncSource.EMAIL)
                ?.lastRun
                ?: Instant.now().minus(DEFAULT_LOOKBACK_DAYS, ChronoUnit.DAYS)

            // Use the later of (last sync time) and (start-from date) so we never
            // re-ingest emails that predate the user's chosen cutoff.
            val sinceDate = if (startFromInstant != null && startFromInstant.isAfter(lastSyncInstant)) {
                startFromInstant
            } else {
                lastSyncInstant
            }

            val emails = emailProviderClient.searchOrders(token, sinceDate)
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

