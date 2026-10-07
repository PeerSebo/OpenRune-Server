package org.rsmod.api.farming

public class FarmingPatches {
    private val states = LinkedHashMap<FarmingPatchKey, FarmingPatchState>()

    public fun getOrNull(key: FarmingPatchKey): FarmingPatchState? = states[key]

    public fun getOrCreate(key: FarmingPatchKey): FarmingPatchState =
        states.getOrPut(key) { FarmingPatchState() }

    public fun put(key: FarmingPatchKey, state: FarmingPatchState) {
        states[key] = state
    }

    public fun asMap(): Map<FarmingPatchKey, FarmingPatchState> = states

    public val size: Int
        get() = states.size
}
