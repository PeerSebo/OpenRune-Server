package org.rsmod.content.minigames.wintertodt.pack

import dev.openrune.pack.PluginPack

class WintertodtPluginPack : PluginPack() {
    override fun dbTables() = listOf(winterRules(), winterActions(), winterCamps(), winterRewards(), winterWarmItems(), winterUniques())
}
