package org.rsmod.content.bosses.thermy

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.cheat.adminGodMode
import org.rsmod.api.player.hat
import org.rsmod.api.player.hit.processor.DamageOnlyPlayerHitProcessor
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.statSub
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SmokeHazardTest {
    @BeforeAll fun cache() { ServerCacheManager.init(241).close() }

    @Test fun `all helmet families protect smoke while black masks do not and gas only protects the cave`() {
        for (symbol in listOf("obj.slayer_facemask", "obj.slayer_helm", "obj.slayer_helm_i_black",
            "obj.sw_slayer_helm_i_araxyte", "obj.pvpa_slayer_helm_i_hooded", "obj.league_6_slayer_helm1",
            "obj.sw_league_6_slayer_helm2_i")) {
            assertTrue(SmokeProtection.combat(symbol.asRSCM()), symbol)
            assertTrue(SmokeProtection.environment(symbol.asRSCM()), symbol)
        }
        assertFalse(SmokeProtection.combat("obj.gasmask".asRSCM()))
        assertTrue(SmokeProtection.environment("obj.gasmask".asRSCM()))
        assertFalse(SmokeProtection.environment("obj.harmless_black_mask".asRSCM()))
        assertFalse(SmokeProtection.environment(null))
    }

    @Test fun `cave hazard removes ten health once per interval and stops after leaving dying or protection`() {
        val clock = MapClock(100)
        val players = PlayerList()
        val player = Player().apply {
            slotId = 1
            coords = SmokeDevilCave.Inside
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
            statMap.setBaseLevel("stat.hitpoints", 99)
            statMap.setCurrentLevel("stat.hitpoints", 99)
        }
        players[1] = player
        val hazard = SmokeHazard(players, clock, DamageOnlyPlayerHitProcessor(EventBus(), NpcList(), players))
        val initial = player.hitpoints
        hazard.tick()
        clock.cycle = 119
        hazard.tick()
        assertEquals(initial, player.hitpoints)
        clock.cycle = 120
        hazard.tick()
        hazard.tick()
        assertEquals(initial - 10, player.hitpoints)
        player.coords = SmokeDevilCave.Outside
        clock.cycle = 160
        hazard.tick()
        assertEquals(initial - 10, player.hitpoints)
        player.coords = SmokeDevilCave.BossInside
        player.hat = InvObj("obj.slayer_helm")
        hazard.tick()
        clock.cycle = 200
        hazard.tick()
        assertEquals(initial - 10, player.hitpoints)
        player.hat = null
        player.adminGodMode = true
        hazard.tick()
        clock.cycle = 240
        hazard.tick()
        assertEquals(initial - 10, player.hitpoints)
        player.adminGodMode = false
        player.statSub("stat.hitpoints", player.hitpoints, 0)
        hazard.tick()
        clock.cycle = 280
        hazard.tick()
        assertEquals(0, player.hitpoints)
    }

    @Test fun `smoke boundaries exclude outside world other levels and neighbouring rooms`() {
        assertTrue(SmokeHazard.inCave(SmokeDevilCave.Inside))
        assertTrue(SmokeHazard.inCave(CoordGrid(2360, 9452)))
        assertFalse(SmokeHazard.inCave(SmokeDevilCave.Outside))
        assertFalse(SmokeHazard.inCave(CoordGrid(2360, 9452, 1)))
        assertFalse(SmokeHazard.inCave(CoordGrid(2360, 9500)))
    }

    @Test fun `leaving and reconnecting both restart the full smoke interval and never persist the timer`() {
        val type = checkNotNull(ServerCacheManager.getVarp("varp.thermy_next_smoke_damage".asRSCM()))
        assertEquals(VarpLifetime.Temp, type.scope)
        assertEquals(VarpTransmitLevel.Never, type.transmit)
        val events = EventBus()
        val clock = MapClock(0)
        val players = PlayerList()
        val player = Player().apply {
            slotId = 1
            coords = SmokeDevilCave.Inside
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
            statMap.setBaseLevel("stat.hitpoints", 99)
            statMap.setCurrentLevel("stat.hitpoints", 99)
        }
        players[1] = player
        val hazard = SmokeHazard(players, clock, DamageOnlyPlayerHitProcessor(events, NpcList(), players))
        with(hazard) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
        hazard.tick()
        assertEquals(20, player.vars["varp.thermy_next_smoke_damage"])
        clock.cycle = 19
        player.coords = SmokeDevilCave.Outside
        hazard.tick()
        assertEquals(0, player.vars["varp.thermy_next_smoke_damage"])
        player.coords = SmokeDevilCave.Inside
        hazard.tick()
        clock.cycle = 38
        hazard.tick()
        assertEquals(99, player.hitpoints)
        clock.cycle = 39
        hazard.tick()
        assertEquals(89, player.hitpoints)
        events.publish(SessionStateEvent.Logout(player))
        assertEquals(0, player.vars["varp.thermy_next_smoke_damage"])
        clock.cycle = 500
        events.publish(SessionStateEvent.Login(player))
        hazard.tick()
        clock.cycle = 519
        hazard.tick()
        assertEquals(89, player.hitpoints)
        clock.cycle = 520
        hazard.tick()
        assertEquals(79, player.hitpoints)
        assertFalse(player.vars.backing.filterKeys {
            ServerCacheManager.getVarp(it)?.scope == VarpLifetime.Perm
        }.containsKey(type.id))
    }
}
