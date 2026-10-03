package com.example.core.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Arrays
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
class PinSecurityManagerTest {

    private lateinit var pinManager: AndroidPinSecurityManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        pinManager = AndroidPinSecurityManager(
            context,
            testKeyOverride = SecretKeySpec(ByteArray(32) { (it * 7 + 3).toByte() }, "AES")
        )
        pinManager.clearPinForTesting()
    }

    @Test
    fun `isPinConfigured returns false initially and true after setup`() {
        assertFalse(pinManager.isPinConfigured())
        assertTrue(pinManager.setupPin("4826"))
        assertTrue(pinManager.isPinConfigured())
    }

    @Test
    fun `verifyPin returns Success for correct PIN and Incorrect for wrong PIN`() {
        pinManager.setupPin("7391")

        val wrongResult = pinManager.verifyPin("0000")
        assertTrue("Wrong PIN must return Incorrect", wrongResult is PinVerificationResult.Incorrect)
        assertEquals(4, (wrongResult as PinVerificationResult.Incorrect).attemptsRemainingBeforeLockout)

        val correctResult = pinManager.verifyPin("7391")
        assertEquals(PinVerificationResult.Success, correctResult)
    }

    @Test
    fun `five consecutive wrong attempts trigger 30-second lockout`() {
        pinManager.setupPin("5555")

        // 4 failed attempts
        repeat(4) {
            val res = pinManager.verifyPin("1111")
            assertTrue(res is PinVerificationResult.Incorrect)
        }

        // 5th failed attempt triggers lockout
        val lockoutRes = pinManager.verifyPin("1111")
        assertTrue("5th failed attempt must trigger lockout", lockoutRes is PinVerificationResult.LockedOut)
        val seconds = (lockoutRes as PinVerificationResult.LockedOut).secondsRemaining
        assertTrue("Lockout duration should be roughly 30 seconds", seconds in 28..30)
    }

    @Test
    fun `changePin updates PIN after verifying old PIN`() {
        pinManager.setupPin("1234")

        // Fail to change with wrong old PIN
        val failedChange = pinManager.changePin("9999", "5678")
        assertFalse(failedChange)
        assertEquals(PinVerificationResult.Success, pinManager.verifyPin("1234"))

        // Successfully change with correct old PIN
        val successChange = pinManager.changePin("1234", "5678")
        assertTrue(successChange)
        assertEquals(PinVerificationResult.Success, pinManager.verifyPin("5678"))
    }

    @Test
    fun `salt uniqueness ensures identical PINs produce distinct cryptographic hashes`() {
        val salt1 = ByteArray(32) { 1 }
        val salt2 = ByteArray(32) { 2 }

        val hash1 = pinManager.hashPin("9876", salt1)
        val hash2 = pinManager.hashPin("9876", salt2)

        assertFalse("Different salts must produce different hashes", Arrays.equals(hash1, hash2))
    }
}
