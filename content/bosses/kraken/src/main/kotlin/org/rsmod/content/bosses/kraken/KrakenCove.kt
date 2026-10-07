package org.rsmod.content.bosses.kraken

import jakarta.inject.Inject
import org.rsmod.api.instances.*
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.game.MapClock
import org.rsmod.game.entity.PlayerList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class KrakenCove @Inject constructor(
    private val manager: InstanceManager,
    private val requirements: KrakenAttackHook,
    private val clock: MapClock,
    private val players: PlayerList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.slayer_cave_kraken_maincave_entrance") { arriveDelay(); telejump(CoveArrival, TeleportType.Exempt) }
        onOpLoc1("loc.slayer_cave_kraken_maincave_exit") { arriveDelay(); telejump(SurfaceArrival, TeleportType.Exempt) }
        onOpLoc1("loc.slayer_cave_kraken_boss_entrance") {
            requirements.requirement(player)?.let { mes(it); return@onOpLoc1 }
            arriveDelay(); telejump(RoomArrival, TeleportType.Exempt)
        }
        onOpLoc2("loc.slayer_cave_kraken_boss_entrance") { enterPrivate() }
        onOpLoc3("loc.slayer_cave_kraken_boss_entrance") {
            val count = players.count { !it.staffInvisible && it.level == 0 && it.x in 2268..2294 && it.z in 10022..10047 }
            mes(if (count == 0) "The Kraken's lair is empty." else "There ${if (count == 1) "is 1 player" else "are $count players"} in the Kraken's lair.")
        }
        onOpLoc1("loc.slayer_cave_kraken_boss_exit") {
            val session = manager.sessionForPlayer(player)
            val destination = if (session?.key == Key) manager.leave(player, session, clock.cycle) else OutsideRoom
            telejump(destination, TeleportType.Exempt)
        }
    }

    private suspend fun ProtectedAccess.enterPrivate() {
        requirements.requirement(player)?.let { mes(it); return }
        if (!choice2("Pay 25,000 coins for a private room.", true, "Cancel.", false)) return
        val result = manager.create(player, Key, PrivateSpec, InstanceAccess.Private, clock.cycle)
        when (result) {
            is InstanceManager.Result.Failed -> mes(result.reason)
            is InstanceManager.Result.Created -> {
                telejump(result.enter, TeleportType.Exempt)
                if (player.coords == result.enter) manager.finalizeEntry(player, result.session, clock.cycle)
                else { manager.cancelPendingEntry(player, clock.cycle); invAdd(inv, "obj.coins", 25_000) }
            }
            else -> Unit
        }
    }

    internal companion object {
        const val Key = "kraken"
        val SurfaceArrival = CoordGrid(2280, 3610)
        val CoveArrival = CoordGrid(2276, 9988)
        val OutsideRoom = CoordGrid(2280, 10016)
        val RoomArrival = CoordGrid(2280, 10022)
        val BossSpawn = CoordGrid(2278, 10034)
        val Tentacles = listOf(CoordGrid(2275, 10034), CoordGrid(2275, 10038), CoordGrid(2284, 10034), CoordGrid(2284, 10038))
        val PrivateSpec get() = InstanceSettings(fee = 25_000, maxPlayers = 1, destroyWhenEmpty = true, bossName = "Kraken")
            .withArea(InstanceArea.copyRegions(regionIds = listOf(9116),
                enterCoord = RegionLocal(0, 35, 156, 40, 38), exitCoord = OutsideRoom,
                npcSpawns = listOf(InstanceNpc(KrakenRules.Pool, BossSpawn)) + Tentacles.map { InstanceNpc(KrakenRules.TentaclePool, it) }), settingsRowId = -1)
    }
}
