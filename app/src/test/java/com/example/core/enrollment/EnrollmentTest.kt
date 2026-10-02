package com.example.core.enrollment

import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import com.example.core.database.ChildDatabase
import com.example.core.database.repository.RoomPolicyRepository
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
    fun `processEnrollmentQr succeeds and marks device enrolled`() = runTest {
        val payload = PairingPayload.create(
            parentId = "parent_tareq",
            childDeviceName = "Tahmid's Tablet",
            validityMinutes = 15,
            parentSecret = "secure_secret_123"
        )
        val qrJson = payload.toJson()

        val result = enrollmentManager.processEnrollmentQr(qrJson, "secure_secret_123")
        assertTrue("Enrollment must succeed", result is EnrollmentResult.Success)

        val enrolledDevice = (result as EnrollmentResult.Success).device
        assertEquals("Tahmid's Tablet", enrolledDevice.deviceName)
        assertEquals(EnrollmentStatus.ENROLLED, enrolledDevice.enrollmentStatus)

        val retrievedDevice = policyRepository.getDevice()
        assertNotNull(retrievedDevice)
        assertEquals(EnrollmentStatus.ENROLLED, retrievedDevice?.enrollmentStatus)
        assertEquals("parent_tareq", retrievedDevice?.parentId)
    }

    @Test
    fun `processEnrollmentQr rejects expired payload`() = runTest {
        val payload = PairingPayload(
            pairingToken = "expired_token",
            parentId = "parent_1",
            childDeviceId = "device_1",
            childDeviceName = "Child Tablet",
            expiresAtEpochMs = System.currentTimeMillis() - 10000L, // 10 seconds ago
            signature = "sig"
        )

        val result = enrollmentManager.processEnrollmentQr(payload.toJson(), "parent_secret_key")
        assertEquals(EnrollmentResult.Expired, result)
    }

    @Test
    fun `processEnrollmentQr rejects tampered signature`() = runTest {
        val payload = PairingPayload.create(
            parentId = "parent_1",
            childDeviceName = "Tablet",
            validityMinutes = 10,
            parentSecret = "secret_A"
        )
        // Attempt verification with secret_B
        val result = enrollmentManager.processEnrollmentQr(payload.toJson(), "secret_B")
        assertEquals(EnrollmentResult.SignatureMismatch, result)
    }
}
