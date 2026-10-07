package org.rsmod.content.minigames.giantsfoundry.state

import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.boolVarp
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.content.minigames.giantsfoundry.configs.FoundryData
import org.rsmod.content.minigames.giantsfoundry.configs.FoundryMouldPart
import org.rsmod.content.minigames.giantsfoundry.configs.FoundryStation
import org.rsmod.game.entity.Player

var Player.foundryWord1 by intVarBit("varbit.giants_foundry_commission_word_1")
var Player.foundryWord2 by intVarBit("varbit.giants_foundry_commission_word_2")
var Player.foundryMouldState by intVarBit("varbit.giants_foundry_mould_state")
var Player.foundryStored by intVarBit("varbit.giants_foundry_preform_stored")
var Player.foundryQuality by intVarBit("varbit.giants_foundry_preform_quality")
var Player.foundryDifficulty by intVarBit("varbit.giants_foundry_preform_dificulty")
var Player.foundryTemperature by intVarBit("varbit.giants_foundry_preform_temperature")
var Player.foundryCompletion by intVarBit("varbit.giants_foundry_preform_completion")
var Player.foundryStartQuality by intVarBit("varbit.giants_foundry_preform_start_quality")
var Player.foundryReputation by intVarp("varp.giants_foundry_reward_shop_points")
var Player.foundryBestQuality by intVarp("varp.giants_foundry_best_quality")
var Player.foundryBestTime by intVarp("varp.giants_foundry_best_time")
var Player.foundryCompleted by intVarp("varp.giants_foundry_completed")
var Player.foundryNextSweetCycle by intVarp("varp.giants_foundry_next_sweet_cycle")
var Player.foundrySweetUntil by intVarp("varp.giants_foundry_sweet_until")
var Player.foundrySweetClaimed by boolVarp("varp.giants_foundry_sweet_claimed")
var Player.foundryWorkStation by intVarp("varp.giants_foundry_work_station")
var Player.foundrySessionGeneration by intVarp("varp.giants_foundry_session_generation")
var Player.foundrySweetCount by intVarp("varp.giants_foundry_sweet_count")
var Player.foundryLastWorkStation by intVarp("varp.giants_foundry_last_work_station")
var Player.foundryLastWorkCycle by intVarp("varp.giants_foundry_last_work_cycle")
var Player.foundryLastDepositCount by intVarp("varp.giants_foundry_last_deposit_count")
var Player.foundryPourPending by boolVarp("varp.giants_foundry_pour_pending")
var Player.foundryPourGeneration by intVarp("varp.giants_foundry_pour_generation")
private var Player.foundryStartedLow by intVarp("varp.giants_foundry_started_epoch_low")
private var Player.foundryStartedHigh by intVarp("varp.giants_foundry_started_epoch_high")

var Player.foundryStartedAt: Long
    get() = (foundryStartedHigh.toLong() shl 32) or (foundryStartedLow.toLong() and 0xffffffffL)
    set(value) {
        require(value >= 0)
        foundryStartedHigh = (value ushr 32).toInt()
        foundryStartedLow = value.toInt()
    }

object FoundryState {
    const val PROCESS_TIMER = "timer.giants_foundry_process"

    fun metalCounts(player: Player): List<Int> = FoundryData.metals.map { player.vars[it.countVarbit] }

    fun hasCommission(player: Player): Boolean = player.foundryWord1 in 1..6 && player.foundryWord2 in 1..6

    fun selectedMoulds(player: Player) = FoundryMouldPart.entries.mapNotNull {
        FoundryData.mould(it, player.vars[it.selectedVarbit])
    }

    fun station(player: Player, section: Int): FoundryStation? {
        if (section !in 0..6) return null
        return FoundryStation.entries.getOrNull(player.vars["varbit.giants_foundry_seed_$section"])
    }

    fun setStations(player: Player, stations: List<FoundryStation>) {
        require(stations.size in 3..7 && stations.first() == FoundryStation.HAMMER)
        for (index in 0..6) {
            VarPlayerIntMapSetter.set(player, "varbit.giants_foundry_seed_$index", stations.getOrNull(index)?.ordinal ?: 0)
        }
    }

    fun clearCommission(player: Player) {
        player.foundryPourPending = false
        player.foundryWord1 = 0
        player.foundryWord2 = 0
        player.foundryMouldState = 0
        VarPlayerIntMapSetter.set(player, "varp.giants_foundry_preform_1", 0)
        VarPlayerIntMapSetter.set(player, "varp.giants_foundry_preform_2", 0)
        player.foundryStartedAt = 0
        player.foundrySweetCount = 0
        clearActivity(player)
        player.clearSoftTimer(PROCESS_TIMER)
    }

    fun clearActivity(player: Player) {
        player.foundryNextSweetCycle = 0
        player.foundrySweetUntil = 0
        player.foundrySweetClaimed = false
        player.foundryWorkStation = 0
        player.foundryLastWorkStation = 0
        player.foundryLastWorkCycle = 0
        player.foundrySessionGeneration++
    }
}
