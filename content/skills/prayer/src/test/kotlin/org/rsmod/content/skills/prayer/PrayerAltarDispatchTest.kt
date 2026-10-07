package org.rsmod.content.skills.prayer

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.events.PlayerActionCompleteEvent
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.route.BoundValidator
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class PrayerAltarDispatchTest {
    @Test fun `Guthix and ordinary altars restore to base without reducing an existing boost`() {
        for (altar in listOf("loc.guthix_altar", "loc.altar")) {
            val f = Fixture()
            f.pray(altar, current = 7)
            assertEquals(50, f.player.stat("stat.prayer"))
            assertTrue(f.client.messages.any { "recharge" in it.toString() })
            f.pray(altar, current = 55)
            assertEquals(55, f.player.stat("stat.prayer"))
        }
    }

    @Test fun `Monastery altar supplies exactly two bonus points from drained full and partial boost`() {
        val f = Fixture()
        for (current in listOf(7, 50, 51, 52, 55)) {
            f.pray("loc.monks_altar", current)
            assertEquals(maxOf(current, 52), f.player.stat("stat.prayer"))
        }
    }

    @Test fun `Smite altar fact uses the altar tile and requires active Smite`() {
        val f = Fixture()
        f.player.coords = CoordGrid(3376, 3285)
        f.player.statMap.setBaseLevel("stat.prayer", 99)
        VarPlayerIntMapSetter.set(f.player, "varbit.prayer_smite", 1)
        f.pray("loc.altar", 7, CoordGrid(3377, 3285))
        assertEquals(CoordGrid(3377, 3285), f.completions.single().coords)
        assertEquals("loc.altar", f.completions.single().target)
        VarPlayerIntMapSetter.set(f.player, "varbit.prayer_smite", 0)
        f.pray("loc.altar", 7, CoordGrid(3377, 3285))
        assertEquals(1, f.completions.size)
    }

    private class Fixture {
        val client = RecordingClient()
        val player = Player().apply {
            this.client = this@Fixture.client
            coords = CoordGrid(3200, 3200)
            currentMapClock = 100; processedMapClock = 100
            statMap.setBaseLevel("stat.prayer", 50)
        }
        private val events = EventBus()
        val completions = mutableListOf<PlayerActionCompleteEvent>()
        private val context = ProtectedAccessContextFactory.empty().copy(getEventBus = { events })
        private val interactions = LocInteractions(BoundValidator(CollisionFlagMap()), events)
        init {
            events.subscribeUnbound(PlayerActionCompleteEvent::class.java) { completions += this }
            with(PrayerAltarScript()) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
        }
        fun pray(symbol: String, current: Int, coords: CoordGrid = CoordGrid(3201, 3200)) {
            player.statMap.setCurrentLevel("stat.prayer", current.toByte())
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM()))
            val loc = BoundLocInfo(LocInfo(2, coords, LocEntity(type.id, 10, 0)), type)
            val event = checkNotNull(interactions.opTrigger(player, loc, InteractionOp.Op1))
            assertTrue(ProtectedAccessLauncher.withProtectedAccess(player, context) {
                assertTrue(events.publish(this, event))
            })
        }
    }
    private class RecordingClient : Client<Any, Any> {
        val messages = mutableListOf<Any>()
        override fun write(message: Any) { messages += message }
        override fun close() {}
        override fun read(player: Player) {}
        override fun flush() {}
        override fun flushHighPriority() {}
        override fun unregister(service: Any, player: Player) {}
    }
    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
