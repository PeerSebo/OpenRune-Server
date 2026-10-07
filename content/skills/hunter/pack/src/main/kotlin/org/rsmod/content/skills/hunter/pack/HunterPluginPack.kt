package org.rsmod.content.skills.hunter.pack

import dev.openrune.pack.PluginPack

class HunterPluginPack : PluginPack() {
    override fun dbTables() = listOf(hunterFalconryTable(), hunterBirdhouseTable(), falconryRulesTable())
}
