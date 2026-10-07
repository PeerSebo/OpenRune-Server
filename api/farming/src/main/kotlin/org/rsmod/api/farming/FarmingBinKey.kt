package org.rsmod.api.farming

/**
 * Bin keys identify world locations; transmit varbits are shared between distant bins and
 * cannot serve as persistent identity.
 */
@JvmInline
public value class FarmingBinKey(public val id: String) {
    override fun toString(): String = id

    public companion object {
        private const val LOC_PREFIX: String = "loc."

        public fun ofLocName(locName: String): FarmingBinKey =
            FarmingBinKey(locName.removePrefix(LOC_PREFIX))
    }
}

public class FarmingBins {
    private val states = LinkedHashMap<FarmingBinKey, FarmingBinState>()

    public fun getOrNull(key: FarmingBinKey): FarmingBinState? = states[key]

    public fun getOrCreate(key: FarmingBinKey): FarmingBinState =
        states.getOrPut(key) { FarmingBinState() }

    public fun put(key: FarmingBinKey, state: FarmingBinState) {
        states[key] = state
    }

    public fun asMap(): Map<FarmingBinKey, FarmingBinState> = states

    public val size: Int
        get() = states.size
}
