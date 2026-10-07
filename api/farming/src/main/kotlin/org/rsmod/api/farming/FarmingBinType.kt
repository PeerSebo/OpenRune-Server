package org.rsmod.api.farming

/**
 * Families have different loc naming and transform layouts; the big bin has split 30-item
 * bands.
 */
public enum class FarmingBinType {
    STANDARD,
    MORYTANIA,
    FARMING_GUILD;

    private val definition: FarmingWorldData.BinType
        get() = FarmingWorldData.binTypes.getValue(name)

    public val locPrefix: String
        get() = definition.locPrefix

    public val capacity: Int
        get() = definition.capacity

    public val emptyLocName: String
        get() = locPrefix + "empty"

    public fun fillLocName(fill: FarmingBinFill, count: Int): String? {
        val band = fill.locBand ?: return null
        return countedLocName(band, count)
    }

    public fun productLocName(product: FarmingBinProduct, count: Int): String? =
        countedLocName(product.locBand, count)

    public fun rottingLocName(product: FarmingBinProduct): String? =
        if (product.hasRotStates) "$locPrefix${product.locBand}_rotting" else null

    public fun readyLocName(product: FarmingBinProduct): String? =
        if (product.hasRotStates) "$locPrefix${product.locBand}_ready" else null

    /** Counts outside the family's capacity have no cache state. */
    private fun countedLocName(band: String, count: Int): String? {
        if (count !in 1..capacity) {
            return null
        }
        return "$locPrefix${band}_${count.toString().padStart(2, '0')}"
    }
}
