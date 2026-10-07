package org.rsmod.content.minigames.mahoganyhomes.configs

import org.rsmod.content.minigames.mahoganyhomes.core.MahoganyHomesHotspotKind
import org.rsmod.content.minigames.mahoganyhomes.core.MahoganyHomesHotspots

public data class MahoganyHomesLayoutSlot(
    public val hotspotIndex: Int,
    public val locName: String,
    public val kind: MahoganyHomesHotspotKind,
)

public object MahoganyHomesLayouts {

    public fun layoutFor(house: MahoganyHomesHouse): List<MahoganyHomesLayoutSlot> =
        house.hotspots.mapIndexedNotNull { index, locName ->
            val def = MahoganyHomesHotspots.get(locName) ?: return@mapIndexedNotNull null
            val kind = def.kind ?: return@mapIndexedNotNull null
            MahoganyHomesLayoutSlot(hotspotIndex = index + 1, locName = locName, kind = kind)
        }
}
