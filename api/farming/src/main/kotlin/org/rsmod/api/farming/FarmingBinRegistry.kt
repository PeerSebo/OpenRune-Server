package org.rsmod.api.farming

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Transmit slots are shared by distant bins. Identity must come from the base loc key, and
 * rendering must resolve each family's actual transform table.
 */
@Singleton
public class FarmingBinRegistry @Inject constructor() {
    private val logger = InlineLogger()

    private val byKey: LinkedHashMap<FarmingBinKey, FarmingBinDef> by lazy {
        FarmingWorldData.bins.associateTo(LinkedHashMap()) { row ->
            val key = FarmingBinKey.ofLocName(row.locName)
            key to FarmingBinDef(key, FarmingBinType.valueOf(row.family), row.locName)
        }
    }

    @Volatile private var locIndex: Map<Int, FarmingBinDef>? = null

    public fun register(locName: String, type: FarmingBinType): FarmingBinDef {
        val key = FarmingBinKey.ofLocName(locName)
        val def = FarmingBinDef(key, type, locName)
        byKey[key] = def
        locIndex = null
        return def
    }

    public fun all(): Collection<FarmingBinDef> = byKey.values

    public fun byKey(key: FarmingBinKey): FarmingBinDef? = byKey[key]

    public fun byLoc(locId: Int): FarmingBinDef? = index()[locId]

    public fun isBinLoc(locId: Int): Boolean = locId in index()

    /**
     * Validate that each family's named states actually occur in this bin's transform table; a
     * valid loc symbol alone is insufficient.
     */
    public fun validate() {
        var healthy = 0
        for (def in byKey.values) {
            if (validate(def)) {
                healthy++
            }
        }
        logger.debug { "Validated $healthy/${byKey.size} compost bin(s) against the cache." }
    }

    private fun validate(def: FarmingBinDef): Boolean {
        val locId = def.locId
        if (locId == null) {
            logger.error { "Compost bin loc symbol does not resolve: ${def.locName}" }
            return false
        }
        if (def.transmitSlot == null) {
            logger.error { "Compost bin ${def.key} has no recognised transmit varbit." }
            return false
        }
        val table = def.transforms
        if (table == null) {
            logger.error { "Compost bin ${def.key} (${def.locName}) is not a multiloc." }
            return false
        }

        val emptyId = FarmingTransformTable.locIdOrNull(def.type.emptyLocName)
        val emptyIndex = emptyId?.let(table::indexOf) ?: -1
        if (emptyIndex != FarmingConstants.BIN_EMPTY_TRANSFORM_INDEX) {
            logger.error {
                "Compost bin ${def.key} is declared ${def.type} but ${def.type.emptyLocName} " +
                    "resolves to index $emptyIndex in its table, not " +
                    "${FarmingConstants.BIN_EMPTY_TRANSFORM_INDEX}. Wrong family?"
            }
            return false
        }

        val missing = unplaceableStates(def, table)
        if (missing.isEmpty()) {
            return true
        }
        logger.error {
            "Compost bin ${def.key} (${def.type}) cannot draw ${missing.size} state(s); " +
                "first: ${missing.take(MAX_REPORTED_STATES).joinToString()}"
        }
        return false
    }

    private fun unplaceableStates(def: FarmingBinDef, table: FarmingTransformTable): List<String> {
        val names = buildList {
            for (fill in FarmingBinFill.entries) {
                for (count in 1..def.capacity) {
                    def.type.fillLocName(fill, count)?.let(::add)
                }
            }
            for (product in FarmingBinProduct.entries) {
                for (count in 1..def.capacity) {
                    def.type.productLocName(product, count)?.let(::add)
                }
                def.type.rottingLocName(product)?.let(::add)
                def.type.readyLocName(product)?.let(::add)
            }
        }
        return names.filter { name ->
            val id = FarmingTransformTable.locIdOrNull(name)
            id == null || table.indexOf(id) < 0
        }
    }

    private fun index(): Map<Int, FarmingBinDef> {
        val cached = locIndex
        if (cached != null) {
            return cached
        }
        val built = HashMap<Int, FarmingBinDef>(byKey.size)
        for (def in byKey.values) {
            val locId = def.locId
            if (locId == null) {
                logger.error { "Compost bin loc symbol does not resolve: ${def.locName}" }
                continue
            }
            val clash = built.put(locId, def)
            if (clash != null) {
                logger.error {
                    "Two compost bins share loc id $locId: ${clash.key} and ${def.key}."
                }
            }
        }
        locIndex = built
        return built
    }

    private companion object {
        private const val MAX_REPORTED_STATES: Int = 5
    }
}
