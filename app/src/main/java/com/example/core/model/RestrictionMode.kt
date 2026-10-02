package com.example.core.model

/**
 * Restriction mode applicable to a managed application.
 */
enum class RestrictionMode {
    /** App is freely usable, subject only to global schedules if attached. */
    ALLOWED,

    /** App is completely blocked from launching and suspended via DevicePolicyManager. */
    BLOCKED,

    /** App is allowed up to a specified daily usage limit in minutes. */
    LIMITED
}
