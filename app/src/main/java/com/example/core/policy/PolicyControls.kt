package com.example.core.policy

/**
 * Device-wide controls synchronized from a parent to its linked child device.
 * Defaults match the existing DeviceOwnerManager preferences for a fresh install.
 */
data class PolicyControls(
    val supervised: Boolean = true,
    val cameraBlocked: Boolean = false,
    val installBlocked: Boolean = true,
    val mandatoryDns: Boolean = true,
    val dnsHost: String = DEFAULT_DNS_HOST,
    val lockdown: Boolean = false
) {
    companion object {
        const val DEFAULT_DNS_HOST = "family-filter-dns.cleanbrowsing.org"
    }
}
