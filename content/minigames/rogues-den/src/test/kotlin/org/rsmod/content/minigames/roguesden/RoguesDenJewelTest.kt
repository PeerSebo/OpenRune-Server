package org.rsmod.content.minigames.roguesden

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.events.PlayerQueueEvents
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.loc.LocRegistryRegion
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.route.BoundValidator
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.minigames.roguesden.configs.RoguesDenExit
import org.rsmod.content.minigames.roguesden.configs.RoguesDenMaze
import org.rsmod.content.minigames.roguesden.configs.RoguesDenSafes
import org.rsmod.content.minigames.roguesden.configs.RoguesDenState.grantedMazeAccess
import org.rsmod.content.minigames.roguesden.configs.RoguesDenState.inRoguesDenMaze
import org.rsmod.content.minigames.roguesden.scripts.MazeSafeScript
import org.rsmod.content.minigames.roguesden.scripts.MysticJewelScript
import org.rsmod.content.minigames.roguesden.scripts.RoguesDenStateScript
import org.rsmod.events.EventBus
import org.rsmod.events.SuspendEvent
import org.rsmod.game.MapClock
import org.rsmod.game.area.AreaIndex
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class RoguesDenJewelTest {
    @Test
    fun `logout restores stats and saves the lobby rather than leaving a disabled maze run`() {
        val fixture = Fixture()
        fixture.player.grantedMazeAccess = true
        fixture.player.inv.objs.fill(InvObj("obj.logs"))
        fixture.player.inv[0] = InvObj(RoguesDenMaze.MYSTIC_JEWEL)

        fixture.events.publish(SessionStateEvent.Logout(fixture.player))

        fixture.assertExited()
        assertEquals(27, fixture.player.inv.count("obj.logs"))
        assertTrue(fixture.player.grantedMazeAccess)
        fixture.events.publish(SessionStateEvent.Login(fixture.player))
        fixture.assertExited()
        assertTrue(fixture.player.grantedMazeAccess)
    }

    @Test
    fun `login repairs a stranded legacy maze save whose temporary participation flag is absent`() {
        val fixture = Fixture()
        fixture.player.inRoguesDenMaze = false
        fixture.player.inv[0] = InvObj(RoguesDenMaze.MYSTIC_JEWEL)
        fixture.player.inv[1] = InvObj(RoguesDenMaze.MYSTIC_JEWEL)
        fixture.player.inv[2] = InvObj("obj.logs")

        fixture.events.publish(SessionStateEvent.Login(fixture.player))

        fixture.assertExited()
        assertEquals(1, fixture.player.inv.count("obj.logs"))
        assertEquals(50, fixture.player.statMap.getCurrentLevel("stat.agility").toInt())
        assertEquals(50, fixture.player.statMap.getCurrentLevel("stat.prayer").toInt())
    }

    @Test
    fun `ordinary lobby and other-plane logins retain their location and stat levels`() {
        val fixture = Fixture()
        fixture.player.inRoguesDenMaze = false
        for (coords in listOf(CoordGrid(3061, 4985, 1), CoordGrid(3056, 4991, 1), CoordGrid(3049, 4996, 0))) {
            fixture.player.coords = coords
            fixture.player.inv[0] = InvObj(RoguesDenMaze.MYSTIC_JEWEL)

            fixture.events.publish(SessionStateEvent.Login(fixture.player))

            assertEquals(coords, fixture.player.coords)
            assertEquals(30, fixture.player.statMap.getCurrentLevel("stat.thieving").toInt())
            assertEquals(0, fixture.player.inv.count(RoguesDenMaze.MYSTIC_JEWEL))
            assertFalse(fixture.player.inRoguesDenMaze)
        }
    }

    @Test
    fun `entry normalization retains exactly one jewel even from a full legacy inventory`() {
        val fixture = Fixture()
        fixture.player.inv.objs.fill(InvObj(RoguesDenMaze.MYSTIC_JEWEL))

        repeat(3) { RoguesDenExit.ensureSingleJewel(fixture.player) }

        assertEquals(1, fixture.player.inv.count(RoguesDenMaze.MYSTIC_JEWEL))
        assertEquals(27, fixture.player.inv.freeSpace())
    }

    @Test
    fun `jewel activation clears duplicate jewels from a full inventory before leaving`() {
        val fixture = Fixture()
        fixture.player.inv.objs.fill(InvObj("obj.logs"))
        fixture.player.inv[0] = InvObj(RoguesDenMaze.MYSTIC_JEWEL)
        fixture.player.inv[27] = InvObj(RoguesDenMaze.MYSTIC_JEWEL)

        fixture.activateJewel()

        fixture.assertExited()
        assertEquals(26, fixture.player.inv.count("obj.logs"))
        assertEquals(2, fixture.player.inv.freeSpace())
    }

    @Test
    fun `failed maze safe ejects the player and removes every jewel without disturbing other items`() {
        val fixture = Fixture(failSafe = true)
        fixture.player.inv.objs.fill(InvObj("obj.logs"))
        fixture.player.inv[0] = InvObj(RoguesDenMaze.MYSTIC_JEWEL)
        fixture.player.inv[27] = InvObj(RoguesDenMaze.MYSTIC_JEWEL)

        val safe = fixture.crackSafe()
        assertEquals(safe.coords, fixture.player.pendingFaceSquare)
        assertEquals(RoguesDenSafes.SAFE_CRACK_ANIM.asRSCM(), fixture.player.pendingSequence.id)
        fixture.finishSafe()

        fixture.assertExited()
        assertEquals(26, fixture.player.inv.count("obj.logs"))
        assertEquals(0, fixture.player.inv.count(RoguesDenMaze.ROGUE_KIT))
        assertEquals(0, fixture.player.inv.count(RoguesDenMaze.ROGUE_CRATE))
    }

    @Test
    fun `safe reward uses the departing jewel slot when the inventory is full`() {
        val fixture = Fixture()
        fixture.player.inv.objs.fill(InvObj("obj.logs"))
        fixture.player.inv[0] = InvObj(RoguesDenMaze.MYSTIC_JEWEL)

        fixture.crackSafe()
        fixture.finishSafe()

        fixture.assertExited()
        assertEquals(27, fixture.player.inv.count("obj.logs"))
        assertEquals(1, fixture.player.inv.count(RoguesDenMaze.ROGUE_KIT))
    }

    private class Fixture(failSafe: Boolean = false) {
        val events = EventBus()
        private val clock = MapClock(100)
        private val collision = CollisionFlagMap()
        private val updates = ZoneUpdateMap()
        private val storage = LocZoneStorage()
        private val normal = LocRegistryNormal(updates, collision, storage)
        private val npcs = NpcList()
        private val regions = RegionRegistry(
            RegionListSmall(), RegionListLarge(), normal, collision, storage,
            NpcRegistry(npcs, collision, events),
            ControllerRegistry(clock, ControllerList()), ZonePlayerActivityBitSet(),
        )
        private val locs = LocRepository(
            clock, LocRegistry(storage, normal, LocRegistryRegion(updates, collision, storage, regions)),
            regions,
        )
        private val interactions = LocInteractions(BoundValidator(collision), events)
        val player = Player().apply {
            inv = Inventory.create("inv.inv")
            worn = Inventory.create("inv.worn")
            coords = CoordGrid(3049, 4996, 1)
            currentMapClock = 100
            processedMapClock = 100
            inRoguesDenMaze = true
            for (stat in listOf("stat.agility", "stat.thieving", "stat.prayer")) {
                statMap.setBaseLevel(stat, 50)
                statMap.setCurrentLevel(stat, 30)
            }
        }
        private val random = object : GameRandom {
            override fun of(maxExclusive: Int) = 0
            override fun of(minInclusive: Int, maxInclusive: Int) =
                if (failSafe) maxInclusive else minInclusive
            override fun randomDouble() = 0.0
        }
        private val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { events },
            getRandom = { random },
            getCollision = { collision },
            getAreaChecker = { AreaChecker(regions, AreaIndex()) },
            getTeleportValidator = { PlayerTeleportValidator(emptySet()) },
        )

        init {
            collision.allocateIfAbsent(3049, 4976, 1)
            collision.allocateIfAbsent(3049, 4996, 1)
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(MazeSafeScript(locs, XpModifiers(emptySet()))) { scripts.startup() }
            with(MysticJewelScript(ObjRepository(clock, ObjRegistry(updates)))) { scripts.startup() }
            with(RoguesDenStateScript(collision)) { scripts.startup() }
        }

        fun crackSafe(): BoundLocInfo {
            val info = locs.add(
                player.coords.translateZ(1), RoguesDenSafes.MAZE_SAFE, Int.MAX_VALUE,
                LocAngle.West, LocShape.CentrepieceStraight,
            )
            val safe = BoundLocInfo(info, checkNotNull(ServerCacheManager.getObject(info.id)))
            dispatch(checkNotNull(interactions.opTrigger(player, safe, InteractionOp.Op1)))
            assertEquals(1, player.queueList.count(RoguesDenSafes.SAFE_CRACK_QUEUE))
            return safe
        }

        fun finishSafe() {
            val iterator = checkNotNull(player.queueList.iterator())
            val queue = iterator.next()
            iterator.remove()
            iterator.cleanUp()
            player.currentMapClock += queue.remainingCycles
            player.processedMapClock = player.currentMapClock
            dispatch(PlayerQueueEvents.Protected(queue.args, queue.id))
        }

        fun activateJewel() {
            val obj = checkNotNull(player.inv[0])
            val type = checkNotNull(ServerCacheManager.getItem(obj.id))
            dispatch(HeldObjEvents.Op1(0, obj, type, player.inv))
        }

        private fun dispatch(event: SuspendEvent<ProtectedAccess>) {
            val coroutine = player.launch {
                assertTrue(events.publish(ProtectedAccess(player, this, context), event))
            }
            assertTrue(coroutine.isIdle)
        }

        fun assertExited() {
            assertEquals(RoguesDenExit.LOBBY_LANDING, player.coords)
            assertFalse(player.inRoguesDenMaze)
            assertEquals(0, player.inv.count(RoguesDenMaze.MYSTIC_JEWEL))
            assertEquals(50, player.statMap.getCurrentLevel("stat.thieving").toInt())
        }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun cache() {
            ServerCacheManager.init(241).close()
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
                ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
            }
        }
    }
}
