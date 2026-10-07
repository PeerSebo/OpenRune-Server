package org.rsmod.content.minigames.giantsfoundry.pack

import dev.openrune.pack.PluginPack

class FoundryPluginPack : PluginPack() {
    override fun dbTables() = listOf(foundryNativeCatalogue(), foundryMetals(), foundryOrePack(), foundryTemperature(),
        sleepingGiantsRepairs(), sleepingGiantsSupplies(), sleepingGiantsCamera(), sleepingGiantsScenery())
}
