package org.rsmod.content.minigames.mahoganyhomes.pack

import dev.openrune.pack.PluginPack

class MahoganyHomesPluginPack : PluginPack() {
    override fun dbTables() = listOf(mahoganyHomesTiers(), mahoganyHomesHouses(), mahoganyHomesRewards())
}
