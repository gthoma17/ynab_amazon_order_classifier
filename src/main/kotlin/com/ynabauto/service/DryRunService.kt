package com.ynabauto.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.ynabauto.domain.AmazonOrder
import com.ynabauto.domain.DryRunResult
import com.ynabauto.domain.OrderStatus
import com.ynabauto.domain.SyncLog
import com.ynabauto.domain.SyncSource
import com.ynabauto.domain.SyncStatus
import com.ynabauto.infrastructure.email.EmailOrder
import com.ynabauto.infrastructure.email.EmailProviderClient
import com.ynabauto.infrastructure.persistence.AmazonOrderRepository
import com.ynabauto.infrastructure.persistence.DryRunResultRepository
import com.ynabauto.infrastructure.persistence.SyncLogRepository
import com.ynabauto.infrastructure.ynab.YnabClient
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * Executes the full sync pipeline (email ingest → YNAB match → Gemini classify)
 * without making any writes to YNAB. Results are persisted to [DryRunResult]
 * rows so the UI can display them after the run completes.
 *
 * A distinct [SyncSource.DRY_RUN] entry is written to sync_logs so dry runs
 * are clearly distinguishable from live runs in the audit log.
 */
@Service
class DryRunService(
    private val emailProviderClient: EmailProviderClient,
    private val amazonOrderRepository: AmazonOrderRepository,
    private val dryRunResultRepository: DryRunResultRepository,
    private val syncLogRepository: SyncLogRepository,
    private val configService: ConfigService,
    private val classificationService: ClassificationService,
    private val ynabClient: YnabClient,
    private val objectMapper: ObjectMapper
) {

    companion object {
        private val log = KotlinLogging.logger {}
        private val AMOUNT_PATTERN = Regex(
            """(?:Order Total|Grand Total|Total|Subtotal)\s*:?\s*\$?\s*([\d,]+(?:\.\d+)?)""",
            RegexOption.IGNORE_CASE
        )
        private val ITEM_PATTERN = Regex("""^(?:\d+\s+of\s*:\s*|\*\s+)(.+)$""", RegexOption.MULTILINE)
    }

    fun getResults(): List<DryRunResult> = dryRunResultRepository.findAllByOrderByRunAtDesc()

    /**
     * Runs the full pipeline for preview purposes.
     *
     * @param dryRunStartFromDate  Lower-bound on order/email date (defaults to 1 month ago when null).
     */
    fun runDryRun(dryRunStartFromDate: LocalDate? = null) {
        val effectiveStartDate = dryRunStartFromDate ?: LocalDate.now().minusMonths(1)
        val sinceInstant = effectiveStartDate.atStartOfDay(ZoneOffset.UTC).toInstant()
        val runAt = Instant.now()

        val token = configService.getValue(ConfigService.YNAB_TOKEN)
        val budgetId = configService.getValue(ConfigService.YNAB_BUDGET_ID)
        if (token == null || budgetId == null) {
            log.warn { "YNAB credentials not configured, aborting dry run" }
            syncLogRepository.save(
                SyncLog(
                    source = SyncSource.DRY_RUN,
                    lastRun = runAt,
                    status = SyncStatus.FAIL,
                    message = "YNAB credentials not configured"
                )
            )
            return
        }

        val orderCap = configService.getValue(ConfigService.ORDER_CAP)
            ?.toIntOrNull()
            ?.takeIf { it > 0 }

        // Replace any previous dry-run results so the UI never shows stale data.
        dryRunResultRepository.deleteAll()

        var overallStatus = SyncStatus.SUCCESS
        var overallMessage: String? = null

        try {
            // Step 1: gather orders — existing PENDING orders + freshly fetched emails
            val orders = gatherOrders(sinceInstant, orderCap)

            if (orders.isEmpty()) {
                log.info { "Dry run: no eligible orders found since $effectiveStartDate" }
                syncLogRepository.save(
                    SyncLog(
                        source = SyncSource.DRY_RUN,
                        lastRun = runAt,
                        status = SyncStatus.SUCCESS,
                        message = "Dry run: 0 orders processed"
                    )
                )
                return
            }

            // Step 2: fetch YNAB transactions for matching (read-only)
            val sinceDate = orders.minOf { it.orderDate }
                .atZone(ZoneOffset.UTC).toLocalDate().minusDays(1)
            val transactions = ynabClient.getTransactions(budgetId, token, sinceDate)
            log.debug { "Dry run: fetched ${transactions.size} YNAB transaction(s) since $sinceDate" }

            // Step 3: match + classify (no writes to YNAB or amazon_orders)
            val results = orders.map { order ->
                val matched = MatchingStrategy.match(order, transactions)
                if (matched == null) {
                    log.debug { "Dry run: no YNAB match for order id=${order.id}, amount=${order.totalAmount}" }
                    DryRunResult(
                        orderId = order.id,
                        orderDate = order.orderDate,
                        totalAmount = order.totalAmount,
                        itemsJson = order.itemsJson,
                        ynabTransactionId = null,
                        proposedCategoryId = null,
                        proposedCategoryName = null,
                        runAt = runAt
                    )
                } else {
                    var categoryId: String? = null
                    var errorMsg: String? = null
                    try {
                        categoryId = classificationService.classify(order)
                    } catch (e: Exception) {
                        log.warn(e) { "Dry run: Gemini classification failed for order id=${order.id}" }
                        errorMsg = e.message
                    }
                    val categoryName = resolveCategoryName(categoryId)
                    DryRunResult(
                        orderId = order.id,
                        orderDate = order.orderDate,
                        totalAmount = order.totalAmount,
                        itemsJson = order.itemsJson,
                        ynabTransactionId = matched.id,
                        proposedCategoryId = categoryId,
                        proposedCategoryName = categoryName,
                        errorMessage = errorMsg,
                        runAt = runAt
                    )
                }
            }

            dryRunResultRepository.saveAll(results)
            log.info { "Dry run complete: ${results.size} order(s) processed" }
            overallMessage = "Dry run: ${results.size} order(s) processed"
        } catch (e: Exception) {
            log.error(e) { "Dry run failed" }
            overallStatus = SyncStatus.FAIL
            overallMessage = e.message
        }

        syncLogRepository.save(
            SyncLog(source = SyncSource.DRY_RUN, lastRun = runAt, status = overallStatus, message = overallMessage)
        )
    }

    /**
     * Collects orders eligible for the dry run:
     * 1. Existing PENDING orders from the DB on or after [sinceInstant].
     * 2. New emails fetched from FastMail that are not yet in the DB.
     *
     * Returns a list capped to [orderCap] if set.
     */
    private fun gatherOrders(sinceInstant: Instant, orderCap: Int?): List<AmazonOrder> {
        // Start with existing PENDING orders filtered by date
        val existingOrders = amazonOrderRepository.findByStatus(OrderStatus.PENDING)
            .filter { !it.orderDate.isBefore(sinceInstant) }
        val existingIds = existingOrders.map { it.emailMessageId }.toSet()

        // Also fetch and parse fresh emails (transient — not persisted during dry run)
        val freshOrders = fetchFreshEmailOrders(sinceInstant, existingIds)

        val combined = existingOrders + freshOrders
        return if (orderCap != null) combined.take(orderCap) else combined
    }

    private fun fetchFreshEmailOrders(sinceInstant: Instant, alreadyKnownIds: Set<String>): List<AmazonOrder> {
        val user = configService.getValue(ConfigService.FASTMAIL_USER)
        val token = configService.getValue(ConfigService.FASTMAIL_TOKEN)
        if (user == null || token == null) return emptyList()

        return try {
            val emails = emailProviderClient.searchOrders(user, token, sinceInstant)
            emails
                .filter { it.messageId !in alreadyKnownIds }
                .mapNotNull { email -> parseEmail(email) }
        } catch (e: Exception) {
            log.warn(e) { "Dry run: failed to fetch emails from FastMail; continuing with DB orders only" }
            emptyList()
        }
    }

    private fun parseEmail(email: EmailOrder): AmazonOrder? {
        val amountMatch = AMOUNT_PATTERN.find(email.bodyText) ?: return null
        val amountStr = amountMatch.groupValues[1].replace(",", "")
        val totalAmount = amountStr.toBigDecimalOrNull() ?: return null

        val items = ITEM_PATTERN.findAll(email.bodyText)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() }
            .toList()
            .ifEmpty { listOf("Amazon Order") }

        return AmazonOrder(
            id = null,
            emailMessageId = email.messageId,
            orderDate = email.receivedAt,
            totalAmount = totalAmount,
            itemsJson = objectMapper.writeValueAsString(items),
            status = OrderStatus.PENDING,
            createdAt = Instant.now()
        )
    }

    private fun resolveCategoryName(categoryId: String?): String? {
        if (categoryId == null) return null
        return configService.getAllCategoryRules()
            .find { it.ynabCategoryId == categoryId }
            ?.ynabCategoryName
    }
}
