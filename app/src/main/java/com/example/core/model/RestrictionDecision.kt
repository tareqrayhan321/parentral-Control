package com.example.core.model

/**
 * Underlying reason why an application was blocked by the Policy Engine.
 */
enum class BlockReason {
    /** Explicitly set to BLOCKED by parent policy. */
    MANUALLY_BLOCKED,

    /** Blocked by an active time schedule (e.g. Bedtime or School hours). */
    SCHEDULE_ACTIVE,

    /** Reached the daily allowed screen time limit for today. */
    DAILY_LIMIT_REACHED
}

/**
 * Deterministic decision evaluated by the PolicyEngine for a given app.
 */
sealed class RestrictionDecision {
    /** Application is permitted to run. */
    data object Allowed : RestrictionDecision()

    /** Application must be suspended / blocked. */
    data class Blocked(
        val reason: BlockReason,
        val details: String? = null
    ) : RestrictionDecision()
}
