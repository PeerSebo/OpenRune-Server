package org.rsmod.content.events.shootingstars.pack

import dev.openrune.definition.dbtables.DBTable
import dev.openrune.pack.PluginPack

class ShootingStarPluginPack : PluginPack() {
    override fun dbTables(): List<DBTable> = listOf(ShootingStarsTable.locations())
}
