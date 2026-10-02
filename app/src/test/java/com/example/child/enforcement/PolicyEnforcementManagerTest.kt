package com.example.child.enforcement

import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import com.example.child.admin.DeviceOwnerManager
import com.example.core.database.ChildDatabase
import com.example.core.database.repository.RoomPolicyRepository
import com.example.core.model.AppPolicy
import com.example.core.model.Policy
import com.example.core.model.RestrictionDecision
import com.example.core.model.RestrictionMode
import com.example.core.policy.DefaultPolicyEngine
import com.example.core.usage.UsageRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PolicyEnforcementManagerTest {

    private lateinit var database: ChildDatabase
    private lateinit var policyRepository: RoomPolicyRepository
    private lateinit var fakeDeviceOwnerManager: FakeDeviceOwnerManager
    private lateinit var fakeUsageRepository: FakeUsageRepository
    private lateinit var enforcementManager: PolicyEnforcementManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, ChildDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        policyRepository = RoomPolicyRepository(database)
        fakeDeviceOwnerManager = FakeDeviceOwnerManager()
        fakeUsageRepository = FakeUsageRepository()

        enforcementManager = DefaultPolicyEnforcementManager(
            context = context,
            policyRepository = policyRepository,
            usageRepository = fakeUsageRepository,
            deviceOwnerManager = fakeDeviceOwnerManager,
            policyEngine = DefaultPolicyEngine()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `enforceCurrentPolicy suspends manually blocked and over-limit packages`() = runTest {
        val blockedApp = AppPolicy(
            packageName = "com.facebook.katana",
            displayName = "Facebook",
            mode = RestrictionMode.BLOCKED
        )
        val limitedAppExceeded = AppPolicy(
            packageName = "com.google.android.youtube",
            displayName = "YouTube",
            mode = RestrictionMode.LIMITED,
            dailyLimitMinutes = 30
        )
        val limitedAppAllowed = AppPolicy(
            packageName = "com.chess",
            displayName = "Chess",
            mode = RestrictionMode.LIMITED,
            dailyLimitMinutes = 60
        )

        val policy = Policy(
            version = 1,
            updatedAtEpochMs = System.currentTimeMillis(),
            apps = mapOf(
                blockedApp.packageName to blockedApp,
                limitedAppExceeded.packageName to limitedAppExceeded,
                limitedAppAllowed.packageName to limitedAppAllowed
            )
        )
        policyRepository.applyNewPolicyAtomic(policy)

        fakeUsageRepository.usageMap["com.google.android.youtube"] = 35 // exceeded 30
        fakeUsageRepository.usageMap["com.chess"] = 15 // within 60

        val decisions = enforcementManager.enforceCurrentPolicy()

        assertTrue(decisions["com.facebook.katana"] is RestrictionDecision.Blocked)
        assertTrue(decisions["com.google.android.youtube"] is RestrictionDecision.Blocked)
        assertEquals(RestrictionDecision.Allowed, decisions["com.chess"])

        // Verify Device Owner was commanded to suspend Facebook and YouTube
        assertTrue(fakeDeviceOwnerManager.currentlySuspended.contains("com.facebook.katana"))
        assertTrue(fakeDeviceOwnerManager.currentlySuspended.contains("com.google.android.youtube"))
        assertTrue(!fakeDeviceOwnerManager.currentlySuspended.contains("com.chess"))

        // Verify Room database marked them as suspended
        val fbEntity = database.appPolicyDao().getPolicyForPackage("com.facebook.katana")
        assertTrue(fbEntity?.isSuspended == true)

        val chessEntity = database.appPolicyDao().getPolicyForPackage("com.chess")
        assertTrue(chessEntity?.isSuspended == false)
    }

    private class FakeDeviceOwnerManager : DeviceOwnerManager {
        val currentlySuspended = mutableSetOf<String>()
        var protectionsEnforced = false

        override fun isAdminActive(): Boolean = true
        override fun isDeviceOwner(): Boolean = true

        override fun suspendPackages(packageNames: List<String>): List<String> {
            currentlySuspended.addAll(packageNames)
            return emptyList()
        }

        override fun unsuspendPackages(packageNames: List<String>): List<String> {
            currentlySuspended.removeAll(packageNames.toSet())
            return emptyList()
        }

        override fun isPackageSuspended(packageName: String): Boolean =
            currentlySuspended.contains(packageName)

        override fun syncSuspendedPackages(
            desiredSuspendedPackages: Set<String>,
            allTrackedPackages: Set<String>
        ) {
            currentlySuspended.clear()
            currentlySuspended.addAll(desiredSuspendedPackages)
        }

        override fun enforceDeviceProtections() {
            protectionsEnforced = true
        }

        override fun removeDeviceProtections() {
            protectionsEnforced = false
        }

        private var _supervisionActive = true
        private var _cameraDisabled = false
        private var _installBlocked = true

        override fun isSupervisionActive(): Boolean = _supervisionActive
        override fun setSupervisionActive(active: Boolean) { _supervisionActive = active }
        override fun isCameraDisabled(): Boolean = _cameraDisabled
        override fun setCameraDisabled(disabled: Boolean) { _cameraDisabled = disabled }
        override fun isAppInstallBlocked(): Boolean = _installBlocked
        override fun setAppInstallBlocked(blocked: Boolean) { _installBlocked = blocked }

        private var _dnsEnforced = true
        private var _dnsHost = "family-filter-dns.cleanbrowsing.org"

        override fun isMandatoryDnsEnforced(): Boolean = _dnsEnforced
        override fun getEnforcedDnsHost(): String = _dnsHost
        override fun setMandatoryDns(enabled: Boolean, dnsHost: String): Boolean {
            _dnsEnforced = enabled
            _dnsHost = dnsHost
            return true
        }
    }

    private class FakeUsageRepository : UsageRepository {
        val usageMap = mutableMapOf<String, Int>()

        override fun hasUsageStatsPermission(): Boolean = true
        override suspend fun refreshTodayUsage(): Map<String, Int> = usageMap
        override suspend fun getTodayUsageMinutes(packageName: String): Int = usageMap[packageName] ?: 0
        override suspend fun handleMidnightReset() {
            usageMap.clear()
        }
    }
}
