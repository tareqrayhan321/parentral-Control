package com.example.core.enrollment

import android.content.Context
import android.util.Log
import com.example.core.database.ChildDatabase
import com.example.core.database.repository.PolicyRepository
import com.example.core.database.repository.RoomPolicyRepository
import com.example.core.model.ChildDevice
import com.example.core.model.EnrollmentStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface EnrollmentResult {
    data class Success(val device: ChildDevice) : EnrollmentResult
    data object InvalidQr : EnrollmentResult
    data object Expired : EnrollmentResult
    data object SignatureMismatch : EnrollmentResult
}

interface EnrollmentManager {
    suspend fun processEnrollmentQr(
        qrContent: String,
        parentSecret: String = "parent_secret_key"
    ): EnrollmentResult

    suspend fun unenrollDevice(): Boolean
}

class DefaultEnrollmentManager(
    private val context: Context,
    private val policyRepository: PolicyRepository = RoomPolicyRepository(ChildDatabase.getInstance(context))
) : EnrollmentManager {

    override suspend fun processEnrollmentQr(
        qrContent: String,
        parentSecret: String
    ): EnrollmentResult = withContext(Dispatchers.IO) {
        val payload = PairingPayload.fromJson(qrContent)
            ?: return@withContext EnrollmentResult.InvalidQr

        if (payload.isExpired()) {
            Log.w(TAG, "Pairing QR has expired.")
            return@withContext EnrollmentResult.Expired
        }

        if (!PairingPayload.verifySignature(payload, parentSecret)) {
            Log.w(TAG, "Pairing QR signature does not match.")
            return@withContext EnrollmentResult.SignatureMismatch
        }

        val childDevice = ChildDevice(
            deviceId = payload.childDeviceId,
            parentId = payload.parentId,
            deviceName = payload.childDeviceName,
            platform = "Android",
            appVersion = "1.0",
            lastSeenEpochMs = System.currentTimeMillis(),
            policyVersion = 1,
            isDeviceOwner = true,
            enrollmentStatus = EnrollmentStatus.ENROLLED
        )

        policyRepository.saveDevice(childDevice)
        policyRepository.logSyncAudit(
            event = "DEVICE_ENROLLED",
            version = 1,
            details = "Device paired successfully with parent ${payload.parentId}"
        )

        Log.i(TAG, "Enrollment completed for ${childDevice.deviceName} (${childDevice.deviceId})")
        EnrollmentResult.Success(childDevice)
    }

    override suspend fun unenrollDevice(): Boolean = withContext(Dispatchers.IO) {
        val current = policyRepository.getDevice() ?: return@withContext false
        val unenrolled = current.copy(enrollmentStatus = EnrollmentStatus.UNENROLLED)
        policyRepository.saveDevice(unenrolled)
        policyRepository.logSyncAudit(
            event = "DEVICE_UNENROLLED",
            version = current.policyVersion,
            details = "Device unenrolled by user"
        )
        true
    }

    companion object {
        private const val TAG = "EnrollmentManager"
    }
}
