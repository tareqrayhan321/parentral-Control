package com.example.core.sync

/**
 * Usage is stored as a map package -> minutes. Package names contain dots, so the keys are encoded
 * ('.' <-> '|', which can never appear in a package name) to keep them from ever being read as
 * Firestore field paths. Pure Kotlin, unit-tested.
 */
object UsageSerializer {
    fun toRemote(minutes: Map<String, Int>): Map<String, Long> =
        minutes.filterValues { it > 0 }.entries.associate { (pkg, min) -> pkg.replace('.', '|') to min.toLong() }

    fun fromRemote(data: Map<*, *>?): Map<String, Int> {
        if (data == null) return emptyMap()
        val out = HashMap<String, Int>()
        for ((k, v) in data) {
            val key = (k as? String)?.replace('|', '.') ?: continue
            val min = (v as? Number)?.toInt() ?: continue
            if (min > 0) out[key] = min
        }
        return out
    }
}
