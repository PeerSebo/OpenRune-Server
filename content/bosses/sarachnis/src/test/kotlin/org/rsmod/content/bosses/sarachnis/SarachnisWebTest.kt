package org.rsmod.content.bosses.sarachnis

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import dev.openrune.util.Wearpos
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.loc.LocRegistryRegion
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.route.BoundValidator
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.map.collision.addLoc
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import sun.misc.Unsafe

@ResourceLock("ServerCacheManager")
class SarachnisWebTest {
    @Test fun `knife in inventory shows knife animation without replacing equipped item`() {
        for (symbol in listOf("loc.sarachnis_web", "loc.sarachnis_web_peek")) {
            val f = Fixture(symbol)
            f.player.inv[4] = InvObj("obj.knife", 1)
            f.player.worn[Wearpos.RightHand.slot] = InvObj("obj.bronze_mace", 1)
            f.player.inv.clearModifiedSlots()
            f.player.worn.clearModifiedSlots()
            f.slash()
            assertEquals("seq.human_knife_slash".asRSCM(), f.player.pendingSequence.id)
            f.tick()
            assertNull(f.locs.findExact(f.web.coords, f.web.shape))
            assertEquals(1, f.player.inv.count("obj.knife"))
            assertEquals(1, f.player.worn.count("obj.bronze_mace"))
            assertFalse(f.player.inv.hasModifiedSlots())
            assertFalse(f.player.worn.hasModifiedSlots())
        }
    }

    @Test fun `equipped slash weapon opens web with sword animation`() {
        val f = Fixture()
        f.player.worn[Wearpos.RightHand.slot] = InvObj("obj.bronze_scimitar", 1)
        f.slash()
        assertEquals("seq.human_sword_slash".asRSCM(), f.player.pendingSequence.id)
        f.tick()
        assertNull(f.locs.findExact(f.web.coords, f.web.shape))
        assertEquals(1, f.player.worn.count("obj.bronze_scimitar"))
    }

    @Test fun `inventory weapon and Aranea boots alone do not satisfy blade requirement`() {
        for (inventoryWeapon in listOf(false, true)) {
            val f = Fixture()
            if (inventoryWeapon) f.player.inv[0] = InvObj("obj.bronze_scimitar", 1)
            else f.player.worn[Wearpos.Feet.slot] = InvObj("obj.aranea_boots", 1)
            val initialSequence = f.player.pendingSequence
            f.slash()
            assertEquals(initialSequence, f.player.pendingSequence)
            assertFalse(f.player.isAccessProtected)
            assertEquals(f.web.entity, f.locs.findExact(f.web.coords, f.web.shape)?.entity)
            assertTrue(f.client.messages.any { "equipped slash weapon or a knife" in it.toString() })
        }
    }

    @Test fun `interrupted knife slash preserves web and inventory`() {
        val f = Fixture()
        f.player.inv[0] = InvObj("obj.knife", 1)
        f.slash()
        f.player.cancelActiveCoroutine()
        f.tick()
        assertEquals(f.web.entity, f.locs.findExact(f.web.coords, f.web.shape)?.entity)
        assertEquals(1, f.player.inv.count("obj.knife"))
    }

    private class Fixture(symbol: String = "loc.sarachnis_web") {
        val events = EventBus()
        val client = RecordingClient()
        val player = Player().apply {
            this.client = this@Fixture.client
            coords = CoordGrid(1839, 9912)
            currentMapClock = 100
            processedMapClock = 100
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
        }
        private val clock = MapClock(100)
        private val collision = CollisionFlagMap().apply {
            for (x in 1824..1856 step 8) for (z in 9896..9928 step 8) allocateIfAbsent(x, z, 0)
        }
        private val storage = LocZoneStorage()
        private val updates = ZoneUpdateMap()
        private val normal = LocRegistryNormal(updates, collision, storage)
        private val regions = RegionRegistry(RegionListSmall(), RegionListLarge(), normal, collision, storage,
            NpcRegistry(NpcList(), collision, events), ControllerRegistry(clock, ControllerList()), ZonePlayerActivityBitSet())
        val locs = LocRepository(clock, LocRegistry(storage, normal, LocRegistryRegion(updates, collision, storage, regions)), regions)
        private val access = ProtectedAccessContextFactory.empty().copy(getEventBus = { events }, getCollision = { collision })
        val web: BoundLocInfo

        init {
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM()))
            val loc = LocInfo(0, CoordGrid(1840, 9912), LocEntity(type.id, 0, 0))
            storage.mapLocs[ZoneKey.from(loc.coords), LocZoneKey(ZoneGrid.from(loc.coords), loc.layer)] = loc.entity
            collision.addLoc(loc, type)
            web = BoundLocInfo(loc, type)
            with(SarachnisAccess(unused<InstanceManager>(), clock, locs, PlayerList())) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        fun slash() {
            assertTrue(ProtectedAccessLauncher.withProtectedAccess(player, access) {
                val event = checkNotNull(LocInteractions(BoundValidator(collision), events)
                    .opTrigger(player, web, InteractionOp.Op1))
                assertTrue(events.publish(this, event))
            })
        }

        fun tick() {
            clock.tick()
            player.currentMapClock = clock.cycle
            player.processedMapClock = clock.cycle
            player.activeCoroutine?.advance()
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

        private inline fun <reified T> unused(): T {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
            return (field.get(null) as Unsafe).allocateInstance(T::class.java) as T
        }
    }
}
