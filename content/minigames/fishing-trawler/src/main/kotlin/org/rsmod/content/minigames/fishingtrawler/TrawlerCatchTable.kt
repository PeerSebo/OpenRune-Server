package org.rsmod.content.minigames.fishingtrawler

object TrawlerCatchTable {

    data class Fish(val item: String, val reqLevel: Int, val xpTenths: Int)

    val CASCADE: List<Fish> get() = TrawlerRewardData.fish

    const val JUNK_XP_TENTHS = 5

    const val SUCCESS_LOW = 150
    const val SUCCESS_HIGH = 200
    const val MAX_LEVEL = 99
}
