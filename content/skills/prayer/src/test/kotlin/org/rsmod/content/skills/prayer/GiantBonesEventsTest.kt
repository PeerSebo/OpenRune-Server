package org.rsmod.content.skills.prayer

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.interact.ObjInteractions
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.obj.Obj
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class GiantBonesEventsTest {
    @Test
    fun `native Bury removes ground bones and awards XP once with a full inventory`() {
        val f = Fixture()
        val type = checkNotNull(ServerCacheManager.getItem(GiantBonesEvents.BONES.asRSCM()))
        assertEquals("Bury", type.options.getOpOrNull(2))
        val logs = checkNotNull(ServerCacheManager.getItem("obj.logs".asRSCM()))
        f.player.inv.objs.fill(InvObj(logs, 1))
        val inventory = f.player.inv.objs.toList()
        val ground = f.spawn()

        f.interact(ground, InteractionOp.Op3)

        assertEquals(1500, f.player.statMap.getFineXP("stat.prayer"))
        assertEquals(inventory, f.player.inv.objs.toList())
        assertEquals(0, f.registry.count())
        f.player.currentMapClock += 3
        f.interact(ground, InteractionOp.Op3)
        assertEquals(1500, f.player.statMap.getFineXP("stat.prayer"))
    }

    @Test
    fun `native Take explains burial without moving bones or granting XP`() {
        val f = Fixture()
        val type = checkNotNull(ServerCacheManager.getItem(GiantBonesEvents.BONES.asRSCM()))
        assertEquals("Take", type.options.getOpOrNull(3))
        val ground = f.spawn()

        f.interact(ground, InteractionOp.Op4)

        assertEquals(1, f.registry.count())
        assertEquals(0, f.player.statMap.getFineXP("stat.prayer"))
        assertTrue(f.client.messages.any { GiantBonesEvents.TAKE_MESSAGE in it.toString() })
        assertEquals(GiantBonesEvents.TAKE_MESSAGE, GiantBonesTakeGate().validateTake(f.player, ground, type))
    }

    @Test
    fun `private distant and disappeared bones cannot grant burial rewards`() {
        val f = Fixture()
        val owner = Player().apply { observerUUID = 2 }
        val privateBones = Obj.fromOwner(owner, f.player.coords, GiantBonesEvents.BONES, 1)
        assertTrue(f.objs.add(privateBones, 100))
        assertFalse(f.script.consume(f.player, privateBones))
        val distant = f.spawn(f.player.coords.translateX(3))
        assertFalse(f.script.consume(f.player, distant))
        val vanished = Obj.fromOwner(f.player, f.player.coords, GiantBonesEvents.BONES, 1)
        assertFalse(f.script.consume(f.player, vanished))
        assertEquals(2, f.registry.count())
    }

    private class Fixture {
        val client = RecordingClient()
        val registry = ObjRegistry(ZoneUpdateMap())
        val objs = ObjRepository(MapClock(100), registry)
        val script = GiantBonesEvents(objs)
        val player = Player().apply {
            this.client = this@Fixture.client
            observerUUID = 1
            coords = CoordGrid(3200, 3200)
            currentMapClock = 100
            processedMapClock = 100
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
            VarPlayerIntMapSetter.set(this, "varbit.giant_bone_bury_warning_disable", 1)
        }
        private val events = EventBus()
        private val context = ProtectedAccessContextFactory.empty().copy(getEventBus = { events })
        private val interactions = ObjInteractions(events)

        init {
            with(script) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
        }

        fun spawn(coords: CoordGrid = player.coords): Obj =
            Obj.fromOwner(player, coords, GiantBonesEvents.BONES, 1).also { assertTrue(objs.add(it, 100)) }

        fun interact(ground: Obj, op: InteractionOp) {
            val event = checkNotNull(interactions.opTrigger(ground, op))
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
