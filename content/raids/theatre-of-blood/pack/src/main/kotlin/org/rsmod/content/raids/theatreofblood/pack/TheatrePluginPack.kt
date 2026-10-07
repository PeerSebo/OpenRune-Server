package org.rsmod.content.raids.theatreofblood.pack

import dev.openrune.cache.tools.tasks.CacheTask
import dev.openrune.pack.PluginPack

class TheatrePluginPack : PluginPack() {
    override fun extraTasks(): List<CacheTask> = listOf(TheatreHealingHitmark(), TheatreExitOrator())

    override fun dbTables() = listOf(
        theatreRules(), theatreRooms(), theatreSupplies(), theatreRewards(), theatreNylocasWaves(), theatreMechanics(),
    )
}
