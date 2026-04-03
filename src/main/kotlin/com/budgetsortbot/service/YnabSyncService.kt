package com.budgetsortbot.service

import com.budgetsortbot.domain.AmazonOrder
import com.budgetsortbot.domain.OrderStatus
import com.budgetsortbot.domain.SyncLog
import com.budgetsortbot.domain.SyncSource
import com.budgetsortbot.domain.SyncStatus
import com.budgetsortbot.infrastructure.persistence.AmazonOrderRepository
import com.budgetsortbot.infrastructure.persistence.SyncLogRepository
import com.budgetsortbot.infrastructure.ynab.YnabClient
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Service
class YnabSyncService(
    private val ynabClient: YnabClient,
    private val amazonOrderRepository: AmazonOrderRepository,
    private val syncLogRepository: SyncLogRepository,
    private val configService: ConfigService,
    private val classificationService: ClassificationService
) {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    fun sync() {
        val token = configService.getValue(ConfigService.YNAB_TOKEN)
        val budgetId = configService.getValue(ConfigService.YNAB_BUDGET_ID)
        if (token == null || budgetId == null) {
            log.warn { "YNAB credentials not configured, skipping sync" }
            return
        }

        val startFromDate = configService.getValue(ConfigService.START_FROM_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val startFromInstant = startFromDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()

        val orderCap = configService.getValue(ConfigService.ORDER_CAP)
            ?.toIntOrNull()
            ?.takeIf { it > 0 }

        var status = SyncStatus.SUCCESS
        var message: String? = null

        try {
            var pendingOrders = amazonOrderRepository.findByStatus(OrderStatus.PENDING)

            // Exclude orders dated before the start-from cutoff
            if (startFromInstant != null) {
                pendingOrders = pendingOrders.filter { !it.orderDate.isBefore(startFromInstant) }
            }

            // Apply per-run order cap
            if (orderCap != null) {
                pendingOrders = pendingOrders.take(orderCap)
            }

            log.info { "YNAB sync: processing ${pendingOrders.size} pending order(s)" }

            if (pendingOrders.isNotEmpty()) {
                val sinceDate = pendingOrders
                    .minOf { it.orderDate }
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate()
                    .minusDays(1)

                val transactions = ynabClient.getTransactions(budgetId, token, sinceDate)
                log.debug { "Fetched ${transactions.size} YNAB transaction(s) since $sinceDate" }

                for (order in pendingOrders) {
                    processOrder(order, transactions, budgetId, token)
                }
            }
        } catch (e: Exception) {
            log.error(e) { "YNAB sync failed" }
            status = SyncStatus.FAIL
            message = e.message
        }

        syncLogRepository.save(
            SyncLog(source = SyncSource.YNAB, lastRun = Instant.now(), status = status, message = message)
        )
    }

    internal fun processOrder(
        order: AmazonOrder,
        transactions: List<com.budgetsortbot.infrastructure.ynab.YnabTransaction>,
        budgetId: String,
        token: String
    ) {
        val matched = MatchingStrategy.match(order, transactions)
        if (matched == null) {
            log.debug { "No YNAB match found for order id=${order.id}, amount=${order.totalAmount}" }
            return
        }

        val matchedOrder = amazonOrderRepository.save(
            order.copy(status = OrderStatus.MATCHED, ynabTransactionId = matched.id)
        )
        log.info { "Matched order id=${order.id} to YNAB transaction id=${matched.id}" }

        try {
            val categoryId = classificationService.classify(matchedOrder)
            val completedOrder = matchedOrder.copy(status = OrderStatus.COMPLETED, ynabCategoryId = categoryId)
            amazonOrderRepository.save(completedOrder)

            val memo = buildMemo(matchedOrder)
            ynabClient.updateTransaction(budgetId, matched.id, token, memo, categoryId)
            log.info { "Completed order id=${order.id}, classified as categoryId=$categoryId" }
        } catch (e: Exception) {
            log.error(e) { "Failed to classify/update order id=${order.id}" }
        }
    }

    private fun buildMemo(order: AmazonOrder): String {
        return "Amazon order (id=${order.id})"
    }
}

