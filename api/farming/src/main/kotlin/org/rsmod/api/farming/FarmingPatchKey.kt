package org.rsmod.api.farming

/**
 * Persistent patch identity is its world loc key. Distant patches share transmit varbits and
 * must not share saved state.
 */
@JvmInline
public value class FarmingPatchKey(public val id: String) {
    override fun toString(): String = id

    public companion object {
        private const val LOC_PREFIX: String = "loc."

        public fun ofLocName(locName: String): FarmingPatchKey =
            FarmingPatchKey(locName.removePrefix(LOC_PREFIX))
    }
}
