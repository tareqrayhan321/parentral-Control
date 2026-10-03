package com.example.core.policy

import com.example.core.model.AppPolicy
import com.example.core.model.Policy
import com.example.core.model.RestrictionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyControlsTest {
    private val original = listOf(
        AppPolicy("a.blocked", "Blocked", RestrictionMode.BLOCKED),
        AppPolicy("b.limited", "Limited", RestrictionMode.LIMITED, 30),
        AppPolicy("c.allowed", "Allowed", RestrictionMode.ALLOWED),
        AppPolicy("d.disabled", "Disabled", RestrictionMode.ALLOWED, enabled = false)
    ).associateBy { it.packageName }
    private val policy = Policy(version = 5, updatedAtEpochMs = 1L, apps = original)

    @Test fun `lockdown off returns the same policy`() = assertSame(policy, policy.withLockdown(false))

    @Test fun `lockdown on blocks every tracked app including disabled ones`() {
        val locked = policy.withLockdown(true)
        assertTrue(locked.apps.values.all { it.mode == RestrictionMode.BLOCKED && it.enabled })
        assertEquals(original.keys, locked.apps.keys)
    }

    @Test fun `lockdown never changes the stored rules, so release restores them exactly`() {
        policy.withLockdown(true)
        assertEquals(original, policy.apps)
        assertEquals(RestrictionMode.BLOCKED, policy.apps["a.blocked"]?.mode)   // old bug: this became ALLOWED
        assertEquals(RestrictionMode.LIMITED, policy.apps["b.limited"]?.mode)
    }
}
