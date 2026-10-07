package org.rsmod.content.areas.wilderness

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.area.AreaIndex
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class WildernessLeverRestrictionTest {
    @Test fun `lever can leave deep Wilderness while spells and jewellery cannot`() {
        val f = Fixture()
        assertNull(f.validate(TeleportType.Lever))
        assertNotNull(f.validate(TeleportType.Standard))
        assertNotNull(f.validate(TeleportType.MemberLevel30))
    }

    @Test fun `lever retains teleblock and recent PvP special restrictions`() {
        val f = Fixture()
        VarPlayerIntMapSetter.set(f.player, "varbit.teleblock_cycles", 50)
        assertEquals("A teleport block has been cast on you.", f.validate(TeleportType.Lever))
        VarPlayerIntMapSetter.set(f.player, "varbit.teleblock_cycles", 0)
        f.player.wildernessLastSpecTick = f.player.currentMapClock
        assertEquals("You cannot use teleports so soon after using a special attack.", f.validate(TeleportType.Lever))
    }

    @Test fun `lever does not bypass unrelated area protection hooks`() {
        val f = Fixture()
        val validator = PlayerTeleportValidator(setOf(
            WildernessTeleportHook(), PlayerTeleportValidateHook { _, _, _ -> "You cannot leave this fight." },
        ))
        assertEquals("You cannot leave this fight.", validator.validate(f.player, TeleportType.Lever, f.areas))
    }

    @Test fun `special attack at tick zero blocks exactly the existing interval and supports reset`() {
        val f = Fixture()
        f.player.currentMapClock = 0
        assertNull(f.validate(TeleportType.Lever))
        WildernessSpecialAttackHook().onPlayerSpecialAttack(f.player, Player())
        assertEquals(0, f.player.wildernessLastSpecTick)
        for (tick in 0..3) {
            f.player.currentMapClock = tick
            assertEquals("You cannot use teleports so soon after using a special attack.", f.validate(TeleportType.Lever))
        }
        f.player.currentMapClock = 4
        assertNull(f.validate(TeleportType.Lever))
        WildernessSpecialAttackHook().onPlayerSpecialAttack(f.player, Player())
        f.player.wildernessLastSpecTick = null
        assertNull(f.validate(TeleportType.Lever))
    }
    private class Fixture {
        val player = Player().apply { coords = CoordGrid(3154, 3924); currentMapClock = 100 }
        private val collision = CollisionFlagMap()
        private val storage = LocZoneStorage()
        private val normal = LocRegistryNormal(ZoneUpdateMap(), collision, storage)
        private val regions = RegionRegistry(RegionListSmall(), RegionListLarge(), normal, collision, storage,
            NpcRegistry(NpcList(), collision, EventBus()), ControllerRegistry(MapClock(100), ControllerList()), ZonePlayerActivityBitSet())
        val areas = AreaChecker(regions, AreaIndex().apply {
            registerAll(player.coords, listOf("area.wilderness".asRSCM().toShort()).iterator())
        })
        private val validator = PlayerTeleportValidator(setOf(WildernessTeleportHook()))
        fun validate(type: TeleportType) = validator.validate(player, type, areas)
    }
    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
