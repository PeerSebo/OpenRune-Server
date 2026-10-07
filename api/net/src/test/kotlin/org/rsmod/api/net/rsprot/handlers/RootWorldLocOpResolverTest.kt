package org.rsmod.api.net.rsprot.handlers

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import net.rsprot.protocol.game.incoming.locs.OpLocV2
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.loc.LocRegistryRegion
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.worldentity.WorldEntityRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.route.BoundValidator
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.WorldEntity
import org.rsmod.game.entity.WorldEntityList
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.movement.RouteRequestCoord
import org.rsmod.game.region.Region
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RootWorldLocOpResolverTest {
    @BeforeAll
    fun loadPackedCache() {
        ServerCacheManager.init(241).close()
    }

    @Test
    fun `native root loc is resolved on projected plane instead of deck plane`() {
        val fixture = Fixture()
        val loc = fixture.addBuoy(fixture.root)
        val request = checkNotNull(fixture.resolve(fixture.packet()))
        assertSame(fixture.player, request.player)
        assertSame(fixture.entity, request.worldEntity)
        assertEquals(loc.coords, request.loc.coords)
        assertEquals(loc.id, request.loc.id)
        assertEquals(InteractionOp.Op1, request.op)
        assertFalse(request.accepted)
    }

    @Test
    fun `forged loc coordinates and wrong root level are rejected`() {
        val fixture = Fixture()
        fixture.addBuoy(CoordGrid(fixture.root.x, fixture.root.z, 1))
        assertNull(fixture.resolve(fixture.packet()))
        fixture.addBuoy(fixture.root)
        assertNull(fixture.resolve(fixture.packet(x = fixture.root.x + 1)))
        assertNull(fixture.resolve(fixture.packet(id = "loc.sailing_gangplank_port_sarim".asRSCM())))
    }

    @Test
    fun `nonexistent buoy options and malformed op numbers are rejected`() {
        val fixture = Fixture()
        fixture.addBuoy(fixture.root)
        for (op in listOf(0, 2, 3, 4, 5, 6)) {
            assertNull(fixture.resolve(fixture.packet(op = op)))
        }
    }

    @Test
    fun `player outside the supplied deck cannot produce a root request`() {
        val fixture = Fixture()
        fixture.addBuoy(fixture.root)
        fixture.player.coords = fixture.root
        assertNull(fixture.resolve(fixture.packet()))
    }

    @Test
    fun `unapproved root requests preserve player movement and interaction state`() {
        val fixture = Fixture()
        fixture.addBuoy(fixture.root)
        val route = RouteRequestCoord(fixture.player.coords.translate(1, 0), clientRequest = true)
        fixture.player.routeRequest = route
        val request = checkNotNull(fixture.resolve(fixture.packet()))
        assertFalse(request.accepted)
        assertSame(route, fixture.player.routeRequest)
        assertNull(fixture.player.interaction)
    }

    private class Fixture {
        val root = CoordGrid(3051, 3193, 0)
        val player = Player().apply {
            slotId = 1
            coords = CoordGrid(6403, 6402, 1)
        }
        private val eventBus = EventBus()
        private val collision = CollisionFlagMap()
        private val storage = LocZoneStorage()
        private val updates = ZoneUpdateMap()
        private val normal = LocRegistryNormal(updates, collision, storage)
        private val regions = RegionRegistry(
            RegionListSmall(), RegionListLarge(), normal, collision, storage,
            NpcRegistry(NpcList(), collision, eventBus),
            ControllerRegistry(MapClock(100), ControllerList()), ZonePlayerActivityBitSet(),
        )
        private val locs = LocRegistry(storage, normal, LocRegistryRegion(updates, collision, storage, regions))
        private val interactions = LocInteractions(BoundValidator(collision), eventBus)
        private val worldEntities = WorldEntityRegistry(WorldEntityList(), eventBus)
        val entity = WorldEntity(
            configId = 1,
            region = Region(CoordGrid(6400, 6400), CoordGrid(6528, 6528), 1, 0),
            instanceSouthWest = CoordGrid(6400, 6400),
            zoneSizeX = 1,
            zoneSizeZ = 1,
            minLevel = 0,
            maxLevel = 3,
            activeLevel = 1,
        ).apply {
            rootCoords = root
            ownerSlot = player.slotId
            worldEntities.add(this)
            assertSame(this, worldEntities.findByInstanceCoords(player.coords))
        }
        private val buoyId = "loc.sailing_docking_buoy_port_sarim".asRSCM()

        fun addBuoy(coords: CoordGrid): LocInfo {
            val loc = LocInfo(2, coords, LocEntity(buoyId, 10, 0))
            storage.mapLocs[ZoneKey.from(coords), LocZoneKey(ZoneGrid.from(coords), loc.layer)] = loc.entity
            return loc
        }

        fun packet(id: Int = buoyId, x: Int = root.x, op: Int = 1) =
            OpLocV2(id, x, root.z, false, op, 0)

        fun resolve(packet: OpLocV2) = resolveRootWorldLocOp(player, entity, packet, locs, interactions)
    }
}
