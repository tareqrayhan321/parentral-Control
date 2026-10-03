package com.example.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UsageSerializerTest {
    private val usage = mapOf("com.google.android.youtube" to 45, "com.example.game" to 12, "zero.app" to 0)

    @Test fun `encoded keys contain no dots`() {
        val remote = UsageSerializer.toRemote(usage)
        assertFalse(remote.keys.any { it.contains('.') })
        assertFalse(remote.containsKey("zero|app"))   // zero minutes are not uploaded
    }

    @Test fun `round trip restores package names and minutes`() {
        val back = UsageSerializer.fromRemote(UsageSerializer.toRemote(usage))
        assertEquals(mapOf("com.google.android.youtube" to 45, "com.example.game" to 12), back)
    }

    @Test fun `Firestore returns Long values and still parses`() =
        assertEquals(mapOf("a.b" to 3), UsageSerializer.fromRemote(mapOf("a|b" to 3L)))

    @Test fun `null and junk are ignored`() {
        assertEquals(emptyMap<String, Int>(), UsageSerializer.fromRemote(null))
        assertEquals(emptyMap<String, Int>(), UsageSerializer.fromRemote(mapOf("x" to "oops", 5 to 3, "y" to -2)))
    }
}
