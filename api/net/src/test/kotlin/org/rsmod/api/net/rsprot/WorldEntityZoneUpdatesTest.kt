package org.rsmod.api.net.rsprot

import net.rsprot.protocol.api.util.ZonePartialEnclosedCacheBuffer
import net.rsprot.protocol.game.outgoing.zone.header.UpdateZoneFullFollows
import net.rsprot.protocol.game.outgoing.zone.header.UpdateZonePartialEnclosed
import net.rsprot.protocol.game.outgoing.zone.payload.LocAddChangeV2
import net.rsprot.protocol.message.OutgoingGameMessage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.rsmod.api.game.process.player.WorldEntityZoneUpdates
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.loc.LocRegistryRegion
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.utils.zone.SharedZoneEnclosedBuffers
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.WorldEntity
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.obj.Obj
import org.rsmod.game.obj.ObjEntity
import org.rsmod.game.region.Region
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap

class WorldEntityZoneUpdatesTest {
    @Test fun `newly visible raft sends fittings on every deck layer exactly once`() {
        val f = Fixture()
        f.fit(CoordGrid(6402, 6403, 0), 100)
        f.fit(CoordGrid(6404, 6403, 1), 101)
        val packets = f.send(added = true)
        assertEquals(4, packets.filterIsInstance<UpdateZoneFullFollows>().size)
        assertEquals(listOf(0, 1), packets.filterIsInstance<UpdateZonePartialEnclosed>().map { it.level })
        assertTrue(packets.first() is UpdateZoneFullFollows)
        assertTrue(packets.none { it is LocAddChangeV2 })
        f.updates.clear()
        assertTrue(f.send(added = false).isEmpty())
    }

    @Test fun `existing viewers receive deck changes and never another boat or root scenery`() {
        val f = Fixture()
        f.updates.locDel(LocInfo(2, CoordGrid(6402, 6403, 1), LocEntity(100, 10, 0)))
        f.updates.locDel(LocInfo(2, CoordGrid(6410, 6403, 1), LocEntity(101, 10, 0)))
        f.updates.locDel(LocInfo(2, CoordGrid(3050, 3190, 0), LocEntity(102, 10, 0)))
        val packets = f.send(added = false)
        assertEquals(1, packets.size)
        val change = packets.single() as UpdateZonePartialEnclosed
        assertEquals(0, change.zoneX)
        assertEquals(0, change.zoneZ)
        assertEquals(1, change.level)
        assertTrue(change.payload.isReadable)
    }

    @Test fun `shore observer receives complete raft fittings before boarding`() {
        val f = Fixture()
        f.player.coords = f.world.rootCoords.translate(1, 0)
        f.fit(CoordGrid(6402, 6403, 0), 100)
        assertEquals(1, f.send(added = true).filterIsInstance<UpdateZonePartialEnclosed>().size)
    }

    @Test fun `reentering visible world resets all layers including removed static fittings`() {
        val f = Fixture()
        f.fit(CoordGrid(6402, 6403, 1), LocRegistry.DELETED_LOC_ID)
        assertEquals(1, f.send(added = true).filterIsInstance<UpdateZonePartialEnclosed>().size)
        assertEquals(1, f.send(added = true).filterIsInstance<UpdateZonePartialEnclosed>().size)
    }

    @Test fun `ground item additions and deletions are enclosed rather than invalid standalone packets`() {
        val f = Fixture()
        val obj = Obj(CoordGrid(6403, 6403, 1), ObjEntity(995, 50, 0), 100, -1)
        f.updates.objAdd(obj)
        f.updates.objDel(obj)
        val packets = f.send(added = false)
        assertEquals(1, packets.size)
        assertTrue(packets.single() is UpdateZonePartialEnclosed)
        assertTrue((packets.single() as UpdateZonePartialEnclosed).payload.readableBytes() > 4)
    }

    private class Fixture {
        val player = Player().apply { slotId = 1; coords = CoordGrid(6403, 6402, 1) }
        val updates = ZoneUpdateMap()
        private val storage = LocZoneStorage()
        private val flags = CollisionFlagMap()
        private val events = EventBus()
        private val normal = LocRegistryNormal(updates, flags, storage)
        private val regions = RegionRegistry(
            RegionListSmall(), RegionListLarge(), normal, flags, storage,
            NpcRegistry(NpcList(), flags, events),
            ControllerRegistry(MapClock(100), ControllerList()), ZonePlayerActivityBitSet(),
        )
        private val locs = LocRegistry(storage, normal, LocRegistryRegion(updates, flags, storage, regions))
        private val sender = WorldEntityZoneUpdates(locs, ObjRegistry(updates), updates,
            SharedZoneEnclosedBuffers(updates, ZonePartialEnclosedCacheBuffer()))
        val world = WorldEntity(
            1, Region(CoordGrid(6400, 6400), CoordGrid(6528, 6528), 1, 0),
            CoordGrid(6400, 6400), 1, 1, 0, 3, 1,
        ).apply { rootCoords = CoordGrid(3051, 3193) }

        fun fit(coords: CoordGrid, id: Int) {
            val loc = LocEntity(id, 10, 0)
            storage.spawnedLocs[ZoneKey.from(coords), LocZoneKey(ZoneGrid.from(coords), 2)] = loc
        }

        fun send(added: Boolean): List<OutgoingGameMessage> = buildList {
            sender.send(player, world, added) { add(it) }
        }
    }
}
