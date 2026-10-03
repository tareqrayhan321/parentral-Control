package com.example.core.enrollment

import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import com.example.core.database.ChildDatabase
import com.example.core.database.repository.RoomPolicyRepository
import com.example.core.model.ChildDevice
import com.example.core.model.EnrollmentStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EnrollmentTest {

    private lateinit var database: ChildDatabase
    private lateinit var policyRepository: RoomPolicyRepository
    private lateinit var enrollmentManager: EnrollmentManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, ChildDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        policyRepository = RoomPolicyRepository(database)
        enrollmentManager = DefaultEnrollmentManager(context, policyRepository)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `QrCodeGenerator creates non-null square bitmap`() {
        val bitmap = QrCodeGenerator.generateQrBitmap("test_pairing_string", 256)
        assertNotNull(bitmap)
        assertEquals(256, bitmap?.width)
        assertEquals(256, bitmap?.height)
    }

    @Test
    fun `unenrollDevice marks an enrolled device as unenrolled`() = runTest {
        policyRepository.saveDevice(
            ChildDevice(
                deviceId = "child1", parentId = "parent1", deviceName = "Tab",
                lastSeenEpochMs = 0L, policyVersion = 1, enrollmentStatus = EnrollmentStatus.ENROLLED
            )
        )
        assertTrue(enrollmentManager.unenrollDevice())
        assertEquals(EnrollmentStatus.UNENROLLED, policyRepository.getDevice()?.enrollmentStatus)
    }

    @Test
    fun `unenrollDevice with no device returns false`() = runTest {
        assertEquals(false, enrollmentManager.unenrollDevice())
    }
}
