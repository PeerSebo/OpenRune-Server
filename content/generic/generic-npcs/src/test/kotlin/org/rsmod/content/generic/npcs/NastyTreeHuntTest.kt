package org.rsmod.content.generic.npcs

import dev.openrune.ServerCacheManager
import dev.openrune.types.MoveRestrict
import dev.openrune.types.hunt.HuntCheckNotTooStrong
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.game.process.npc.hunt.AggressionTolerance
import org.rsmod.api.game.process.npc.hunt.NpcHuntProcessor
import org.rsmod.api.game.process.npc.hunt.NpcPlayerHuntProcessor
import org.rsmod.api.hunt.Hunt
import org.rsmod.api.npc.interact.AiLocInteractions
import org.rsmod.api.npc.interact.AiNpcInteractions
import org.rsmod.api.npc.interact.AiObjInteractions
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.loc.LocRegistryRegion
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.player.PlayerRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.route.BoundValidator
import org.rsmod.api.route.RayCastValidator
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NoopNpcInfo
import org.rsmod.game.entity.npc.NpcInfoProtocol
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@OptIn(InternalApi::class)
class NastyTreeHuntTest {
    @Test fun `a nasty tree reacquires a returning player without a new login`() {
        for (symbol in listOf("npc.nasty_tree", "npc.nasty_tree_swamp")) {
            val f = Fixture(symbol)
            f.tick()
            assertNotNull(f.tree.interaction, symbol)
            assertEquals(f.tree.type.huntRange, f.tree.huntRange, symbol)
            assertTrue(f.tree.huntRange > 0, symbol)
            val uid = f.player.uid

            f.leave()
            repeat(8) { f.tick() }
            assertNull(f.tree.interaction, symbol)
            assertEquals(PlayerUid.NULL, f.tree.huntPlayer, symbol)

            f.returnToTree()
            f.tick()
            assertEquals(uid, f.player.uid, "The same player session must be reused")
            assertNotNull(f.tree.interaction, symbol)
            assertTrue(f.tree.huntRange > 0, symbol)
            assertEquals(MoveRestrict.NoMove, f.tree.moveRestrict, symbol)
            assertEquals(0, f.tree.wanderRange, symbol)
        }
    }

    @Test fun `native tree hunt has no combat-level exemption`() {
        for (symbol in listOf("npc.nasty_tree", "npc.nasty_tree_swamp")) {
            val tree = Npc(symbol, Start)
            val hunt = checkNotNull(ServerCacheManager.getHunt(checkNotNull(tree.huntMode)))
            assertTrue(hunt.findKeepHunting, symbol)
            assertNotEquals(HuntCheckNotTooStrong.OutsideWilderness, hunt.checkNotTooStrong, symbol)
        }
    }

    private class Fixture(symbol: String) {
        val world = WorldFixture()
        val playerList = PlayerList()
        val players = PlayerRegistry(playerList, world.collision, ZonePlayerActivityBitSet(), world.events)
        val player = Player().apply {
            coords = Start.translate(1, 0)
            currentMapClock = world.clock.cycle
            processedMapClock = world.clock.cycle
            slotId = 1
            uuid = 1234L
        }
        val tree = world.npc(symbol, Start).apply {
            infoProtocol = object : NpcInfoProtocol by NoopNpcInfo {
                override fun isActive(): Boolean = true
            }
        }
        private val hunt = Hunt(RayCastValidator(world.collision), players, world.npcRegistry,
            ObjRegistry(world.updates), world.locRegistry)
        private val find = NpcPlayerHuntProcessor(DefaultGameRandom(1), world.clock, hunt, AggressionTolerance())
        private val consume = NpcHuntProcessor(DefaultGameRandom(1), world.clock, hunt, playerList, world.npcList,
            AiPlayerInteractions(world.events, playerList), AiNpcInteractions(world.events),
            AiObjInteractions(world.events), AiLocInteractions(BoundValidator(world.collision), world.events))

        init {
            players.add(player)
            players.change(player, ZoneKey.NULL, ZoneKey.from(player.coords))
        }

        fun tick() {
            find.process(tree)
            consume.process(tree)
            world.clock.tick()
        }

        fun leave() {
            tree.clearInteraction()
            tree.resetMovement()
            move(Start.translate(20, 0))
        }

        fun returnToTree() = move(Start.translate(1, 0))

        private fun move(coords: CoordGrid) {
            val old = ZoneKey.from(player.coords)
            player.coords = coords
            players.change(player, old, ZoneKey.from(coords))
        }
    }

    private class WorldFixture {
        val clock = MapClock(100)
        val events = EventBus()
        val collision = CollisionFlagMap()
        val updates = ZoneUpdateMap()
        val storage = LocZoneStorage()
        val npcList = NpcList()
        val npcRegistry = NpcRegistry(npcList, collision, events)
        private val normal = LocRegistryNormal(updates, collision, storage)
        private val regions = RegionRegistry(RegionListSmall(), RegionListLarge(), normal, collision, storage,
            npcRegistry, ControllerRegistry(clock, ControllerList()), ZonePlayerActivityBitSet())
        val locRegistry = LocRegistry(storage, normal, LocRegistryRegion(updates, collision, storage, regions))

        fun npc(symbol: String, coords: CoordGrid): Npc = Npc(symbol, coords).apply {
            for (x in coords.x - 8..coords.x + 24) for (z in coords.z - 8..coords.z + 8)
                collision.allocateIfAbsent(x, z, coords.level)
            npcRegistry.add(this)
        }
    }

    companion object {
        private val Start = CoordGrid(3090, 3360)

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
