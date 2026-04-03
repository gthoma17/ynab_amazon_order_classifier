package com.ynabauto.web.dto

import java.math.BigDecimal
import java.time.Instant

data class ApiKeysRequest(
    val ynabToken: String? = null,
    val ynabBudgetId: String? = null,
    val fastmailUser: String? = null,
    val fastmailToken: String? = null,
    val geminiKey: String? = null
)

data class ApiKeysResponse(
    val ynabToken: String?,
    val ynabBudgetId: String?,
    val fastmailUser: String?,
    val fastmailToken: String?,
    val geminiKey: String?
)

data class CategoryRuleRequest(
    val ynabCategoryId: String,
    val ynabCategoryName: String,
    val userDescription: String
)

data class CategoryRuleResponse(
    val id: Long?,
    val ynabCategoryId: String,
    val ynabCategoryName: String,
    val userDescription: String
)

data class PendingOrderResponse(
    val id: Long,
    val orderDate: Instant,
    val totalAmount: BigDecimal,
    val items: List<String>,
    val status: String,
    val createdAt: Instant
)

data class SyncLogResponse(
    val id: Long,
    val source: String,
    val lastRun: Instant,
    val status: String,
    val message: String?
)

data class YnabCategoryResponse(
    val id: String,
    val name: String,
    val categoryGroupName: String
)

data class ProbeResult(
    val success: Boolean,
    val message: String
)

// ── Processing guardrails ──────────────────────────────────────────────────────

data class ProcessingConfigResponse(
    val orderCap: Int,
    val startFromDate: String?,
    val installedAt: String?,
    val scheduleConfig: ScheduleConfigDto?
)

data class ProcessingConfigRequest(
    val orderCap: Int? = null,
    val startFromDate: String? = null,
    val scheduleConfig: ScheduleConfigDto? = null
)

data class ScheduleConfigDto(
    val type: String,
    val secondInterval: Int? = null,
    val minuteInterval: Int? = null,
    val hourInterval: Int? = null,
    val hour: Int? = null,
    val minute: Int = 0,
    val dayOfWeek: String? = null
)

// ── Dry run ───────────────────────────────────────────────────────────────────

data class DryRunRequest(
    /** ISO date (yyyy-MM-dd). Defaults to one month ago when absent. */
    val startFromDate: String? = null
)

data class DryRunResultResponse(
    val id: Long,
    val orderId: Long?,
    val orderDate: Instant,
    val totalAmount: BigDecimal,
    val items: List<String>,
    val ynabTransactionId: String?,
    val proposedCategoryId: String?,
    val proposedCategoryName: String?,
    val errorMessage: String?,
    val runAt: Instant
)

