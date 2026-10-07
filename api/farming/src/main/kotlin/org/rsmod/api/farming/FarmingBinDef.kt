package org.rsmod.api.farming

import dev.openrune.ServerCacheManager

/** Identify a physical bin by its stable key, not its shared transmit varbit. */
public class FarmingBinDef
internal constructor(
    public val key: FarmingBinKey,
    public val type: FarmingBinType,
    public val locName: String,
) {
    public val capacity: Int
        get() = type.capacity

    public val locId: Int? by lazy { FarmingTransformTable.locIdOrNull(locName) }

    public val transmitSlot: FarmingTransmitSlot? by lazy {
        val id = locId ?: return@lazy null
        val varbitId = ServerCacheManager.getObject(id)?.multiVarBit ?: return@lazy null
        FarmingTransmitSlot.ofVarbitId(varbitId)
    }

    public val transforms: FarmingTransformTable? by lazy {
        val id = locId ?: return@lazy null
        FarmingTransformTable.of(id)
    }

    override fun toString(): String = "FarmingBinDef(key=$key, type=$type, loc=$locName)"
}
