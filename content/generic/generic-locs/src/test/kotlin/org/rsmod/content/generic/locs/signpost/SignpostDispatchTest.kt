package org.rsmod.content.generic.locs.signpost

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import net.rsprot.protocol.game.outgoing.camera.CamReset
import net.rsprot.protocol.game.outgoing.interfaces.IfOpenSub
import net.rsprot.protocol.game.outgoing.interfaces.IfSetText
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.enums.NamedEnums.signpost_directions
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.ifClose
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

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class SignpostDispatchTest {
    @Test fun `both northern Al Kharid signs open all four compass directions through approach and click`() {
        for (coords in listOf(CoordGrid(3268, 3332), CoordGrid(3283, 3333))) {
            for (approach in listOf(false, true)) {
                val f = Fixture()
                f.read("loc.fai_varrock_signpost_1", coords, approach)
                f.assertCompass(listOf("West to Lumbridge.", "South to Al Kharid.", "North to Varrock.", "East to the Digsite."))
                assertFalse(f.client.closed)
                f.player.ifClose(f.events)
                assertTrue(f.client.messages.any { it === CamReset })
                assertFalse(f.player.ui.containsModal("interface.aide_compass"))
            }
        }
    }

    @Test fun `unmapped sign placements cannot throw disconnect or change the camera`() {
        val missing = CoordGrid(3284, 3333)
        assertNull(signpost_directions.getOrNull(missing))
        for (symbol in listOf("loc.fai_varrock_signpost_1", "loc.aide_signpost_1")) {
            for (approach in listOf(false, true)) {
                val f = Fixture()
                assertDoesNotThrow { f.read(symbol, missing, approach) }
                assertFalse(f.client.closed)
                assertFalse(f.player.ui.containsModal("interface.aide_compass"))
                val message = f.client.messages.single()
                assertTrue(message.toString().contains("You can't make out the directions on this signpost."))
            }
        }
    }

    @Test fun `existing aide signs keep their coordinate specific compass rows`() {
        val existing = listOf(53005468, 53431454, 50908384, 48876750)
        for (packed in existing) {
            val coords = CoordGrid(packed)
            val f = Fixture()
            f.read("loc.aide_signpost_1", coords, approach = false)
            f.assertCompass(signpost_directions.getValue(coords).split("|"))
        }
    }

    private class Fixture {
        val events = EventBus()
        val client = RecordingClient()
        val player = Player().apply {
            this.client = this@Fixture.client
            currentMapClock = 100
            processedMapClock = 100
        }
        private val context = ProtectedAccessContextFactory.empty().copy(getEventBus = { events })
        private val interactions = LocInteractions(BoundValidator(CollisionFlagMap()), events)

        init {
            with(SignpostScript()) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
        }

        fun read(symbol: String, coords: CoordGrid, approach: Boolean) {
            player.coords = coords.translateX(if (approach) -4 else -1)
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM()))
            val loc = BoundLocInfo(LocInfo(2, coords, LocEntity(type.id, 10, 2)), type)
            val event = checkNotNull(if (approach) {
                interactions.apTrigger(player, loc, InteractionOp.Op1)
            } else {
                interactions.opTrigger(player, loc, InteractionOp.Op1)
            })
            assertTrue(ProtectedAccessLauncher.withProtectedAccess(player, context) {
                assertTrue(events.publish(this, event))
            })
        }

        fun assertCompass(expected: List<String>) {
            assertTrue(player.ui.containsModal("interface.aide_compass"))
            assertTrue(client.messages.filterIsInstance<IfOpenSub>().any {
                it.interfaceId == "interface.aide_compass".asRSCM()
            })
            val text = client.messages.filterIsInstance<IfSetText>()
            assertEquals(4, text.size)
            for ((direction, value) in listOf("west", "south", "north", "east").zip(expected)) {
                val component = "component.aide_compass:aide_${direction}_text_2".asRSCM()
                assertEquals(value, text.single { it.combinedId == component }.text)
                assertEquals(component, ServerCacheManager.fromComponent(component).packed)
            }
        }
    }

    private class RecordingClient : Client<Any, Any> {
        val messages = mutableListOf<Any>()
        var closed = false
        override fun write(message: Any) { messages += message }
        override fun close() { closed = true }
        override fun read(player: Player) {}
        override fun flush() {}
        override fun flushHighPriority() {}
        override fun unregister(service: Any, player: Player) {}
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
