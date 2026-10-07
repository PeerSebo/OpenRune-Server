package org.rsmod.content.skills.sailing.pack

import dev.openrune.pack.PluginPack

class SailingPluginPack : PluginPack() {
    override fun dbTables() = listOf(portRewardAnchors(), portBountyRewards(), portCourierRewards())
}
