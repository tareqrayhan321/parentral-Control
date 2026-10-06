package com.example.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.entity.ChildDeviceEntity
import com.example.core.database.repository.RoomPolicyRepository
import com.example.core.model.ChildDevice
import com.example.core.model.EnrollmentStatus
import com.example.core.model.Policy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regression tests for "the two phones will not connect": the local placeholder row written by the first
 * policy apply ("local_child_device", ENROLLED, empty parentId) used to hide the real paired device, so sync
 * compared the Firebase uid with "local_child_device" and stopped.
 */
@RunWith(RobolectricTestRunner::class)
class ChildDeviceRowsTest {

    private lateinit var database: ChildDatabase
    private lateinit var repository: RoomPolicyRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, ChildDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomPolicyRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun realDevice(id: String = "uid-real") = ChildDevice(
        deviceId = id, parentId = "parent-1", deviceName = "SM-T295",
        appVersion = "1.0", lastSeenEpochMs = 2_000L, policyVersion = 0,
        enrollmentStatus = EnrollmentStatus.ENROLLED
    )

    private fun rowCount(): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM child_device").use { c ->
            c.moveToFirst()
            c.getInt(0)
        }

    @Test
    fun `placeholder row is not a pairing`() = runTest {
        repository.applyNewPolicyAtomic(Policy(version = 1, updatedAtEpochMs = 1_000L))
        val placeholder = repository.getDevice()
        assertEquals("local_child_device", placeholder?.deviceId)
        assertFalse(placeholder!!.isPaired)
    }

    @Test
    fun `saving the real device replaces the placeholder`() = runTest {
        repository.applyNewPolicyAtomic(Policy(version = 1, updatedAtEpochMs = 1_000L))
        repository.saveDevice(realDevice())

        assertEquals(1, rowCount())
        val device = repository.getDevice()
        assertEquals("uid-real", device?.deviceId)
        assertEquals("parent-1", device?.parentId)
        assertTrue(device!!.isPaired)
    }

    @Test
    fun `legacy database holding both rows still resolves to the real device`() = runTest {
        val dao = database.childDeviceDao()
        // Oldest row first: exactly what an already-installed phone has.
        dao.saveDevice(
            ChildDeviceEntity("local_child_device", "", "Managed Child Device", EnrollmentStatus.ENROLLED.name, 1, 1_000L)
        )
        dao.saveDevice(
            ChildDeviceEntity("uid-real", "parent-1", "SM-T295", EnrollmentStatus.ENROLLED.name, 0, 2_000L)
        )

        val device = repository.getDevice()
        assertEquals("uid-real", device?.deviceId)
        assertTrue(device!!.isPaired)
    }

    @Test
    fun `unenrolling leaves one unpaired row`() = runTest {
        repository.applyNewPolicyAtomic(Policy(version = 1, updatedAtEpochMs = 1_000L))
        repository.saveDevice(realDevice())
        repository.saveDevice(repository.getDevice()!!.copy(enrollmentStatus = EnrollmentStatus.UNENROLLED))

        assertEquals(1, rowCount())
        assertFalse(repository.getDevice()!!.isPaired)
    }
}
