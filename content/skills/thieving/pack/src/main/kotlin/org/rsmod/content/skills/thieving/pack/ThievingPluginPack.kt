package org.rsmod.content.skills.thieving.pack

import dev.openrune.pack.PluginPack

class ThievingPluginPack : PluginPack() {
    override fun dbTables() = listOf(thievingPickpockets())
}
