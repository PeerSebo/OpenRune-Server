package org.rsmod.content.minigames.wintertodt

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.random.GameRandom
import org.rsmod.content.interfaces.collectionlog.CollectionLog
import org.rsmod.content.interfaces.collectionlog.collectionTransmit
import org.rsmod.game.entity.Player

internal data class WinterReward(val obj: String, val count: Int = 1, val noted: Boolean = false)

internal object WintertodtRewards {
    fun rolls(points: Int, random: GameRandom): Int {
        val threshold = WintertodtData.rules.minimumPoints
        if (points < threshold) return 0
        return 1 + points / threshold + if (random.of(threshold) < points % threshold) 1 else 0
    }
    fun roll(player: Player, random: GameRandom, extraCrate: Boolean = false): WinterReward {
        for ((index, rare) in WintertodtData.uniques.withIndex()) {
            if (extraCrate && index != 1) continue
            if (random.of(rare.chance) != 0) continue
            val item = rare.items.minBy { owned(player, it) }
            val capped = rare.cap > 0 && owned(player, item) >= rare.cap
            val range = if (capped) rare.replacementQuantity else rare.quantity
            return WinterReward(if (capped) rare.replacement else item, random.of(range.first, range.last))
        }
        val category = when (random.of(25)) {
            in 0..2 -> 0; in 3..5 -> 1; in 6..8 -> 2; in 9..11 -> 3
            in 12..14 -> 4; in 15..17 -> 5; in 18..22 -> 6; 23 -> 7; else -> 8
        }
        val candidates = WintertodtData.rewards.filter {
            it.category == category && player.statBase(it.stat) >= it.level
        }
        check(candidates.isNotEmpty()) { "No Wintertodt rewards for category $category" }
        val level = player.statBase(candidates.first().stat).coerceIn(1, 99)
        val quality = (random.of(100) + random.of(100) + level - 1).coerceAtMost(198)
        val item = candidates[minOf(candidates.lastIndex, quality * candidates.size / 199)]
        return WinterReward(item.obj, random.of(item.quantity.first, item.quantity.last),
            noted = item.obj != "obj.coins")
    }
    fun collect(player: Player, random: GameRandom): Boolean {
        if (player.vars[WintertodtState.RewardPool] <= 0) return false
        val pending = player.vars[WintertodtState.PendingObj]
        val reward = if (pending != 0) {
            WinterReward(RSCM.getReverseMapping(RSCMType.OBJ, pending),
                player.vars[WintertodtState.PendingCount], player.vars[WintertodtState.PendingNoted] == 1)
        } else {
            roll(player, random).also {
                WintertodtState.set(player, WintertodtState.PendingObj, it.obj.asRSCM(RSCMType.OBJ))
                WintertodtState.set(player, WintertodtState.PendingCount, it.count)
                WintertodtState.set(player, WintertodtState.PendingNoted, if (it.noted) 1 else 0)
            }
        }
        if (!give(player, listOf(reward))) return false
        WintertodtState.set(player, WintertodtState.RewardPool,
            player.vars[WintertodtState.RewardPool] - 1)
        for (key in listOf(WintertodtState.PendingObj, WintertodtState.PendingCount,
            WintertodtState.PendingNoted)) WintertodtState.set(player, key, 0)
        return true
    }
    fun give(player: Player, rewards: List<WinterReward>, consume: String? = null): Boolean {
        val transaction = player.invTransaction(player.inv) {
            val inventory = select(player.inv)
            if (consume != null) delete(inventory, consume.asRSCM(), 1)
            for (reward in rewards) add(inventory, reward.obj.asRSCM(), reward.count, cert = reward.noted)
        }
        if (!transaction.success) return false
        for (reward in rewards) {
            CollectionLog.grant(player, reward.obj, reward.count)
            if (reward.obj == "obj.phoenixpet") {
                WintertodtState.set(player, "varbit.pet_insurance_phoenixpet", 1)
            }
            player.mes("Your reward is: ${reward.count} x ${ServerCacheManager.getItem(reward.obj.asRSCM())!!.name}.")
        }
        return true
    }
    fun owned(player: Player, obj: String): Int =
        (sequenceOf(player.inv, player.worn) + player.invMap.values.asSequence())
            .filter { it !== player.collectionTransmit }.distinct()
            .sumOf {
                it.count(obj).toLong() + if (obj == "obj.wint_torch")
                    it.count("obj.wint_torch_offhand") else 0
            }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
