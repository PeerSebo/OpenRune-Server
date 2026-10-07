package org.rsmod.api.farming

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
public class FarmingPatchRegistry @Inject constructor() {
    private val logger = InlineLogger()

    private val byKey: LinkedHashMap<FarmingPatchKey, FarmingPatchDef> by lazy {
        FarmingWorldData.patches.associateTo(LinkedHashMap()) { row ->
            val key = FarmingPatchKey.ofLocName(row.locName)
            key to FarmingPatchDef(key, FarmingPatchType.valueOf(row.family), row.locName)
        }
    }

    @Volatile private var locIndex: Map<Int, FarmingPatchDef>? = null

    public fun register(locName: String, type: FarmingPatchType): FarmingPatchDef {
        val key = FarmingPatchKey.ofLocName(locName)
        val def = FarmingPatchDef(key, type, locName)
        byKey[key] = def
        locIndex = null
        return def
    }

    public fun all(): Collection<FarmingPatchDef> = byKey.values

    public fun byKey(key: FarmingPatchKey): FarmingPatchDef? = byKey[key]

    public fun byLoc(locId: Int): FarmingPatchDef? = index()[locId]

    public fun isPatchLoc(locId: Int): Boolean = locId in index()

    private fun index(): Map<Int, FarmingPatchDef> {
        val cached = locIndex
        if (cached != null) {
            return cached
        }
        val built = HashMap<Int, FarmingPatchDef>(byKey.size)
        for (def in byKey.values) {
            val locId = def.locId
            if (locId == null) {
                logger.error { "Farming patch loc symbol does not resolve: ${def.locName}" }
                continue
            }
            val clash = built.put(locId, def)
            if (clash != null) {
                logger.error {
                    "Two farming patches share loc id $locId: ${clash.key} and ${def.key}."
                }
            }
        }
        locIndex = built
        return built
    }
}
