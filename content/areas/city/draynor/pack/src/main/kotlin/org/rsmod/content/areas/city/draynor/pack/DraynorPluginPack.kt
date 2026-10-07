package org.rsmod.content.areas.city.draynor.pack

import dev.openrune.pack.PluginPack

class DraynorPluginPack : PluginPack() {
    override fun dbTables() = listOf(avaDevices(), diangoToys(), diangoScarfStyles(), diangoHandeggs(), draynorBankers(), draynorSewerRoutes())
}
