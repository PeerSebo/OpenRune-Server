package org.rsmod.content.minigames.gnomeball.pack

import dev.openrune.pack.PluginPack

class GnomeballPluginPack : PluginPack() {
    override fun dbTables() = listOf(gnomeballRules(), gnomeballPlayers())
}
