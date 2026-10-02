package com.example.core.model

/**
 * Standard, verified parental and family-safe DNS-over-TLS (DoT) providers.
 */
data class DnsProvider(
    val id: String,
    val name: String,
    val host: String,
    val description: String,
    val blocksAdult: Boolean = true,
    val blocksMalware: Boolean = true,
    val safeSearch: Boolean = true
) {
    companion object {
        val CLEANBROWSING_FAMILY = DnsProvider(
            id = "cleanbrowsing",
            name = "CleanBrowsing Family Filter",
            host = "family-filter-dns.cleanbrowsing.org",
            description = "Blocks adult content, phishing, proxy/VPN bypass sites, and enforces SafeSearch on Google, Bing, and YouTube.",
            blocksAdult = true,
            blocksMalware = true,
            safeSearch = true
        )

        val CLOUDFLARE_FAMILY = DnsProvider(
            id = "cloudflare_family",
            name = "Cloudflare 1.1.1.1 for Families",
            host = "family.cloudflare-dns.com",
            description = "High-speed Anycast DNS blocking malicious websites and pornography at the network layer.",
            blocksAdult = true,
            blocksMalware = true,
            safeSearch = false
        )

        val ADGUARD_FAMILY = DnsProvider(
            id = "adguard_family",
            name = "AdGuard Family Protection",
            host = "family.adguard-dns.com",
            description = "Filters adult websites, aggressive ads, trackers, and malicious domains across all apps.",
            blocksAdult = true,
            blocksMalware = true,
            safeSearch = true
        )

        val OPENDNS_FAMILY = DnsProvider(
            id = "opendns_family",
            name = "Cisco OpenDNS FamilyShield",
            host = "family-filter.opendns.com",
            description = "Automatically blocks adult content domains without configuration.",
            blocksAdult = true,
            blocksMalware = true,
            safeSearch = false
        )

        val ALL_PROVIDERS = listOf(
            CLEANBROWSING_FAMILY,
            CLOUDFLARE_FAMILY,
            ADGUARD_FAMILY,
            OPENDNS_FAMILY
        )

        fun findById(id: String): DnsProvider =
            ALL_PROVIDERS.firstOrNull { it.id == id } ?: CLEANBROWSING_FAMILY
    }
}
