package org.rsmod.content.skills.thieving.configs

object ThievingStallGuards {
    private val ARDOUGNE_RESPONDERS: List<String> =
        listOf("npc.guard1", "npc.knight_of_ardougne", "npc.paladin", "npc.hero")

    private val byStall: Map<String, List<String>> =
        mapOf(
            "loc.cakethiefstall" to ARDOUGNE_RESPONDERS,
            "loc.silkthiefstall" to ARDOUGNE_RESPONDERS,
            "loc.furthiefstall" to ARDOUGNE_RESPONDERS,
            "loc.spicethiefstall" to ARDOUGNE_RESPONDERS,
            "loc.silverthiefstall" to ARDOUGNE_RESPONDERS,
            "loc.gemthiefstall" to ARDOUGNE_RESPONDERS,
            "loc.rag_market_stall" to listOf("npc.farming_market_guard"),
            "loc.seed_stall" to listOf("npc.farming_market_guard"),
            "loc.viking_fish_market" to listOf("npc.viking_guard"),
            "loc.viking_fur_market" to listOf("npc.viking_guard"),
            "loc.fish_stall_warrens" to listOf("npc.warrens_thief_stall"),
            "loc.hos_fruit_stall_02" to listOf("npc.hosidius_guarddog"),
        )

    fun forStall(loc: String): List<String> = byStall[loc].orEmpty()
}
