package org.rsmod.content.minigames.pyramidplunder.pack

import dev.openrune.pack.PluginPack

class PyramidPlunderPluginPack : PluginPack() {
    override fun dbTables() = listOf(plunderRooms())
}
