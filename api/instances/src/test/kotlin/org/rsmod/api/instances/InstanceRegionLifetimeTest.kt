package org.rsmod.api.instances

import dev.openrune.ServerCacheManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.instances.region.InstanceAreaResolver
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.client.NoopClient
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

@ResourceLock("ServerCacheManager")
class InstanceRegionLifetimeTest {
    @Test
    fun `two owner allocations before player zone registration retain separate collision copies`() {
        val fixture = Fixture()
        val firstOwner = fixture.player(1)
        val secondOwner = fixture.player(2)
        val first = fixture.create(firstOwner)
        val second = fixture.create(secondOwner)
        assertNotEquals(first.enter, second.enter)
        fixture.assertCollision(first.enter)
        fixture.assertCollision(second.enter)
        fixture.sweep()
        fixture.assertCollision(first.enter)
        fixture.assertCollision(second.enter)

        fixture.manager.cancelPendingEntry(firstOwner, 100)
        fixture.sweep()
        assertEquals(-1, fixture.flags[first.enter.x + 1, first.enter.z + 1, 0])
        fixture.assertCollision(second.enter)
        assertNull(fixture.manager.sessionForId(first.session.id))
        assertSame(second.session, fixture.manager.sessionForId(second.session.id))

        fixture.manager.cancelPendingEntry(secondOwner, 100)
        fixture.sweep()
        assertEquals(-1, fixture.flags[second.enter.x + 1, second.enter.z + 1, 0])
    }

    @Test
    fun `an unentered owner session expires and releases its region`() {
        val fixture = Fixture()
        val owner = fixture.player(1)
        val created = fixture.create(owner, reclaimTicks = 0)
        val deadline = 100 + DEFAULT_EMPTY_INSTANCE_RECLAIM_TICKS
        assertEquals(SessionState.Reclaim(deadline), created.session.state)
        fixture.manager.tickReclaim(deadline - 1)
        fixture.sweep()
        fixture.assertCollision(created.enter)
        fixture.manager.tickReclaim(deadline)
        fixture.sweep()
        assertNull(fixture.manager.sessionForId(created.session.id))
        assertEquals(-1, fixture.flags[created.enter.x + 1, created.enter.z + 1, 0])
        fixture.manager.finalizeEntry(owner, created.session, deadline + 1)
        assertNull(fixture.manager.sessionForPlayer(owner))
        assertNull(owner.currentInstanceId())
    }

    @Test
    fun `finalizing entry cancels the initial reclaim deadline without player zone registration`() {
        val fixture = Fixture()
        val owner = fixture.player(1)
        val created = fixture.create(owner, reclaimTicks = 0)
        owner.coords = created.enter
        fixture.manager.finalizeEntry(owner, created.session, 101)
        assertEquals(SessionState.Active, created.session.state)
        fixture.manager.tickReclaim(100 + DEFAULT_EMPTY_INSTANCE_RECLAIM_TICKS)
        fixture.sweep()
        fixture.assertCollision(created.enter)
        assertSame(created.session, fixture.manager.sessionForId(created.session.id))
    }

    @Test
    fun `owner reclaim deadline releases a previously protected region`() {
        val fixture = Fixture()
        val owner = fixture.player(1)
        val created = fixture.create(owner, reclaimTicks = 5)
        owner.coords = created.enter
        fixture.manager.finalizeEntry(owner, created.session, 100)
        fixture.manager.leave(owner, created.session, 100)
        fixture.manager.tickReclaim(104)
        fixture.sweep()
        fixture.assertCollision(created.enter)
        fixture.manager.tickReclaim(105)
        fixture.sweep()
        assertNull(fixture.manager.sessionForId(created.session.id))
        assertEquals(-1, fixture.flags[created.enter.x + 1, created.enter.z + 1, 0])
    }

    @Test
    fun `ending a server owned session releases its protected region`() {
        val fixture = Fixture()
        val session = checkNotNull(fixture.manager.createServerOwned(
            "region_lifetime", fixture.spec(), currentTick = 100,
        ))
        val enter = checkNotNull(fixture.manager.resolveCoord(session, CoordGrid(3136, 2816, 0)))
        fixture.sweep()
        fixture.assertCollision(enter)
        assertTrue(fixture.manager.endServerOwned(session, 100))
        fixture.sweep()
        assertEquals(-1, fixture.flags[enter.x + 1, enter.z + 1, 0])
        assertFalse(fixture.manager.endServerOwned(session, 100))
    }

    private class Fixture {
        val flags = CollisionFlagMap()
        private val clock = MapClock(100)
        private val events = EventBus()
        private val locs = LocZoneStorage()
        private val updates = ZoneUpdateMap()
        private val npcs = NpcList()
        private val players = PlayerList()
        private val npcRegistry = NpcRegistry(npcs, flags, events)
        private val registry = RegionRegistry(
            RegionListSmall(), RegionListLarge(), LocRegistryNormal(updates, flags, locs),
            flags, locs, npcRegistry, ControllerRegistry(clock, ControllerList()),
            ZonePlayerActivityBitSet(),
        )
        val manager = InstanceManager(
            RegionRepository(registry), NpcRepository(clock, npcRegistry, npcs), players, events,
            InstanceAreaResolver(), clock, flags,
        )

        init {
            for (x in 3136 until 3200 step 8) {
                for (z in 2816 until 2880 step 8) {
                    repeat(4) { flags.allocateIfAbsent(x, z, it) }
                }
            }
            flags[3137, 2817, 0] = CollisionFlag.BLOCK_WALK
        }

        fun player(id: Int): Player = Player().apply {
            uuid = id.toLong()
            slotId = id
            client = NoopClient
            players[id] = this
        }

        fun spec(reclaimTicks: Int = 5) = InstanceSettings(
            reclaimTicks = reclaimTicks, destroyWhenEmpty = false,
        ).withArea(
            InstanceArea.copyRegions(
                listOf(12588), enterCoord = RegionLocal(0, 49, 44, 0, 0),
                exitCoord = CoordGrid.ZERO,
            ),
            0,
        )

        fun create(owner: Player, reclaimTicks: Int = 5): InstanceManager.Result.Created =
            manager.create(owner, "region_lifetime", spec(reclaimTicks), InstanceAccess.Private, 100)
                as InstanceManager.Result.Created

        fun sweep() {
            registry.removeInactiveSmallRegions()
            registry.removeInactiveLargeRegions()
        }

        fun assertCollision(enter: CoordGrid) {
            assertEquals(CollisionFlag.BLOCK_WALK, flags[enter.x + 1, enter.z + 1, 0])
        }
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
