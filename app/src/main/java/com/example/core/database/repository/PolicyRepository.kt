package com.example.core.database.repository

import androidx.room.withTransaction
import com.example.core.database.ChildDatabase
import com.example.core.database.entity.AppPolicyEntity
import com.example.core.database.entity.ChildDeviceEntity
import com.example.core.database.entity.DailyUsageEntity
import com.example.core.database.entity.ScheduleEntity
import com.example.core.database.entity.SyncAuditEntity
import com.example.core.model.AppPolicy
import com.example.core.model.ChildDevice
import com.example.core.model.EnrollmentStatus
import com.example.core.model.Policy
import com.example.core.model.Schedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

interface PolicyRepository {
    fun observeCurrentPolicy(): Flow<Policy>
    suspend fun getCurrentPolicy(): Policy
    suspend fun applyNewPolicyAtomic(policy: Policy, deviceId: String? = null): Boolean
    fun observeDevice(): Flow<ChildDevice?>
    suspend fun getDevice(): ChildDevice?
    suspend fun saveDevice(device: ChildDevice)
    suspend fun recordTodayUsage(packageName: String, dateString: String, minutes: Int)
    suspend fun getTodayUsageMap(dateString: String): Map<String, Int>
    fun observeTodayUsage(dateString: String): Flow<Map<String, Int>>
    suspend fun updateAppSuspension(packageName: String, isSuspended: Boolean)
    suspend fun logSyncAudit(event: String, version: Int, details: String? = null)
    fun observeRecentAudits(): Flow<List<SyncAuditEntity>>
}

class RoomPolicyRepository(
    private val database: ChildDatabase
) : PolicyRepository {

    private val appPolicyDao = database.appPolicyDao()
    private val scheduleDao = database.scheduleDao()
    private val childDeviceDao = database.childDeviceDao()
    private val dailyUsageDao = database.dailyUsageDao()
    private val syncAuditDao = database.syncAuditDao()

    override fun observeCurrentPolicy(): Flow<Policy> {
        return combine(
            appPolicyDao.observeAllPolicies(),
            scheduleDao.observeAllSchedules(),
            childDeviceDao.observeCurrentDevice()
        ) { appEntities, scheduleEntities, deviceEntity ->
            val apps = appEntities.associate { it.packageName to it.toDomain() }
            val schedules = scheduleEntities.associate { it.id to it.toDomain() }
            Policy(
                version = deviceEntity?.policyVersion ?: 0,
                updatedAtEpochMs = deviceEntity?.lastSynchronizedAt ?: 0L,
                apps = apps,
                schedules = schedules
            )
        }
    }

    override suspend fun getCurrentPolicy(): Policy {
        val appEntities = appPolicyDao.getAllPolicies()
        val scheduleEntities = scheduleDao.getAllSchedules()
        val deviceEntity = childDeviceDao.getCurrentDevice()

        val apps = appEntities.associate { it.packageName to it.toDomain() }
        val schedules = scheduleEntities.associate { it.id to it.toDomain() }

        return Policy(
            version = deviceEntity?.policyVersion ?: 0,
            updatedAtEpochMs = deviceEntity?.lastSynchronizedAt ?: 0L,
            apps = apps,
            schedules = schedules
        )
    }

    override suspend fun applyNewPolicyAtomic(policy: Policy, deviceId: String?): Boolean {
        return database.withTransaction {
            val currentDevice = childDeviceDao.getCurrentDevice()
            val currentVersion = currentDevice?.policyVersion ?: 0

            // Monotonic version enforcement: reject older or identical versions
            if (policy.version <= currentVersion && currentVersion != 0) {
                logSyncAudit(
                    event = "POLICY_REJECTED_STALE",
                    version = policy.version,
                    details = "Incoming version ${policy.version} is not newer than current $currentVersion"
                )
                return@withTransaction false
            }

            // 1. Synchronize App Policies
            val appEntities = policy.apps.values.map { AppPolicyEntity.fromDomain(it) }
            appPolicyDao.insertOrUpdateAll(appEntities)
            appPolicyDao.deleteOrphanedPolicies(policy.apps.keys.toList())

            // 2. Synchronize Schedules
            val scheduleEntities = policy.schedules.values.map { ScheduleEntity.fromDomain(it) }
            scheduleDao.insertOrUpdateAll(scheduleEntities)
            scheduleDao.deleteOrphanedSchedules(policy.schedules.keys.toList())

            // 3. Update Device Policy Version
            val targetDeviceId = deviceId ?: currentDevice?.deviceId ?: "local_child_device"
            if (currentDevice != null) {
                childDeviceDao.updatePolicyVersion(
                    deviceId = targetDeviceId,
                    version = policy.version,
                    syncTime = policy.updatedAtEpochMs
                )
            } else {
                childDeviceDao.saveDevice(
                    ChildDeviceEntity(
                        deviceId = targetDeviceId,
                        parentId = "",
                        deviceName = "Managed Child Device",
                        enrollmentStatus = EnrollmentStatus.ENROLLED.name,
                        policyVersion = policy.version,
                        lastSynchronizedAt = policy.updatedAtEpochMs
                    )
                )
            }

            // 4. Audit Log
            logSyncAudit(
                event = "POLICY_APPLIED",
                version = policy.version,
                details = "Updated ${policy.apps.size} apps and ${policy.schedules.size} schedules"
            )

            true
        }
    }

    override fun observeDevice(): Flow<ChildDevice?> {
        return childDeviceDao.observeCurrentDevice().map { entity ->
            entity?.let {
                val status = try {
                    EnrollmentStatus.valueOf(it.enrollmentStatus)
                } catch (_: Exception) {
                    EnrollmentStatus.PENDING
                }
                ChildDevice(
                    deviceId = it.deviceId,
                    parentId = it.parentId,
                    deviceName = it.deviceName,
                    platform = "Android",
                    appVersion = "1.0",
                    lastSeenEpochMs = it.lastSynchronizedAt,
                    policyVersion = it.policyVersion,
                    enrollmentStatus = status
                )
            }
        }
    }

    override suspend fun getDevice(): ChildDevice? {
        val entity = childDeviceDao.getCurrentDevice() ?: return null
        val status = try {
            EnrollmentStatus.valueOf(entity.enrollmentStatus)
        } catch (_: Exception) {
            EnrollmentStatus.PENDING
        }
        return ChildDevice(
            deviceId = entity.deviceId,
            parentId = entity.parentId,
            deviceName = entity.deviceName,
            platform = "Android",
            appVersion = "1.0",
            lastSeenEpochMs = entity.lastSynchronizedAt,
            policyVersion = entity.policyVersion,
            enrollmentStatus = status
        )
    }

    override suspend fun saveDevice(device: ChildDevice) {
        childDeviceDao.saveDevice(
            ChildDeviceEntity(
                deviceId = device.deviceId,
                parentId = device.parentId,
                deviceName = device.deviceName,
                enrollmentStatus = device.enrollmentStatus.name,
                policyVersion = device.policyVersion,
                lastSynchronizedAt = device.lastSeenEpochMs
            )
        )
    }

    override suspend fun recordTodayUsage(packageName: String, dateString: String, minutes: Int) {
        dailyUsageDao.recordUsage(
            DailyUsageEntity(
                packageName = packageName,
                dateString = dateString,
                usedMinutes = minutes,
                lastCalculatedTimestamp = System.currentTimeMillis()
            )
        )
    }

    override suspend fun getTodayUsageMap(dateString: String): Map<String, Int> {
        return dailyUsageDao.getDailyUsageForDate(dateString).associate {
            it.packageName to it.usedMinutes
        }
    }

    override fun observeTodayUsage(dateString: String): Flow<Map<String, Int>> {
        return dailyUsageDao.observeDailyUsageForDate(dateString).map { list ->
            list.associate { it.packageName to it.usedMinutes }
        }
    }

    override suspend fun updateAppSuspension(packageName: String, isSuspended: Boolean) {
        appPolicyDao.updateSuspensionStatus(packageName, isSuspended)
    }

    override suspend fun logSyncAudit(event: String, version: Int, details: String?) {
        syncAuditDao.insertAudit(
            SyncAuditEntity(
                event = event,
                policyVersion = version,
                details = details,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    override fun observeRecentAudits(): Flow<List<SyncAuditEntity>> {
        return syncAuditDao.observeRecentAudits()
    }
}
