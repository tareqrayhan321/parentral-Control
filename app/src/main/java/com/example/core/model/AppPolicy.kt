package com.example.core.model

/**
 * Individual application policy configured by the parent.
 */
data class AppPolicy(
    val packageName: String,
    val displayName: String,
    val mode: RestrictionMode = RestrictionMode.ALLOWED,
    val dailyLimitMinutes: Int? = null,
    val scheduleIds: List<String> = emptyList(),
    val enabled: Boolean = true
) {
    init {
        if (mode == RestrictionMode.LIMITED) {
            require(dailyLimitMinutes != null && dailyLimitMinutes in 1..1440) {
                "LIMITED mode requires dailyLimitMinutes between 1 and 1440. Received: $dailyLimitMinutes"
            }
        }
    }
}
