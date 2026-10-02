package com.example.core.model

/**
 * Child device metadata representation.
 */
data class ChildDevice(
    val deviceId: String,
    val parentId: String,
    val deviceName: String,
    val platform: String = "Android",
    val appVersion: String,
    val lastSeenEpochMs: Long,
    val policyVersion: Int,
    val isDeviceOwner: Boolean = false,
    val enrollmentStatus: EnrollmentStatus = EnrollmentStatus.ENROLLED
)

enum class EnrollmentStatus {
    PENDING,
    ENROLLED,
    UNENROLLED
}
