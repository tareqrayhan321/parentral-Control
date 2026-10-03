package com.example.core.enrollment

import android.content.Context
import com.example.core.database.ChildDatabase
import com.example.core.database.repository.PolicyRepository
import com.example.core.database.repository.RoomPolicyRepository
import com.example.core.model.EnrollmentStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Local enrollment state only. Pairing itself now happens through the Firebase backend
 * (see core.sync.SyncGateway.claimPairing); the old offline HMAC/typed-code pairing was removed.
 */
interface EnrollmentManager {
    suspend fun unenrollDevice(): Boolean
}

class DefaultEnrollmentManager(
    private val context: Context,
    private val policyRepository: PolicyRepository = RoomPolicyRepository(ChildDatabase.getInstance(context))
) : EnrollmentManager {

    override suspend fun unenrollDevice(): Boolean = withContext(Dispatchers.IO) {
        val current = policyRepository.getDevice() ?: return@withContext false
        policyRepository.saveDevice(current.copy(enrollmentStatus = EnrollmentStatus.UNENROLLED))
        policyRepository.logSyncAudit(
            event = "DEVICE_UNENROLLED",
            version = current.policyVersion,
            details = "Device unenrolled"
        )
        true
    }
}
