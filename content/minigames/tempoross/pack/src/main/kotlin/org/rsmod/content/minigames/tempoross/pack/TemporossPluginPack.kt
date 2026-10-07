package org.rsmod.content.minigames.tempoross.pack

import dev.openrune.pack.PluginPack

class TemporossPluginPack : PluginPack() {
    override fun dbTables() = listOf(
        temporossFishBands(),
        temporossFishSlots(),
        temporossCasketRewards(),
    )
}
