package com.example.core.model

/**
 * Child device metadata representation.
 *
 * Device Owner status is deliberately NOT part of this model: it is a live Android
 * system fact and must always be read from DeviceOwnerManager.isDeviceOwner(),
 * never from stored data.
 */
data class ChildDevice(
    val deviceId: String,
    val parentId: String,
    val deviceName: String,
    val platform: String = "Android",
    val appVersion: String,
    val lastSeenEpochMs: Long,
    val policyVersion: Int,
    val enrollmentStatus: EnrollmentStatus = EnrollmentStatus.ENROLLED
)

enum class EnrollmentStatus {
    PENDING,
    ENROLLED,
    UNENROLLED
}
