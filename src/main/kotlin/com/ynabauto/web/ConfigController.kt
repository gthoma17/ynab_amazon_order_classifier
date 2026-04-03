package com.ynabauto.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.ynabauto.domain.CategoryRule
import com.ynabauto.domain.DryRunResult
import com.ynabauto.service.ConfigService
import com.ynabauto.service.ConnectionProbeService
import com.ynabauto.service.DryRunService
import com.ynabauto.service.ScheduleConfig
import com.ynabauto.service.ScheduleType
import com.ynabauto.service.SyncScheduler
import com.ynabauto.web.dto.ApiKeysRequest
import com.ynabauto.web.dto.ApiKeysResponse
import com.ynabauto.web.dto.CategoryRuleRequest
import com.ynabauto.web.dto.CategoryRuleResponse
import com.ynabauto.web.dto.DryRunRequest
import com.ynabauto.web.dto.DryRunResultResponse
import com.ynabauto.web.dto.ProcessingConfigRequest
import com.ynabauto.web.dto.ProcessingConfigResponse
import com.ynabauto.web.dto.ProbeResult
import com.ynabauto.web.dto.ScheduleConfigDto
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.LocalDate

@RestController
@RequestMapping("/api/config")
class ConfigController(
    private val configService: ConfigService,
    private val connectionProbeService: ConnectionProbeService,
    private val syncScheduler: SyncScheduler,
    private val dryRunService: DryRunService,
    private val objectMapper: ObjectMapper
) {

    @GetMapping("/keys")
    fun getKeys(): ApiKeysResponse {
        return ApiKeysResponse(
            ynabToken = configService.getValue(ConfigService.YNAB_TOKEN),
            ynabBudgetId = configService.getValue(ConfigService.YNAB_BUDGET_ID),
            fastmailUser = configService.getValue(ConfigService.FASTMAIL_USER),
            fastmailToken = configService.getValue(ConfigService.FASTMAIL_TOKEN),
            geminiKey = configService.getValue(ConfigService.GEMINI_KEY)
        )
    }

    @PutMapping("/keys")
    fun updateKeys(@RequestBody request: ApiKeysRequest): ResponseEntity<Void> {
        request.ynabToken?.let { configService.setValue(ConfigService.YNAB_TOKEN, it) }
        request.ynabBudgetId?.let { configService.setValue(ConfigService.YNAB_BUDGET_ID, it) }
        request.fastmailUser?.let { configService.setValue(ConfigService.FASTMAIL_USER, it) }
        request.fastmailToken?.let { configService.setValue(ConfigService.FASTMAIL_TOKEN, it) }
        request.geminiKey?.let { configService.setValue(ConfigService.GEMINI_KEY, it) }
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/categories")
    fun getCategories(): List<CategoryRuleResponse> {
        return configService.getAllCategoryRules().map { it.toResponse() }
    }

    @PutMapping("/categories")
    fun updateCategories(@RequestBody rules: List<CategoryRuleRequest>): ResponseEntity<Void> {
        val entities = rules.map { req ->
            CategoryRule(
                ynabCategoryId = req.ynabCategoryId,
                ynabCategoryName = req.ynabCategoryName,
                userDescription = req.userDescription,
                updatedAt = Instant.now()
            )
        }
        configService.saveCategoryRules(entities)
        return ResponseEntity.noContent().build()
    }

    private fun CategoryRule.toResponse() = CategoryRuleResponse(
        id = id,
        ynabCategoryId = ynabCategoryId,
        ynabCategoryName = ynabCategoryName,
        userDescription = userDescription
    )

    @PostMapping("/probe/fastmail")
    fun probeFastMail(): ProbeResult = connectionProbeService.probeFastMail()

    @PostMapping("/probe/ynab")
    fun probeYnab(): ProbeResult = connectionProbeService.probeYnab()

    @PostMapping("/probe/gemini")
    fun probeGemini(): ProbeResult = connectionProbeService.probeGemini()

    // ── Processing guardrails ──────────────────────────────────────────────────

    @GetMapping("/processing")
    fun getProcessingConfig(): ProcessingConfigResponse {
        val scheduleJson = configService.getValue(ConfigService.SCHEDULE_CONFIG)
        val scheduleDto = scheduleJson?.let {
            runCatching { objectMapper.readValue(it, ScheduleConfig::class.java).toDto() }.getOrNull()
        }
        return ProcessingConfigResponse(
            orderCap = configService.getValue(ConfigService.ORDER_CAP)?.toIntOrNull() ?: 0,
            startFromDate = configService.getValue(ConfigService.START_FROM_DATE),
            installedAt = configService.getValue(ConfigService.INSTALLED_AT),
            scheduleConfig = scheduleDto
        )
    }

    @PutMapping("/processing")
    fun updateProcessingConfig(@RequestBody request: ProcessingConfigRequest): ResponseEntity<Void> {
        request.orderCap?.let { configService.setValue(ConfigService.ORDER_CAP, it.toString()) }
        request.startFromDate?.let { configService.setValue(ConfigService.START_FROM_DATE, it) }
        request.scheduleConfig?.let {
            val scheduleConfig = it.toDomain()
            configService.setValue(ConfigService.SCHEDULE_CONFIG, objectMapper.writeValueAsString(scheduleConfig))
            // Apply new schedule immediately without requiring a container restart
            syncScheduler.reschedule()
        }
        return ResponseEntity.noContent().build()
    }

    // ── Dry run ───────────────────────────────────────────────────────────────

    @PostMapping("/dry-run")
    fun triggerDryRun(@RequestBody(required = false) request: DryRunRequest?): List<DryRunResultResponse> {
        val startFromDate = request?.startFromDate?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }
        dryRunService.runDryRun(startFromDate)
        return getDryRunResults()
    }

    @GetMapping("/dry-run/results")
    fun getDryRunResults(): List<DryRunResultResponse> {
        return dryRunService.getResults().map { it.toResponse() }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun ScheduleConfig.toDto() = ScheduleConfigDto(
        type = type.name,
        secondInterval = secondInterval,
        minuteInterval = minuteInterval,
        hourInterval = hourInterval,
        hour = hour,
        minute = minute,
        dayOfWeek = dayOfWeek
    )

    private fun ScheduleConfigDto.toDomain() = ScheduleConfig(
        type = runCatching { ScheduleType.valueOf(type) }.getOrDefault(ScheduleType.EVERY_N_HOURS),
        secondInterval = secondInterval,
        minuteInterval = minuteInterval,
        hourInterval = hourInterval,
        hour = hour,
        minute = minute,
        dayOfWeek = dayOfWeek
    )

    private fun DryRunResult.toResponse(): DryRunResultResponse {
        val items = runCatching {
            @Suppress("UNCHECKED_CAST")
            objectMapper.readValue(itemsJson, List::class.java) as List<String>
        }.getOrDefault(emptyList())
        return DryRunResultResponse(
            id = id!!,
            orderId = orderId,
            orderDate = orderDate,
            totalAmount = totalAmount,
            items = items,
            ynabTransactionId = ynabTransactionId,
            proposedCategoryId = proposedCategoryId,
            proposedCategoryName = proposedCategoryName,
            errorMessage = errorMessage,
            runAt = runAt
        )
    }
}

