package org.rsmod.content.minigames.sorceressgarden.pack

import dev.openrune.pack.PluginPack

class SorceressGardenPluginPack : PluginPack() {
    override fun dbTables() = listOf(gardenSeasons(), gardenPatrols())
}
