package org.rsmod.content.minigames.tempoross.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.interfaces.collectionlog.CollectionLog
import org.rsmod.content.minigames.tempoross.TemporossRewards
import org.rsmod.content.minigames.tempoross.TemporossRewards.Reward
import org.rsmod.content.minigames.tempoross.TemporossStats
import org.rsmod.content.minigames.tempoross.configs.TemporossOutfitUnlocks
import org.rsmod.content.minigames.tempoross.configs.temporossBestPoints
import org.rsmod.content.minigames.tempoross.configs.temporossBestTicks
import org.rsmod.content.minigames.tempoross.configs.temporossRewardSearches
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class TemporossRewardScript : PluginScript() {
    override fun ScriptContext.startup() {
        onOpHeld1(TemporossRewards.CASKET) {
            if (!TemporossRewards.openCasket(player, random) { casketClue(player, random) }) {
                mes("You need more inventory space to open this casket.")
            }
        }
        onOpNpc1(POOL_WATCHER) { poolWatcher() }
        onOpNpc3(POOL_WATCHER) {
            if ("obj.net" in inv || "obj.big_net" in inv) mes("You already have a fishing net.")
            else if (!invAdd(inv, "obj.net", 1).success) mes("You need room for a fishing net.")
            else mes("The Spirit Angler gives you a small fishing net.")
        }
        onOpNpc4(POOL_WATCHER) { buyMenu() }
        onOpNpc1(GITA) {
            val part = choice5("Headband", "hat", "Top", "top", "Waders", "legs", "Boots", "boots", "Cancel", "")
            if (part.isNotEmpty()) {
                val ordinary = "obj.trawler_reward_$part"
                val spirit = "obj.spirit_angler_$part"
                convertOutfit(part, if (ordinary in inv) ordinary else spirit)
            }
        }
        for (part in outfitParts) {
            for (item in listOf("obj.trawler_reward_$part", "obj.spirit_angler_$part")) {
                onOpNpcU(GITA, item) { convertOutfit(part, item) }
            }
        }
        onOpNpcU(POOL_WATCHER, "obj.tome_of_water_uncharged") { exchangeTome() }
        onOpLoc1("loc.tempoross_lobby_stats_board") { showStats() }
        onIfModalButton("component.tempoross_stats:close_button") { ifClose() }
    }

    private suspend fun ProtectedAccess.poolWatcher() {
        when (choice4("Browse Flakes 'n' Flotsam", 1, "Exchange an empty Tome of Water", 2, "Check my records", 3, "Cancel", 0)) {
            1 -> buyMenu()
            2 -> exchangeTome()
            3 -> showStats()
        }
    }

    private suspend fun ProtectedAccess.buyMenu() {
        val item = choice3("Fish barrel - 6,000 spirit flakes", TemporossRewards.BARREL,
            "Tackle box - 6,000 spirit flakes", TemporossRewards.TACKLE_BOX, "Cancel", "",
            title = "Flakes 'n' Flotsam")
        if (item.isEmpty()) return
        if (TemporossRewards.ownsUnique(player, item)) {
            mes("You already own one of those.")
            return
        }
        if (!purchase(player, item)) {
            mes("You need 6,000 spirit flakes and room for your purchase.")
            return
        }
        mes("You exchange 6,000 spirit flakes for your purchase.")
    }

    private suspend fun ProtectedAccess.convertOutfit(part: String, source: String) {
        if (source !in inv) { mes("Bring me the outfit piece you want to convert."); return }
        val toSpirit = source.startsWith("obj.trawler_reward_")
        val cost = if (toSpirit && !TemporossOutfitUnlocks.has(player, part)) 1200 else 0
        if (!choice2("Convert${if (cost > 0) " for 1,200 spirit flakes" else " for free"}", true, "Cancel", false)) return
        if (!convert(player, part, source)) {
            mes("You need the outfit piece, enough spirit flakes and room for the conversion.")
            return
        }
        mes("Gita converts your outfit piece.")
    }

    private suspend fun ProtectedAccess.exchangeTome() {
        if (!choice2("Exchange an empty Tome of Water for 25 soaked pages", true, "Cancel", false)) return
        if (!exchange(player, "obj.tome_of_water_uncharged", 1, TemporossRewards.PAGES, 25)) {
            mes("You need an empty Tome of Water and room for the pages.")
            return
        }
        mes("You receive 25 soaked pages.")
    }

    private fun ProtectedAccess.showStats() {
        ifOpenMain("interface.tempoross_stats")
        ifSetText("component.tempoross_stats:text_mybestpoints_data", player.temporossBestPoints.toString())
        ifSetText("component.tempoross_stats:text_mytotal_data", player.vars[TemporossStats.KILLS].toString())
        ifSetText("component.tempoross_stats:text_myfastest_data", TemporossStats.time(player.temporossBestTicks))
        ifSetText("component.tempoross_stats:text_myrewardsused_used", player.temporossRewardSearches.toString())
        ifSetText("component.tempoross_stats:text_globaltotal_data", "-")
        ifSetText("component.tempoross_stats:text_globalfastest_data", "-")
    }

    companion object {
        private const val POOL_WATCHER = "npc.tempoross_lobby_pool_watcher"
        private const val GITA = "npc.tempoross_lobby_outfit_vendor"
        private val outfitParts = TemporossOutfitUnlocks.parts
        private val clues by lazy {
            listOf("easy", "medium", "hard").associateWith { tier ->
                ServerCacheManager.getItems().values.filter { it.name == "Clue scroll ($tier)" && it.certtemplate == 0 && !it.isPlaceholder }.map { it.internalName }
            }
        }

        fun purchase(player: Player, item: String): Boolean {
            if (item !in listOf(TemporossRewards.BARREL, TemporossRewards.TACKLE_BOX)) return false
            if (TemporossRewards.ownsUnique(player, item)) return false
            if (!exchange(player, TemporossRewards.FLAKES, 6000, item, 1)) return false
            CollectionLog.grant(player, item)
            return true
        }

        fun convert(player: Player, part: String, source: String): Boolean {
            if (part !in outfitParts) return false
            val ordinary = "obj.trawler_reward_$part"
            val spirit = "obj.spirit_angler_$part"
            if (source != ordinary && source != spirit) return false
            val toSpirit = source == ordinary
            val cost = if (toSpirit && !TemporossOutfitUnlocks.has(player, part)) 1200 else 0
            val result = player.invTransaction(player.inv) {
                val inv = select(player.inv)
                delete(inv, source.asRSCM(), 1)
                if (cost > 0) delete(inv, TemporossRewards.FLAKES.asRSCM(), cost)
                add(inv, (if (toSpirit) spirit else ordinary).asRSCM(), 1)
            }
            if (!result.success) return false
            TemporossOutfitUnlocks.unlock(player, part)
            return true
        }

        fun exchange(player: Player, source: String, count: Int, reward: String, rewardCount: Int): Boolean =
            player.invTransaction(player.inv) {
                val inv = select(player.inv)
                delete(inv, source.asRSCM(), count)
                add(inv, reward.asRSCM(), rewardCount)
            }.success

        fun casketClue(player: Player, random: GameRandom): Reward? {
            val tier = when (random.of(100)) { in 0..4 -> "easy"; in 5..7 -> "medium"; in 8..9 -> "hard"; else -> null }
                ?: return bonusClue(player, random)
            return clueFor(player, random, tier)
        }

        private fun bonusClue(player: Player, random: GameRandom): Reward? {
            // The empty outcome supplies only the additional Combat Achievement marginal probability.
            val extra = listOf("easy" to (1.0 / 19 - .05), "medium" to (1.0 / 31 - .03), "hard" to (1.0 / 47 - .02))
            var value = random.randomDouble() * .9
            for ((tier, probability) in extra) {
                if (player.vars["varbit.ca_lamp_claimed_$tier"] == 0) continue
                if (value < probability) return clueFor(player, random, tier)
                value -= probability
            }
            return null
        }

        private fun clueFor(player: Player, random: GameRandom, tier: String): Reward? {
            if (TemporossRewards.hasStackableClues(player)) return Reward("obj.league_clue_box_$tier")
            val candidates = clues.getValue(tier)
            if (candidates.isEmpty()) return null
            if (TemporossRewards.hasClue(player, tier)) return null
            return Reward(candidates[random.of(candidates.size)])
        }
    }
}
