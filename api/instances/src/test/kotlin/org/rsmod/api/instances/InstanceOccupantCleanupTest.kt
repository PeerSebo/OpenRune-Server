package org.rsmod.api.instances

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.rsmod.api.instances.region.BaseAreaKey
import org.rsmod.api.instances.region.InstanceAreaResolver
import org.rsmod.api.instances.region.InstancePlacement
import org.rsmod.api.instances.region.buildRegionTemplate
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.region.Region
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import sun.misc.Unsafe

class InstanceOccupantCleanupTest {
    @Test fun `a missing occupant starts the normal reclaim countdown and releases indexes`() {
        val (manager, session) = fixture()
        manager.tickReclaim(100)
        assertTrue(session.occupants.isEmpty())
        assertEquals(SessionState.Reclaim(105), session.state)
        assertTrue(index<Long>(manager, "playerIndex").isEmpty())
        assertEquals(1, sessions(manager).size)
        manager.tickReclaim(104)
        assertEquals(1, sessions(manager).size)
        manager.tickReclaim(105)
        assertTrue(sessions(manager).isEmpty())
        assertTrue(index<Long>(manager, "ownerIndex").isEmpty())
        assertTrue(index<Int>(manager, "regionToInstance").isEmpty())
    }

    @Test fun `destroy when empty also applies when the last player vanished`() {
        val (manager, _) = fixture(destroyWhenEmpty = true)
        manager.tickReclaim(100)
        assertTrue(sessions(manager).isEmpty())
    }

    @Test fun `an active public minigame survives a vanished player`() {
        val (manager, session) = fixture(serverOwned = true)
        manager.tickReclaim(100)
        assertTrue(session.occupants.isEmpty())
        assertEquals(SessionState.Active, session.state)
        assertEquals(1, sessions(manager).size)
    }

    private fun fixture(destroyWhenEmpty: Boolean = false, serverOwned: Boolean = false): Pair<InstanceManager, InstanceSession> {
        val registry = RegionRegistry(
            RegionListSmall(), RegionListLarge(), unused<LocRegistryNormal>(), CollisionFlagMap(),
            LocZoneStorage(), unused<NpcRegistry>(), unused<ControllerRegistry>(),
            ZonePlayerActivityBitSet(),
        )
        val manager = InstanceManager(RegionRepository(registry), unused<NpcRepository>(), PlayerList(),
            EventBus(), InstanceAreaResolver(), MapClock(100), CollisionFlagMap())
        val area = InstanceArea.copyRegions(12588)
        val session = InstanceSession(InstanceId(1), mutableSetOf(12588), 1, "cleanup",
            InstanceSettings(reclaimTicks = 5, destroyWhenEmpty = destroyWhenEmpty).withArea(area, 0),
            InstancePlacement(RegionLocal(0, 0, 0, 0, 0), CoordGrid.ZERO,
                buildRegionTemplate(area.regionIds, area.level, area.rotation), emptyList(), 0, BaseAreaKey(0, 0, 0)),
            InstanceAccess.Private, isServerOwned = serverOwned)
        session.addOccupant(1)
        sessions(manager)[session.id] = session
        field<MutableMap<InstanceId, Region>>(manager, "regions")[session.id] =
            Region(CoordGrid(3200, 3200), CoordGrid(3264, 3264), 1, 1)
        index<Long>(manager, "ownerIndex")[1] = session.id
        index<Long>(manager, "playerIndex")[1] = session.id
        index<Int>(manager, "regionToInstance")[12588] = session.id
        return manager to session
    }

    private fun sessions(manager: InstanceManager) = field<MutableMap<InstanceId, InstanceSession>>(manager, "sessions")
    private fun <K> index(manager: InstanceManager, name: String) = field<MutableMap<K, InstanceId>>(manager, name)

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(manager: InstanceManager, name: String): T =
        InstanceManager::class.java.getDeclaredField(name).apply { isAccessible = true }.get(manager) as T
    private inline fun <reified T> unused(): T {
        val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        return (field.get(null) as Unsafe).allocateInstance(T::class.java) as T
    }
}
