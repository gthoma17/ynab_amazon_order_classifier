package com.budgetsortbot.service

enum class ScheduleType {
    HOURLY,
    EVERY_N_HOURS,
    EVERY_N_MINUTES,

    /**
     * Fires every N seconds. Intended for development and testing only —
     * not recommended for production deployments.
     */
    EVERY_N_SECONDS,
    DAILY,
    WEEKLY,
}

/**
 * Human-readable schedule configuration stored as JSON in app_config.
 * Translated to a Spring cron expression for actual scheduling.
 *
 * Cron format used by Spring: second minute hour day-of-month month day-of-week
 */
data class ScheduleConfig(
    val type: ScheduleType = ScheduleType.EVERY_N_HOURS,
    /** Used when type = EVERY_N_SECONDS. Must be >= 1. */
    val secondInterval: Int? = null,
    /** Used when type = EVERY_N_MINUTES. Must be >= 1. */
    val minuteInterval: Int? = null,
    /** Used when type = EVERY_N_HOURS. Must be >= 1. */
    val hourInterval: Int? = 5,
    /** Hour-of-day (0–23). Used when type = DAILY or WEEKLY. */
    val hour: Int? = 0,
    /** Minute (0–59). Used when type = DAILY or WEEKLY. */
    val minute: Int = 0,
    /**
     * Day-of-week. Used when type = WEEKLY.
     * Accepts Spring cron day names: MON, TUE, WED, THU, FRI, SAT, SUN.
     */
    val dayOfWeek: String? = null,
) {
    /**
     * Converts this configuration to a 6-field Spring cron expression.
     * Returns null if the configuration is invalid.
     */
    fun toCron(): String? {
        return when (type) {
            ScheduleType.EVERY_N_SECONDS -> {
                val n = secondInterval?.takeIf { it >= 1 } ?: return null
                "*/$n * * * * *"
            }
            ScheduleType.EVERY_N_MINUTES -> {
                val n = minuteInterval?.takeIf { it >= 1 } ?: return null
                "0 */$n * * * *"
            }
            ScheduleType.HOURLY -> "0 0 * * * *"
            ScheduleType.EVERY_N_HOURS -> {
                val n = hourInterval?.takeIf { it >= 1 } ?: return null
                "0 0 */$n * * *"
            }
            ScheduleType.DAILY -> {
                val h = hour?.takeIf { it in 0..23 } ?: return null
                val m = minute.takeIf { it in 0..59 } ?: return null
                "0 $m $h * * *"
            }
            ScheduleType.WEEKLY -> {
                val h = hour?.takeIf { it in 0..23 } ?: return null
                val m = minute.takeIf { it in 0..59 } ?: return null
                val dow = dayOfWeek?.takeIf { it.isNotBlank() } ?: return null
                "0 $m $h * * $dow"
            }
        }
    }
}
