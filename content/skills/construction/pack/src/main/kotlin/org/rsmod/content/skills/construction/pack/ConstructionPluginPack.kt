package org.rsmod.content.skills.construction.pack

import dev.openrune.pack.PluginPack

class ConstructionPluginPack : PluginPack() {
    override fun dbTables() = listOf(sawmillPlanks())
}
