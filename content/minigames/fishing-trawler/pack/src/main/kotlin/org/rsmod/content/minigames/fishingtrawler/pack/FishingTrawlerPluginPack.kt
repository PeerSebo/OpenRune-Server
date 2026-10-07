package org.rsmod.content.minigames.fishingtrawler.pack

import dev.openrune.pack.PluginPack

class FishingTrawlerPluginPack : PluginPack() {
    override fun dbTables() = listOf(trawlerCatch(), trawlerAnglerOutfit())
}
