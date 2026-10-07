package org.rsmod.content.minigames.tearsofguthix.pack

import dev.openrune.pack.PluginPack

class TearsPluginPack : PluginPack() {
    override fun dbTables() = listOf(tearsRules(), tearsWalls(), tearsSkills())
}
