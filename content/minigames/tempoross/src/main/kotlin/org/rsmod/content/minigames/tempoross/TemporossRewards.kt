package org.rsmod.content.minigames.tempoross

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.output.mes
import org.rsmod.api.random.GameRandom
import org.rsmod.content.interfaces.collectionlog.CollectionLog
import org.rsmod.content.interfaces.collectionlog.collectionTransmit
import org.rsmod.content.minigames.tempoross.configs.TemporossRewardData
import org.rsmod.content.minigames.tempoross.configs.temporossRewardSearches
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.game.entity.Player

object TemporossRewards {
    internal fun hasStackableClues(player: Player): Boolean =
        player.vars["varbit.cluequest"] >= 8 || QuestRequirements.hasCompleted(player, "quest_xmarksthespot")

    internal fun hasClue(player: Player, tier: String): Boolean {
        val names = listOf("Clue scroll", "Challenge scroll", "Clue casket", "Clue nest", "Clue bottle", "Clue geode").map { "$it ($tier)" }.toSet()
        val collection = player.collectionTransmit
        return (sequenceOf(player.inv) + player.invMap.values.asSequence().filter { it !== collection }).any { inventory ->
            inventory.objs.any { item -> item != null && item.count > 0 &&
                ServerCacheManager.getItem(item.id)?.let { !it.isPlaceholder && it.name in names } == true }
        }
    }

    const val FLAKES = "obj.spirit_flakes"
    const val CASKET = "obj.tempoross_reward_casket"
    const val BARREL = "obj.fish_barrel_closed"
    const val TACKLE_BOX = "obj.tackle_box"
    const val PAGES = "obj.soaked_page"
    const val PET = "obj.temporosspet"
    private val pendingPool = AttributeKey<String>(persistenceKey = "tempoross_pending_pool_reward")
    private val pendingCasket = AttributeKey<String>(persistenceKey = "tempoross_pending_casket_reward")

    data class Reward(val obj: String, val count: Int = 1, val noted: Boolean = false)
    data class Entry(val obj: String, val quantity: IntRange, val weight: Int, val noted: Boolean = true) {
        fun roll(random: GameRandom) = Reward(obj, random.of(quantity.first, quantity.last), noted)
    }

    fun fishBand(level: Int): List<String> {
        val bands = TemporossRewardData.fishBands
        return (bands.lastOrNull { level >= it.level } ?: bands.first()).fish
    }

    // Common weights are approximate; exclusive rare-before-common allocation is inferred.
    fun rollPool(level: Int, random: GameRandom, owns: (String) -> Boolean): Reward {
        when (random.of(8000)) {
            0 -> return Reward(PET)
            1 -> return Reward("obj.dragon_harpoon")
            in 2..6 -> return Reward("obj.poh_trophydrop_harpoonfish")
            in 7..11 -> return Reward("obj.tome_of_water_uncharged")
            in 12..31 -> return if (owns(BARREL)) Reward(PAGES, 25) else Reward(BARREL)
            in 32..51 -> return if (owns(TACKLE_BOX)) Reward(PAGES, 25) else Reward(TACKLE_BOX)
            in 52..200 -> return Reward(PAGES, random.of(5, 9))
        }
        return when (val roll = random.of(160)) {
            in 0..89 -> {
                val slots = TemporossRewardData.fishSlots
                var remaining = random.of(slots.sumOf { it.weight })
                val index = slots.indexOfFirst { slot ->
                    if (remaining < slot.weight) true else { remaining -= slot.weight; false }
                }
                val quantity = slots[index].quantity
                Reward(fishBand(level)[index], random.of(quantity.first, quantity.last), noted = true)
            }
            in 90..129 -> Reward(FLAKES, random.of(32, 64))
            in 130..137 -> Reward(CASKET)
            in 138..144 -> Reward("obj.woodplank", random.of(20, 30), noted = true)
            in 145..149 -> Reward("obj.plank_oak", random.of(15, 25), noted = true)
            in 150..153 -> Reward("obj.seaweed", random.of(20, 60), noted = true)
            in 154..155 -> Reward("obj.nails", random.of(300, 500))
            in 156..157 -> Reward("obj.feather", random.of(800, 1600))
            else -> { check(roll in 158..159); Reward("obj.fishing_bait", random.of(800, 1600)) }
        }
    }

    val casketTable: List<Entry> get() = TemporossRewardData.casketRewards

    fun rollCasket(random: GameRandom): Reward {
        var roll = random.of(casketTable.sumOf { it.weight })
        for (entry in casketTable) {
            if (roll < entry.weight) return entry.roll(random)
            roll -= entry.weight
        }
        error("Invalid casket roll")
    }

    fun ownsUnique(player: Player, obj: String): Boolean {
        val variants = if (obj == BARREL) listOf(BARREL, "obj.fish_barrel_open", "obj.fish_sack_barrel_closed", "obj.fish_sack_barrel_open") else listOf(obj)
        val collection = player.collectionTransmit
        val inventories = sequenceOf(player.inv, player.worn) + player.invMap.values.asSequence().filter { it !== collection }
        return inventories.any { inventory -> variants.any { inventory.count(it) > 0 } }
    }

    fun collect(player: Player, level: Int, random: GameRandom): Boolean {
        val reward = pending(player, pendingPool) { listOf(rollPool(level, random) { ownsUnique(player, it) }) }.map {
            // Recheck ownership while a rolled reward waits for inventory space.
            if (it.obj in listOf(BARREL, TACKLE_BOX) && ownsUnique(player, it.obj)) Reward(PAGES, 25) else it
        }
        if (!give(player, reward)) return false
        player.attr.remove(pendingPool)
        player.temporossRewardSearches = (player.temporossRewardSearches.toLong() + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return true
    }

    fun openCasket(player: Player, random: GameRandom, clue: () -> Reward?): Boolean {
        if (player.inv.count(CASKET) == 0) return false
        val rewards = pending(player, pendingCasket) { listOfNotNull(rollCasket(random), clue()) }.mapNotNull { pendingClue(player, it) }
        if (!give(player, rewards, consume = CASKET)) return false
        player.attr.remove(pendingCasket)
        return true
    }

    // Recheck pending clue uniqueness after the player makes room.
    private fun pendingClue(player: Player, reward: Reward): Reward? {
        val name = ServerCacheManager.getItem(reward.obj.asRSCM())?.name ?: return reward
        val tier = listOf("easy", "medium", "hard").firstOrNull { name == "Clue scroll ($it)" } ?: return reward
        if (hasStackableClues(player)) return Reward("obj.league_clue_box_$tier")
        if (hasClue(player, tier)) return null
        return reward
    }

    private fun pending(player: Player, key: AttributeKey<String>, roll: () -> List<Reward>): List<Reward> {
        val stored = player.attr[key]
        if (stored != null) return stored.split(';').map { part ->
            val fields = part.split(',')
            Reward(fields[0], fields[1].toInt(), fields[2].toBoolean())
        }
        val rewards = roll()
        player.attr[key] = rewards.joinToString(";") { "${it.obj},${it.count},${it.noted}" }
        return rewards
    }

    // Spend only when every reward fits; failures retain the original roll.
    private fun give(player: Player, rewards: List<Reward>, consume: String? = null): Boolean {
        val result = player.invTransaction(player.inv) {
            val inventory = select(player.inv)
            if (consume != null) delete(inventory, consume.asRSCM(), 1)
            for (reward in rewards) add(inventory, reward.obj.asRSCM(), reward.count, cert = reward.noted)
        }
        if (!result.success) return false
        for (reward in rewards) {
            CollectionLog.grant(player, reward.obj, reward.count)
            if (reward.obj == PET) org.rsmod.api.player.vars.VarPlayerIntMapSetter.set(player, "varbit.pet_insurance_tempoross", 1)
            val name = checkNotNull(ServerCacheManager.getItem(reward.obj.asRSCM())).name
            player.mes("Your reward is: ${reward.count} x $name.")
        }
        return true
    }
}
