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
) {
    /**
     * A REAL pairing: enrolled AND bound to a parent. The local placeholder row that the first policy apply
     * writes ("local_child_device", ENROLLED, empty parentId) is NOT a pairing and must never be shown
     * as "Linked" or compared against the Firebase uid.
     */
    val isPaired: Boolean
        get() = enrollmentStatus == EnrollmentStatus.ENROLLED && parentId.isNotBlank()
}

enum class EnrollmentStatus {
    PENDING,
    ENROLLED,
    UNENROLLED
}
