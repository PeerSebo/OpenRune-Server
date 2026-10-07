package org.rsmod.api.farming

import dev.openrune.ServerCacheManager

/** Resolve cache properties lazily because patches are constructed before cache loading. */
public class FarmingPatchDef
internal constructor(
    public val key: FarmingPatchKey,
    public val type: FarmingPatchType,
    public val locName: String,
) {
    public val locId: Int? by lazy { FarmingTransformTable.locIdOrNull(locName) }

    /** Read the shared transmit slot from the loc's native multiVarBit. */
    public val transmitSlot: FarmingTransmitSlot? by lazy {
        val id = locId ?: return@lazy null
        val varbitId = ServerCacheManager.getObject(id)?.multiVarBit ?: return@lazy null
        FarmingTransmitSlot.ofVarbitId(varbitId)
    }

    public val transforms: FarmingTransformTable? by lazy {
        val id = locId ?: return@lazy null
        FarmingTransformTable.of(id)
    }

    override fun toString(): String = "FarmingPatchDef(key=$key, type=$type, loc=$locName)"
}
