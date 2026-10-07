package org.rsmod.content.minigames.giantsfoundry.state

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.minigames.giantsfoundry.configs.FoundryMouldPart
import org.rsmod.content.minigames.giantsfoundry.configs.FoundryStation
import org.rsmod.game.entity.Player

@ResourceLock("ServerCacheManager")
class FoundryStateTest {
    @Test
    fun `native commission state and records survive a save while activity resets`() {
        val player = Player().apply {
            foundryWord1 = 4
            foundryWord2 = 5
            foundryQuality = 79
            foundryStartQuality = 94
            foundryDifficulty = 50
            foundryTemperature = 570
            foundryCompletion = 600
            foundryMouldState = 2
            foundryStored = 1
            foundryReputation = 1500
            foundryStartedAt = 4_200_000_000L
            foundryBestQuality = 94
            foundryBestTime = 237
            foundryCompleted = 12
            foundrySweetCount = 2
            foundryNextSweetCycle = 100
            foundrySweetUntil = 200
            foundrySweetClaimed = true
            foundryWorkStation = 2
            foundryLastWorkStation = 2
            foundryLastWorkCycle = 80
            foundrySessionGeneration = 3
        }
        FoundryState.setStations(player, listOf(FoundryStation.HAMMER, FoundryStation.GRINDSTONE, FoundryStation.POLISHING_WHEEL, FoundryStation.HAMMER))
        val restored = reload(player)
        assertEquals(4, restored.foundryWord1)
        assertEquals(5, restored.foundryWord2)
        assertEquals(79, restored.foundryQuality)
        assertEquals(94, restored.foundryStartQuality)
        assertEquals(50, restored.foundryDifficulty)
        assertEquals(570, restored.foundryTemperature)
        assertEquals(600, restored.foundryCompletion)
        assertEquals(2, restored.foundryMouldState)
        assertEquals(1, restored.foundryStored)
        assertEquals(1500, restored.foundryReputation)
        assertEquals(4_200_000_000L, restored.foundryStartedAt)
        assertEquals(94, restored.foundryBestQuality)
        assertEquals(237, restored.foundryBestTime)
        assertEquals(12, restored.foundryCompleted)
        assertEquals(2, restored.foundrySweetCount)
        assertEquals(FoundryStation.GRINDSTONE, FoundryState.station(restored, 1))
        assertEquals(0, restored.foundryNextSweetCycle)
        assertEquals(0, restored.foundrySweetUntil)
        assertFalse(restored.foundrySweetClaimed)
        assertEquals(0, restored.foundryWorkStation)
        assertEquals(0, restored.foundryLastWorkStation)
        assertEquals(0, restored.foundryLastWorkCycle)
        assertEquals(0, restored.foundrySessionGeneration)
    }

    @Test
    fun `clearing commission stops process while preserving preloaded metal rewards and unlocked moulds`() {
        val player = Player().apply {
            foundryWord1 = 1
            foundryWord2 = 5
            foundryQuality = 80
            foundryMouldState = 2
            foundryReputation = 500
            foundryCompleted = 8
            foundryBestQuality = 80
            foundryBestTime = 250
            foundryStartedAt = 1000
            foundrySweetCount = 1
            foundryWorkStation = 1
            foundryLastWorkStation = 1
            foundryLastWorkCycle = 100
            foundrySessionGeneration = 3
            softTimer(FoundryState.PROCESS_TIMER, 2)
        }
        VarPlayerIntMapSetter.set(player, "varbit.giants_foundry_adamant_count", 28)
        VarPlayerIntMapSetter.set(player, "varbit.giants_foundry_crucible_state", 2)
        VarPlayerIntMapSetter.set(player, "varbit.giants_foundry_unlocked_mould_ricasso_11", 1)
        VarPlayerIntMapSetter.set(player, FoundryMouldPart.FORTE.selectedVarbit, 1)
        FoundryState.clearCommission(player)
        assertFalse(FoundryState.hasCommission(player))
        assertEquals(0, player.foundryQuality)
        assertEquals(0, player.foundryMouldState)
        assertEquals(0L, player.foundryStartedAt)
        assertEquals(0, player.foundrySweetCount)
        assertEquals(0, player.foundryWorkStation)
        assertEquals(0, player.foundryLastWorkStation)
        assertEquals(0, player.foundryLastWorkCycle)
        assertEquals(4, player.foundrySessionGeneration)
        assertFalse(FoundryState.PROCESS_TIMER in player.softTimerMap)
        assertEquals(listOf(0, 0, 0, 0, 28, 0), FoundryState.metalCounts(player))
        assertEquals(2, player.vars["varbit.giants_foundry_crucible_state"])
        assertEquals(1, player.vars["varbit.giants_foundry_unlocked_mould_ricasso_11"])
        assertEquals(500, player.foundryReputation)
        assertEquals(8, player.foundryCompleted)
        assertEquals(80, player.foundryBestQuality)
        assertEquals(250, player.foundryBestTime)
    }

    @Test
    fun `server side records never transmit and cannot leak between players`() {
        val first = Player().apply { foundryReputation = 900; foundryStartedAt = Long.MAX_VALUE }
        val second = Player()
        assertEquals(0, second.foundryReputation)
        assertEquals(0L, second.foundryStartedAt)
        assertEquals(Long.MAX_VALUE, first.foundryStartedAt)
        for (name in listOf("started_epoch_low", "started_epoch_high", "best_quality", "best_time", "completed", "sweet_count")) {
            val type = requireNotNull(ServerCacheManager.getVarp("varp.giants_foundry_$name".asRSCM()))
            assertEquals(VarpLifetime.Perm, type.scope)
            assertEquals(VarpTransmitLevel.Never, type.transmit)
        }
    }

    @Test
    fun `new stage list replaces every seed from previous commissions`() {
        val player = Player()
        FoundryState.setStations(player, List(7) { if (it == 0) FoundryStation.HAMMER else FoundryStation.POLISHING_WHEEL })
        FoundryState.setStations(player, listOf(FoundryStation.HAMMER, FoundryStation.GRINDSTONE, FoundryStation.POLISHING_WHEEL))
        assertEquals(FoundryStation.HAMMER, FoundryState.station(player, 0))
        assertEquals(FoundryStation.GRINDSTONE, FoundryState.station(player, 1))
        assertEquals(FoundryStation.POLISHING_WHEEL, FoundryState.station(player, 2))
        for (section in 3..6) assertEquals(FoundryStation.HAMMER, FoundryState.station(player, section))
        assertNull(FoundryState.station(player, -1))
        assertNull(FoundryState.station(player, 7))
    }

    private fun reload(original: Player) = Player().apply {
        vars.backing.putAll(original.vars.backing.filterKeys { ServerCacheManager.getVarp(it)?.scope == VarpLifetime.Perm })
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
