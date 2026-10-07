package org.rsmod.content.minigames.giantsfoundry

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.invtx.add
import org.rsmod.api.invtx.delete
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.ironman.isUltimateIronman
import org.rsmod.api.player.restoreRunEnergyPercent
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.smithingLvl
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.worn.WornUnequipOp
import org.rsmod.api.random.GameRandom
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.minigames.giantsfoundry.configs.*
import org.rsmod.content.minigames.giantsfoundry.quest.SleepingGiantsService
import org.rsmod.content.minigames.giantsfoundry.quest.sleepingGiantsTutorialCommission
import org.rsmod.content.minigames.giantsfoundry.state.*
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

@Singleton
class FoundryGame @Inject constructor(
    private val interfaces: FoundryInterfaces,
    private val events: EventBus,
    private val xpModifiers: XpModifiers,
) {
    fun commission(
        player: Player,
        random: GameRandom,
        now: Long = System.currentTimeMillis() / 1000,
    ): Boolean {
        if (hasCommission(player)) return false
        player.sleepingGiantsTutorialCommission = false
        val first = random.of(1, 6)
        val choices = (1..6).filter { it != first && kotlin.math.abs(first - it) != 3 }
        player.foundryWord1 = first
        player.foundryWord2 = choices[random.of(choices.size)]
        player.foundryStartedAt = now
        return true
    }

    fun deposit(player: Player, item: Int, requested: Int, allowWaste: Boolean = false): Int {
        val material = FoundryData.materials[item] ?: return 0
        if (requested <= 0 || player.smithingLvl < material.metal.level) return 0
        val room = FoundryMath.CRUCIBLE_CAPACITY - metalCounts(player).sum()
        val type = requireNotNull(ServerCacheManager.getItem(item))
        val count = minOf(requested, player.inv.physicalCount(type.internalName))
        val offeredBars = count.toLong() * material.bars
        if (room <= 0 || count <= 0 || (!allowWaste && offeredBars > room)) return 0
        val used = minOf(room.toLong(), offeredBars).toInt()
        val transaction = player.invTransaction(player.inv) {
            delete(select(player.inv), item, count)
        }
        if (transaction.failure) return 0
        val countVarbit = material.metal.countVarbit
        VarPlayerIntMapSetter.set(player, countVarbit, player.vars[countVarbit] + used)
        val counts = metalCounts(player)
        val full = counts.sum() == FoundryMath.CRUCIBLE_CAPACITY
        VarPlayerIntMapSetter.set(player, CRUCIBLE_STATE, if (full) 2 else 1)
        VarPlayerIntMapSetter.set(player, ALLOY, if (full) FoundryMath.alloyScore(counts) else 0)
        SleepingGiantsService.refreshTutorial(player)
        return used
    }

    internal fun canCast(player: Player): Boolean {
        if (!hasCommission(player) || player.foundryStartQuality > 0 ||
            player.foundryMouldState != 1
        ) {
            return false
        }
        val counts = metalCounts(player)
        if (counts.sum() != FoundryMath.CRUCIBLE_CAPACITY) return false
        val moulds = selectedMoulds(player)
        return moulds.size == FoundryMouldPart.entries.size && moulds.all { canUseMould(player, it) }
    }

    fun cast(player: Player, random: GameRandom): Boolean {
        if (!canCast(player)) return false
        val counts = metalCounts(player)
        val moulds = selectedMoulds(player)
        val difficulty = FoundryMath.alloyScore(counts)
        val mouldQuality = FoundryData.mouldScore(player.foundryWord1, player.foundryWord2, moulds)
        val quality = difficulty + mouldQuality
        player.foundryDifficulty = difficulty
        player.foundryQuality = quality
        player.foundryStartQuality = quality
        player.foundryTemperature = 1000
        player.foundryCompletion = 0
        player.foundryMouldState = 2
        player.foundryStored = 0
        for (metal in FoundryData.metals) {
            VarPlayerIntMapSetter.set(player, metal.countVarbit, 0)
        }
        VarPlayerIntMapSetter.set(player, CRUCIBLE_STATE, 0)
        var previous = FoundryStation.HAMMER.ordinal
        for (section in 0..6) {
            val station =
                if (section == 0) previous
                else {
                    val choices = FoundryStation.entries.indices.filter { it != previous }
                    choices[random.of(choices.size)]
                }
            VarPlayerIntMapSetter.set(player, "varbit.giants_foundry_seed_$section", station)
            previous = station
        }
        player.foundrySweetCount = 0
        SleepingGiantsService.refreshTutorial(player)
        return true
    }

    fun work(
        player: Player,
        station: FoundryStation,
        sweet: Boolean = false,
        uniformBonus: Boolean = false,
    ): WorkResult {
        if (!isEquipped(player) || player.foundryStartQuality <= 0) return WorkResult.NoPreform
        if (player.foundryQuality == 0) return WorkResult.Ruined
        if (player.foundryCompletion >= FoundryMath.COMPLETE) return WorkResult.Complete
        val section = FoundryMath.section(player.foundryCompletion, player.foundryDifficulty)
        val expected = player.vars["varbit.giants_foundry_seed_$section"]
        if (expected != station.ordinal ||
            player.foundryTemperature !in FoundryMath.temperatureRange(
                station,
                player.foundryDifficulty,
            )
        ) {
            player.foundryQuality = (player.foundryQuality - 5).coerceAtLeast(0)
            player.foundryTemperature = (player.foundryTemperature + station.heat).coerceIn(0, 1000)
            return WorkResult.Damaged
        }
        val progress = (if (sweet) 50 else station.progress) + if (uniformBonus) 1 else 0
        player.foundryCompletion = minOf(FoundryMath.COMPLETE, player.foundryCompletion + progress)
        player.foundryTemperature = (player.foundryTemperature + station.heat).coerceIn(0, 1000)
        if (sweet) {
            player.foundryQuality = minOf(player.foundryStartQuality, player.foundryQuality + 1)
        }
        val xp = station.xp * (if (sweet) 2 else 1) * xpModifiers.get(player, "stat.smithing")
        player.statAdvance("stat.smithing", xp)
        return if (player.foundryCompletion == 1000) WorkResult.Complete else WorkResult.Progress
    }

    fun handIn(player: Player, now: Long = System.currentTimeMillis() / 1000): Reward? {
        if (!hasCommission(player) || player.foundryStartQuality <= 0 || !isEquipped(player)) {
            return null
        }
        if (player.foundryCompletion < 1000 && player.foundryQuality > 0) return null
        val quality = player.foundryQuality
        val xp = FoundryMath.experience(quality)
        val coins = FoundryMath.coins(quality)
        val destination =
            if (player.isUltimateIronman) player.inv else player.invMap.getOrPut("inv.bank")
        val transaction = player.invTransaction(destination) {
            add(select(destination), "obj.coins".asRSCM(), coins)
        }
        if (transaction.failure) return null
        val duration = (now - player.foundryStartedAt).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
        removePreform(player)
        val faster = player.foundryBestTime == 0 || duration < player.foundryBestTime
        val betterQuality = quality > player.foundryBestQuality
        val fasterSameQuality = quality == player.foundryBestQuality && faster
        if (betterQuality || fasterSameQuality) {
            player.foundryBestQuality = quality
            player.foundryBestTime = duration
        }
        player.foundryCompleted =
            (player.foundryCompleted.toLong() + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        player.foundryReputation =
            (player.foundryReputation.toLong() + quality)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
        clearSword(player)
        player.statAdvance("stat.smithing", xp * xpModifiers.get(player, "stat.smithing"))
        player.restoreRunEnergyPercent(50)
        return Reward(quality, xp, coins, duration)
    }

    fun store(player: Player) {
        removePreform(player)
        if (player.foundryStartQuality > 0 && player.foundryMouldState == 0) {
            player.foundryStored = 1
        }
        FoundryState.clearActivity(player)
        player.clearSoftTimer(TIMER)
        interfaces.hideHud(player)
    }

    fun cancel(player: Player) {
        store(player)
        clearSword(player)
    }

    private fun clearSword(player: Player) {
        player.sleepingGiantsTutorialCommission = false
        FoundryState.clearCommission(player)
        interfaces.hideHud(player)
    }

    private fun removePreform(player: Player) {
        if (isEquipped(player)) {
            val type = requireNotNull(ServerCacheManager.getItem(PREFORM.asRSCM()))
            player.worn[Wearpos.RightHand.slot] = null
            WornUnequipOp.notifyWornUnequip(player, Wearpos.RightHand, type, events)
        }
        val preformId = PREFORM.asRSCM()
        for (slot in player.inv.indices) {
            if (player.inv[slot]?.id == preformId) player.inv[slot] = null
        }
    }

    enum class WorkResult { NoPreform, Ruined, Damaged, Progress, Complete }

    data class Reward(val quality: Int, val xp: Int, val coins: Int, val seconds: Int)

    companion object {
        const val PREFORM = "obj.giants_foundry_preform"
        const val TIMER = "timer.giants_foundry_process"
        const val POINTS = "varp.giants_foundry_reward_shop_points"
        const val CRUCIBLE_STATE = "varbit.giants_foundry_crucible_state"
        const val ALLOY = "varbit.giants_foundry_mould_alloy_dificulty"
        fun isInside(coords: CoordGrid) =
            coords.x in 3340..3391 && coords.z in 11456..11519 && coords.level in 0..1

        fun hasCommission(player: Player) = FoundryState.hasCommission(player)
        fun isEquipped(player: Player) = player.righthand?.id == PREFORM.asRSCM()
        fun metalCounts(player: Player) = FoundryState.metalCounts(player)
        fun selectedMoulds(player: Player) = FoundryState.selectedMoulds(player)

        fun canUseMould(player: Player, mould: FoundryMould): Boolean =
            player.smithingLvl >= mould.level &&
                (mould.unlockVarbit == null || player.vars[mould.unlockVarbit] != 0)

        fun uniformBonus(player: Player, random: GameRandom): Boolean {
            val pieces = FoundryData.uniformPieces.count { alternatives ->
                alternatives.any { it in player.worn }
            }
            return pieces == FoundryData.uniformPieces.size || (pieces > 0 && random.of(5) < pieces)
        }
    }
}
