package com.example.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PairingQrTest {
    private val token = "a".repeat(64)

    @Test fun `round trip`() {
        val qr = PairingQr(token, "parentUid1", "Abbu")
        assertEquals(qr, PairingQr.fromJson(qr.toJson()))
    }

    @Test fun `short token is rejected`() =
        assertNull(PairingQr.fromJson(PairingQr("short", "p", "x").toJson()))

    @Test fun `old v1 payload is rejected`() =
        assertNull(PairingQr.fromJson("""{"token":"$token","parentId":"p","sig":"s"}"""))

    @Test fun `garbage is rejected`() = assertNull(PairingQr.fromJson("not json"))

    @Test fun `blank parent uid is rejected`() =
        assertNull(PairingQr.fromJson(PairingQr(token, " ", "x").toJson()))
}
