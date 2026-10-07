package org.rsmod.content.minigames.tearsofguthix

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import dev.openrune.util.Wearpos
import dev.or2.central.account.Rights
import net.rsprot.protocol.game.incoming.locs.OpLocV2
import net.rsprot.protocol.game.incoming.misc.user.MoveGameClick
import net.rsprot.protocol.game.incoming.misc.user.MoveMinimapClick
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import net.rsprot.protocol.game.outgoing.zone.payload.LocAnim
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.game.process.player.PlayerMovementProcessor
import org.rsmod.api.music.MusicRepository
import org.rsmod.api.net.rsprot.handlers.MoveGameClickHandler
import org.rsmod.api.net.rsprot.handlers.MoveMinimapClickHandler
import org.rsmod.api.net.rsprot.handlers.OpLocHandler
import org.rsmod.api.player.events.PlayerActionCompleteEvent
import org.rsmod.api.player.events.PlayerMoveClick
import org.rsmod.api.player.hook.PlayerRestrictions
import org.rsmod.api.player.hook.RestrictedAction
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.music.MusicPlayer
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.recovery.loginExitCoord
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.setActiveMoveSpeed
import org.rsmod.api.random.GameRandom
import org.rsmod.api.realm.Realm
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.loc.LocRegistryRegion
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.worldentity.WorldEntityRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.BoundValidator
import org.rsmod.api.route.RouteFactory
import org.rsmod.api.route.StepFactory
import org.rsmod.content.quest.manager.QuestRequirementMode
import org.rsmod.content.quest.manager.QuestRequirementPolicy
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.WorldEntityList
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.game.movement.RouteRequestCoord
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.game.seq.EntitySeq
import org.rsmod.game.ui.Component
import org.rsmod.game.ui.UserInterface
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import sun.misc.Unsafe

@ResourceLock("ServerCacheManager")
@Execution(ExecutionMode.SAME_THREAD)
class TearsTest {
    private val originalPolicy = QuestRequirements.activePolicy()

    @AfterEach fun restorePolicy() = QuestRequirements.install(originalPolicy)

    @Test fun `tables resolve native walls and eligibility order`() {
        assertEquals(9, TearsData.walls.size)
        assertEquals(24, TearsData.skills.size)
        assertEquals("stat.attack", TearsData.skills.first().stat)
        assertEquals("stat.sailing", TearsData.skills.last().stat)
        for (wall in TearsData.walls) {
            assertEquals(1, wall.coords.chebyshevDistance(wall.stand))
            assertNotNull(ServerCacheManager.getObject(wall.blue.asRSCM()))
            assertTrue(TearsData.rules.contains(wall.stand))
        }
    }

    @Test fun `persistent eligibility and temporary session variables never transmit`() {
        for (name in listOf(TearsState.HasPlayed, TearsState.LastDay, TearsState.LastQp,
            TearsState.LastXpLow, TearsState.LastXpHigh, TearsState.NotifyDay)) {
            val type = checkNotNull(ServerCacheManager.getVarp(name.asRSCM()))
            assertEquals(VarpLifetime.Perm, type.scope)
            assertEquals(VarpTransmitLevel.Never, type.transmit)
        }
        for (name in listOf(TearsState.Active, TearsState.Start, TearsState.Deadline,
            TearsState.Wall, TearsState.RunMode)) {
            val type = checkNotNull(ServerCacheManager.getVarp(name.asRSCM()))
            assertEquals(VarpLifetime.Temp, type.scope)
        }
    }

    @Test fun `captured reward and hard diary bonus match integer formula`() {
        assertEquals(5154, TearsRewards.experience(136, 7546, false))
        assertEquals(5669, TearsRewards.experience(136, 7546, true))
        assertEquals(1000, TearsRewards.experience(100, 0, false))
        assertEquals(6000, TearsRewards.experience(100, 200000000, false))
        assertEquals(0, TearsRewards.experience(0, 10000, false))
    }

    @Test fun `first game is exempt and weekly clock resets at UTC day boundary`() {
        val p = player()
        assertNull(TearsState.eligibility(p, 200))
        TearsState.rememberReward(p, 200)
        assertNotNull(TearsState.eligibility(p, 206))
        VarPlayerIntMapSetter.set(p, "varp.qp", 1)
        assertNull(TearsState.eligibility(p, 207))
        assertNotNull(TearsState.eligibility(p, 206))
    }

    @Test fun `weekly baseline retains totals beyond signed integer range`() {
        val p = player()
        for (skill in TearsData.skills) p.statMap.setFineXP(skill.stat, 1500000000)
        TearsState.rememberReward(p, 200)
        assertEquals(3600000000L, TearsState.previousXp(p))
        assertNotNull(TearsState.eligibility(p, 207))
        p.statMap.setFineXP("stat.attack", 1501000000)
        assertNull(TearsState.eligibility(p, 207))
        p.statMap.setFineXP("stat.attack", 1499999999)
        assertNotNull(TearsState.eligibility(p, 207))
    }

    @Test fun `reward chooses lowest XP and current native tie order`() {
        QuestRequirements.install(QuestRequirementPolicy())
        val p = player()
        for (skill in TearsData.skills) p.statMap.setFineXP(skill.stat, 1000000)
        p.statMap.setFineXP("stat.attack", 1000)
        p.statMap.setFineXP("stat.strength", 1000)
        assertEquals("stat.attack", TearsRewards.lowest(p)?.stat)
        p.statMap.setFineXP("stat.magic", 999)
        assertEquals("stat.magic", TearsRewards.lowest(p)?.stat)
    }

    @Test fun `construction requires owned house even with optional quests`() {
        QuestRequirements.install(QuestRequirementPolicy())
        val p = player()
        for (skill in TearsData.skills) p.statMap.setFineXP(skill.stat, 1000)
        p.statMap.setFineXP("stat.construction", 0)
        assertNotEquals("stat.construction", TearsRewards.lowest(p)?.stat)
        VarPlayerIntMapSetter.set(p, "varbit.poh_house_location", 1)
        assertEquals("stat.construction", TearsRewards.lowest(p)?.stat)
    }

    @Test fun `stream moves always retain three blue and three green walls`() {
        val streams = TearsStreams(random, 16)
        repeat(400) { cycle ->
            val changed = streams.advance(cycle)
            assertEquals(3, (0..8).count { streams.colour(it) == 1 })
            assertEquals(3, (0..8).count { streams.colour(it) == -1 })
            if (cycle % 16 >= 6) assertTrue(changed.isEmpty())
            assertTrue(streams.advance(cycle).isEmpty())
        }
    }

    @Test fun `optional entry does not grant quest progress or remove actual equipment`() {
        val f = Fixture()
        QuestRequirements.install(QuestRequirementPolicy())
        val p = player()
        p.inv[0] = InvObj("obj.bronze_sword")
        p.inv[1] = InvObj("obj.wooden_shield")
        p.setActiveMoveSpeed(MoveSpeed.Run)
        val before = p.worn.objs.copyOf()
        assertNull(f.enter(p))
        assertEquals(0, p.vars["varp.qp"])
        assertEquals(0, p.vars["varbit.tog_juna_bowl"])
        assertEquals(132, p.vars[TearsState.Countdown])
        assertArrayEquals(before, p.worn.objs)
        assertEquals("obj.bronze_sword".asRSCM(), p.inv[0]?.id)
        assertEquals("obj.wooden_shield".asRSCM(), p.inv[1]?.id)
        assertEquals("obj.tog_bowl".asRSCM(), p.appearance.wornObj(3, before[3])?.id)
        assertNull(p.appearance.wornObj(5, before[5]))
        assertEquals(TearsData.rules.returnCoord, p.loginExitCoord)
        assertEquals("interface.tog_sidepanel".asRSCM(),
            p.ui.overlays[Component("component.toplevel_osrs_stretch:side4".asRSCM())].id)
        f.game.leave(p)
        assertArrayEquals(before, p.worn.objs)
        assertNull(p.bas)
        assertTrue(p.appearance.wornOverrides.isEmpty())
        assertEquals(1, p.vars["varp.option_run"])
        assertEquals(0, p.vars[TearsState.HasPlayed])
        assertNull(p.loginExitCoord)
        assertEquals(EntitySeq.ZERO, p.pendingSequence)
        assertEquals("interface.wornitems".asRSCM(),
            p.ui.overlays[Component("component.toplevel_osrs_stretch:side4".asRSCM())].id)
    }

    @Test fun `strict entry uses real bowl quest progress`() {
        val f = Fixture()
        QuestRequirements.install(QuestRequirementPolicy(QuestRequirementMode.RespectProgress))
        val p = player()
        assertNotNull(f.enter(p))
        assertFalse(TearsState.active(p))
        VarPlayerIntMapSetter.set(p, "varbit.tog_juna_bowl", 2)
        VarPlayerIntMapSetter.set(p, "varp.qp", 226)
        assertNull(f.enter(p))
        assertEquals(226, p.vars[TearsState.Countdown])
        f.game.leave(p)
        assertEquals(2, p.vars["varbit.tog_juna_bowl"])
    }

    @Test fun `entry requires both free hands without removing equipment`() {
        val f = Fixture()
        val p = player()
        p.worn[Wearpos.RightHand.slot] = InvObj("obj.bronze_sword")
        assertNotNull(f.enter(p))
        p.worn[Wearpos.RightHand.slot] = null
        p.worn[Wearpos.LeftHand.slot] = InvObj("obj.wooden_shield")
        assertNotNull(f.enter(p))
        assertFalse(TearsState.active(p))
        assertNotNull(p.worn[Wearpos.LeftHand.slot])
    }

    @Test fun `manual logout ends collection and waits until after drink reward`() {
        val f = Fixture()
        val p = player()
        for (skill in TearsData.skills) p.statMap.setBaseLevel(skill.stat, 99)
        assertNull(f.enter(p))
        TearsState.set(p, TearsState.Collected, 10)
        f.game.logoutRequest(p)
        f.game.logoutRequest(p)
        assertEquals(2, p.vars[TearsState.Active])
        assertFalse(p.manualLogout)
        assertEquals(0, p.vars[TearsState.HasPlayed])
        f.returnToJuna(p)
        assertEquals(3, p.vars[TearsState.Active])
        f.clock.cycle += 2
        f.game.tick()
        assertTrue(p.manualLogout)
        assertEquals(1, p.vars[TearsState.HasPlayed])
        assertFalse(TearsState.active(p))
    }

    @Test fun `shutdown restores all active players without unclaimed rewards`() {
        val f = Fixture()
        val a = player()
        val b = player()
        assertNull(f.enter(a))
        assertNull(f.enter(b))
        TearsState.set(a, TearsState.Collected, 200)
        f.game.shutdown()
        for (p in listOf(a, b)) {
            assertFalse(TearsState.active(p))
            assertNull(p.bas)
            assertNull(p.loginExitCoord)
            assertEquals(0, p.vars[TearsState.HasPlayed])
        }
    }

    @Test fun `spam collection cannot award more than once per game cycle`() {
        val f = Fixture()
        val p = player()
        assertNull(f.enter(p))
        p.coords = TearsData.walls[3].stand
        f.game.collect(p, 3)
        val deadline = p.vars[TearsState.Deadline]
        repeat(20) { f.game.collect(p, 3); f.game.tick() }
        assertEquals(1, p.vars[TearsState.Collected])
        assertEquals(deadline, p.vars[TearsState.Deadline])
        f.game.leave(p)
    }

    @Test fun `green tears do not make the bowl negative and moving stops collection`() {
        val f = Fixture()
        val p = player()
        assertNull(f.enter(p))
        p.coords = TearsData.walls[1].stand
        assertTrue(f.game.checkWall(p, 1))
        f.game.collect(p, 1)
        f.game.tick()
        assertEquals(0, p.vars[TearsState.Collected])
        p.coords = p.coords.translateX(-1)
        f.clock.tick()
        f.game.tick()
        assertEquals(0, p.vars[TearsState.Wall])
        assertEquals(0, p.vars["varbit.tog_minigame_collecting"])
        f.game.leave(p)
    }

    @Test fun `empty wall refuses collection and cannot start when a stream arrives`() {
        val f = Fixture()
        val p = player()
        assertNull(f.enter(p))
        p.coords = TearsData.walls[0].stand
        val deadline = p.vars[TearsState.Deadline]
        assertEquals(EntitySeq.ZERO, p.pendingSequence)
        f.game.collect(p, 0)
        assertEquals(EntitySeq.ZERO, p.pendingSequence)
        assertEquals(0, p.vars[TearsState.Wall])
        assertEquals(0, p.vars["varbit.tog_minigame_collecting"])
        assertEquals(deadline, p.vars[TearsState.Deadline])
        val messages = (p.client as RecordingClient).messages.filterIsInstance<MessageGame>()
        assertEquals("There are no tears to collect from that part of the wall.", messages.last().message)
        var streamArrived = false
        repeat(16) {
            f.step(p)
            if (f.game.checkWall(p, 0)) streamArrived = true
            assertEquals(0, p.vars[TearsState.Wall])
            assertEquals(0, p.vars["varbit.tog_minigame_collecting"])
            assertEquals(0, p.vars[TearsState.Collected])
        }
        assertTrue(streamArrived)
        f.game.leave(p)
    }

    @Test fun `arrival rejects a wall whose tears moved after the click`() {
        val f = Fixture()
        val p = player()
        assertNull(f.enter(p))
        assertTrue(f.game.checkWall(p, 3))
        p.coords = TearsData.walls[3].stand
        f.clock.cycle = 114
        f.game.tick()
        f.game.collect(p, 3)
        assertEquals(EntitySeq.ZERO, p.pendingSequence)
        assertEquals(0, p.vars[TearsState.Wall])
        assertEquals(0, p.vars["varbit.tog_minigame_collecting"])
        assertEquals("There are no tears to collect from that part of the wall.",
            (p.client as RecordingClient).messages.filterIsInstance<MessageGame>().last().message)
        f.game.leave(p)
    }

    @Test fun `drying stream stops collection and returning tears cannot restart it`() {
        val f = Fixture()
        val p = player()
        assertNull(f.enter(p))
        p.coords = TearsData.walls[3].stand
        f.game.collect(p, 3)
        assertEquals(4, p.vars[TearsState.Wall])
        f.clock.cycle = 114
        f.game.tick()
        assertEquals(0, p.vars[TearsState.Wall])
        assertEquals(0, p.vars["varbit.tog_minigame_collecting"])
        assertEquals("seq.tog_lean_back_bowl".asRSCM(), p.pendingSequence.id)
        val count = p.vars[TearsState.Collected]
        var streamReturned = false
        repeat(32) {
            f.step(p)
            if (f.game.checkWall(p, 3)) streamReturned = true
            assertEquals(0, p.vars[TearsState.Wall])
            assertEquals(count, p.vars[TearsState.Collected])
        }
        assertTrue(streamReturned)
        f.game.leave(p)
    }

    @Test fun `disconnect cleans every session phase before logout and forfeits rewards`() {
        for (phase in 1..5) for (reason in 0..2) {
            val f = Fixture()
            val p = player()
            assertNull(f.enter(p))
            TearsState.set(p, TearsState.Active, phase)
            TearsState.set(p, TearsState.Collected, 100)
            p.forcedRoute = true
            p.walk(TearsData.rules.entryRoute[1])
            when (reason) {
                0 -> p.clientDisconnected.set(true)
                1 -> p.forceDisconnect = true
                2 -> p.pendingShutdown = true
            }
            f.game.enforceWalk()
            assertFalse(TearsState.active(p))
            assertEquals(TearsData.rules.returnCoord, p.coords)
            assertFalse(p.forcedRoute)
            assertTrue(p.routeDestination.isEmpty())
            assertNull(p.bas)
            assertTrue(p.appearance.wornOverrides.isEmpty())
            assertEquals(0, p.vars["varbit.tog_minigame_collecting"])
            assertEquals(0, p.vars[TearsState.Collected])
            assertEquals(0, p.vars[TearsState.HasPlayed])
            assertNull(p.loginExitCoord)
        }
    }

    @Test fun `login recovers orphaned entrance and return passage without collecting flag`() {
        val f = Fixture()
        for (tile in TearsData.rules.entryRoute + TearsData.walls.map { it.stand }) {
            val p = player()
            p.coords = tile
            p.forcedRoute = true
            p.walk(TearsData.rules.entryRoute.last())
            f.game.login(p)
            assertEquals(TearsData.rules.returnCoord, p.coords)
            assertFalse(p.forcedRoute)
            assertTrue(p.routeDestination.isEmpty())
            assertEquals(0, p.vars[TearsState.Collected])
            assertEquals(0, p.vars[TearsState.HasPlayed])
        }
    }

    @Test fun `current tile game and minimap clicks stop collection without changing position`() {
        val f = Fixture()
        val p = player()
        assertNull(f.enter(p))
        p.coords = TearsData.walls[3].stand
        p.processedMapClock = f.clock.cycle
        val stand = p.coords
        repeat(2) { input ->
            f.game.collect(p, 3)
            assertEquals(4, p.vars[TearsState.Wall])
            if (input == 0) f.gameClick.handle(p, MoveGameClick(stand.x, stand.z, 0))
            else f.minimapClick.handle(p, MoveMinimapClick(stand.x, stand.z, 0, 0, 0, 0, 0, 0))
            assertEquals(stand, p.coords)
            assertEquals(0, p.vars[TearsState.Wall])
            assertEquals(0, p.vars["varbit.tog_minigame_collecting"])
            assertEquals("seq.tog_lean_back_bowl".asRSCM(), p.pendingSequence.id)
            p.routeRequest = null
        }
        repeat(16) { f.step(p) }
        assertEquals(stand, p.coords)
        assertEquals(0, p.vars[TearsState.Collected])
        assertEquals(0, p.vars[TearsState.Wall])
        f.game.leave(p)
    }

    @Test fun `timer starts after capped approach period when player never collects`() {
        val f = Fixture()
        val p = player()
        assertNull(f.enter(p))
        f.clock.cycle += TearsData.rules.approachTicks
        f.game.tick()
        assertEquals(f.clock.cycle + 132, p.vars[TearsState.Deadline])
        f.clock.cycle += 132
        f.game.tick()
        assertEquals(2, p.vars[TearsState.Active])
        f.game.leave(p)
    }

    @Test fun `completed session rewards once and records exact diary count`() {
        val f = Fixture()
        val p = player()
        for (skill in TearsData.skills) {
            p.statMap.setFineXP(skill.stat, 1000000)
            p.statMap.setBaseLevel(skill.stat, 99)
            p.statMap.setCurrentLevel(skill.stat, 99)
        }
        p.statMap.setFineXP("stat.sailing", 75460)
        val actions = mutableListOf<PlayerActionCompleteEvent>()
        f.events.subscribeUnbound(PlayerActionCompleteEvent::class.java) { actions += this }
        assertNull(f.enter(p))
        TearsState.set(p, TearsState.Collected, 136)
        TearsState.set(p, TearsState.Deadline, f.clock.cycle)
        f.game.tick()
        f.returnToJuna(p)
        assertEquals(3, p.vars[TearsState.Active])
        f.clock.cycle += 2
        f.game.tick()
        assertEquals(12700, p.statMap.getXP("stat.sailing"))
        assertEquals(136, actions.single().count)
        assertEquals(1, p.vars[TearsState.HasPlayed])
        assertEquals(136, p.vars[TearsState.Best])
        assertFalse(TearsState.active(p))
        f.clock.tick()
        f.game.tick()
        assertEquals(1, actions.size)
        assertEquals(12700, p.statMap.getXP("stat.sailing"))
    }

    @Test fun `logout forfeits unclaimed tears and allows a clean fresh entry`() {
        val f = Fixture()
        val p = player()
        assertNull(f.enter(p))
        TearsState.set(p, TearsState.Collected, 200)
        f.game.leave(p)
        f.game.login(p)
        assertEquals(0, p.vars[TearsState.Collected])
        assertEquals(0, p.vars[TearsState.HasPlayed])
        assertNull(f.enter(p))
        f.game.leave(p)
    }

    @Test fun `recovery clears orphan native collection state without rewards`() {
        val f = Fixture()
        val p = player()
        p.coords = TearsData.walls[3].stand
        TearsState.set(p, "varbit.tog_minigame_collecting", 1)
        TearsState.set(p, TearsState.Collected, 90)
        f.game.login(p)
        assertEquals(TearsData.rules.returnCoord, p.coords)
        assertEquals(0, p.vars[TearsState.Collected])
        assertEquals(0, p.vars[TearsState.HasPlayed])
    }

    @Test fun `simultaneous players have independent bowls and deadlines`() {
        val f = Fixture()
        val a = player()
        val b = player()
        VarPlayerIntMapSetter.set(b, "varp.qp", 226)
        assertNull(f.enter(a))
        assertNull(f.enter(b))
        for (p in listOf(a, b)) {
            p.coords = TearsData.walls[3].stand
            f.game.collect(p, 3)
        }
        f.game.tick()
        assertEquals(1, a.vars[TearsState.Collected])
        assertEquals(1, b.vars[TearsState.Collected])
        assertNotEquals(a.vars[TearsState.Deadline], b.vars[TearsState.Deadline])
        f.game.leave(a)
        assertTrue(TearsState.active(b))
        f.game.leave(b)
    }

    @Test fun `collection permits walking but prevents item and drink actions`() {
        val p = player()
        val hook = TearsRestrictions()
        TearsState.set(p, TearsState.Active, 1)
        assertNull(hook.restriction(p, RestrictedAction.Walk))
        assertNotNull(hook.restriction(p, RestrictedAction.Drink))
        TearsState.set(p, TearsState.Active, 2)
        assertNotNull(hook.restriction(p, RestrictedAction.Walk))
        TearsState.clear(p)
        assertNull(hook.restriction(p, RestrictedAction.Drink))
    }

    @Test fun `ctrl clicks still walk a single tile while holding the bowl`() {
        val f = Fixture()
        val p = player()
        assertNull(f.enter(p))
        p.tempMoveSpeed = MoveSpeed.Run
        p.routeRequest = RouteRequestCoord(p.coords.translateX(5))
        val movement = PlayerMovementProcessor(f.collision, RouteFactory(f.collision),
            StepFactory(f.collision), f.events, setOf(TearsRestrictions()))
        val start = p.coords
        movement.process(p)
        assertEquals(1, start.chebyshevDistance(p.coords))
        assertEquals(MoveSpeed.Walk, p.moveSpeed)
        f.game.leave(p)
    }

    @Test fun `entry and exit follow each waypoint and pause before passing beneath Juna`() {
        val f = Fixture()
        val p = player()
        assertNull(f.game.enter(p))
        assertEquals(4, p.vars[TearsState.Active])
        assertTrue(p.forcedRoute)
        f.game.tick()
        val start = p.coords
        repeat(TearsData.rules.tailPauseTicks) {
            f.step(p)
            assertEquals(start, p.coords)
        }
        val entry = mutableListOf(p.coords)
        while (p.vars[TearsState.Active] == 4) {
            f.step(p)
            if (entry.last() != p.coords) entry += p.coords
        }
        assertEquals(TearsData.rules.entryRoute, entry)
        assertFalse(p.forcedRoute)
        assertEquals(0, p.vars[TearsState.Deadline])
        assertEquals(1, f.tailAnimations())
        f.game.logoutRequest(p)
        val exit = mutableListOf(p.coords)
        var paused = 0
        while (p.vars[TearsState.Active] == 2) {
            val before = p.coords
            f.step(p)
            if (p.coords == before && p.coords == TearsData.rules.entryRoute[2]) paused++
            if (exit.last() != p.coords) exit += p.coords
        }
        assertEquals(TearsData.rules.entryRoute.asReversed(), exit)
        assertTrue(paused >= TearsData.rules.tailPauseTicks)
        assertFalse(p.forcedRoute)
        assertEquals(2, f.tailAnimations())
        assertEquals(5, p.vars[TearsState.Active])
        assertNotEquals("seq.tog_drink_bowl".asRSCM(), p.pendingSequence.id)
        f.step(p)
        assertEquals(3, p.vars[TearsState.Active])
        assertEquals("seq.tog_drink_bowl".asRSCM(), p.pendingSequence.id)
        f.game.leave(p)
    }

    @Test fun `world and minimap clicks cannot replace the protected passage or drink`() {
        val f = Fixture()
        val p = player()
        assertNull(f.game.enter(p))
        fun spamClicks() {
            f.game.enforceWalk()
            assertTrue(p.isDelayed)
            val route = p.routeDestination.toList()
            repeat(10) {
                f.gameClick.handle(p, MoveGameClick(3260, 9517, 0))
                f.minimapClick.handle(p, MoveMinimapClick(3260, 9518, 0, 0, 0, 0, 0, 0))
                f.locClick.handle(p, OpLocV2("loc.tog_weepingwall".asRSCM(), 3261, 9517, false, 1, 0))
            }
            assertEquals(route, p.routeDestination.toList())
            assertNull(p.routeRequest)
            assertNull(p.interaction)
        }
        while (p.vars[TearsState.Active] == 4) { spamClicks(); f.step(p) }
        f.game.logoutRequest(p)
        while (p.vars[TearsState.Active] in listOf(2, 5, 3)) { spamClicks(); f.step(p) }
        assertFalse(TearsState.active(p))
        p.processedMapClock = ++f.clock.cycle
        p.currentMapClock = f.clock.cycle
        assertFalse(p.isDelayed)
        f.gameClick.handle(p, MoveGameClick(3251, 9517, 0))
        assertNotNull(p.routeRequest)
    }

    @Test fun `entry and return preserve delays belonging to other actions`() {
        val f = Fixture()
        val p = player()
        p.delay = f.clock.cycle + 500
        val previous = p.delay
        assertNull(f.game.enter(p))
        f.game.enforceWalk()
        assertEquals(previous, p.delay)
        f.game.leave(p)
        assertEquals(previous, p.delay)
    }

    @Test fun `eligible login sends the captured red message once and respects disabled notices`() {
        val f = Fixture()
        fun eligible() = player().apply {
            TearsState.rememberReward(this, TearsState.day() - 7)
            statMap.setFineXP("stat.attack", 1000000)
        }
        val p = eligible()
        f.game.login(p)
        f.game.login(p)
        val messages = (p.client as RecordingClient).messages.filterIsInstance<MessageGame>()
        assertEquals(1, messages.size)
        assertEquals(0, messages.single().type)
        assertEquals("<col=ef1020>You are eligible to drink from the Tears of Guthix.</col>",
            messages.single().message)
        val disabled = eligible()
        TearsState.set(disabled, "varbit.tog_helper_disabled", 1)
        f.game.login(disabled)
        assertTrue((disabled.client as RecordingClient).messages.filterIsInstance<MessageGame>().isEmpty())
    }

    @Test fun `crossing timeout removes forced movement and forfeits pending reward`() {
        val f = Fixture()
        val p = player()
        assertNull(f.game.enter(p))
        f.clock.cycle += TearsData.rules.crossingTimeoutTicks
        f.game.tick()
        assertFalse(TearsState.active(p))
        assertFalse(p.forcedRoute)
        assertTrue(p.routeDestination.isEmpty())
        assertEquals(0, p.vars[TearsState.HasPlayed])
    }

    @Test fun `reset command enforces administrator rights and keeps best score and quests`() {
        val commands = CheatCommandMap()
        with(TearsCommands()) { ScriptContext(EventBus(), commands, EngineQueueCache()).startup() }
        val p = player()
        TearsState.rememberReward(p)
        TearsState.set(p, TearsState.Best, 100)
        val handler = checkNotNull(commands["togreset"])
        assertEquals(Rights.ADMINISTRATOR, handler.requiredRights)
        handler.action(Cheat(p, "togreset", emptyList()))
        assertEquals(1, p.vars[TearsState.HasPlayed])
        p.modLevel = Rights.ADMINISTRATOR
        handler.action(Cheat(p, "togreset", listOf("extra")))
        assertEquals(1, p.vars[TearsState.HasPlayed])
        TearsState.set(p, TearsState.Active, 1)
        handler.action(Cheat(p, "togreset", emptyList()))
        assertEquals(1, p.vars[TearsState.HasPlayed])
        TearsState.clear(p)
        handler.action(Cheat(p, "togreset", emptyList()))
        assertNull(TearsState.eligibility(p))
        assertEquals(0, p.vars[TearsState.HasPlayed])
        assertEquals(100, p.vars[TearsState.Best])
        assertEquals(0, p.vars["varp.qp"])
        assertEquals(0, p.vars["varbit.tog_juna_bowl"])
    }

    private class Fixture {
        val clock = MapClock(100)
        val events = EventBus()
        val collision = CollisionFlagMap()
        private val storage = LocZoneStorage()
        private val updates = ZoneUpdateMap()
        private val npcs = NpcRegistry(NpcList(), collision, events)
        private val normal = LocRegistryNormal(updates, collision, storage)
        private val regions = RegionRegistry(RegionListSmall(), RegionListLarge(), normal, collision,
            storage, npcs, ControllerRegistry(clock, ControllerList()), ZonePlayerActivityBitSet())
        private val locRegistry = LocRegistry(storage, normal, LocRegistryRegion(updates, collision, storage, regions))
        private val locs = LocRepository(clock, locRegistry, regions)
        private val repository = MusicRepository(random).apply { load() }
        private val music = MusicPlayer::class.java.getDeclaredConstructor(GameRandom::class.java,
            MusicRepository::class.java).newInstance(random, repository)
        val game = TearsGame(clock, locs, collision, events, music, WorldRepository(updates), random)
        private val movement = PlayerMovementProcessor(collision, RouteFactory(collision),
            StepFactory(collision), events, setOf(TearsRestrictions()))
        private val contextFactory = run {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
            (field.get(null) as Unsafe).allocateInstance(ProtectedAccessContextFactory::class.java)
                as ProtectedAccessContextFactory
        }
        private val restrictions = PlayerRestrictions(setOf(TearsRestrictions()))
        val gameClick = MoveGameClickHandler(Realm("test"), events, collision, contextFactory, restrictions)
        val minimapClick = MoveMinimapClickHandler(Realm("test"), events, collision, contextFactory, restrictions)
        val locClick = OpLocHandler(events, locRegistry, LocInteractions(BoundValidator(collision), events),
            WorldEntityRegistry(WorldEntityList(), events), ProtectedAccessLauncher(contextFactory))

        fun tailAnimations() = updates.updatedZones.values.flatMap { it.protList }
            .filterIsInstance<LocAnim>().count { it.id == "seq.tog_snake_openclose".asRSCM() }

        fun step(player: Player) {
            clock.tick()
            player.currentMapClock = clock.cycle
            player.processedMapClock = clock.cycle - 1
            game.enforceWalk()
            movement.process(player)
            game.tick()
        }

        fun enter(player: Player): String? {
            val reason = game.enter(player)
            if (reason == null) {
                repeat(20) { if (player.vars[TearsState.Active] == 4) step(player) }
                assertEquals(1, player.vars[TearsState.Active])
                clock.tick()
                player.currentMapClock = clock.cycle
                player.processedMapClock = clock.cycle - 1
            }
            return reason
        }

        fun returnToJuna(player: Player) {
            repeat(30) { if (player.vars[TearsState.Active] in listOf(2, 5)) step(player) }
        }
        init {
            events.subscribeUnbound(PlayerMoveClick::class.java) { game.move(this) }
            for (x in 3240..3265) for (z in 9508..9526) collision.allocateIfAbsent(x, z, 2)
            locs.add(TearsData.rules.entryRoute[1], "loc.tog_juna", Int.MAX_VALUE,
                LocAngle.values().single { it.id == 1 }, LocShape.CentrepieceStraight)
            QuestRequirements.install(QuestRequirementPolicy())
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
        @JvmStatic @BeforeAll fun loadCache() { ServerCacheManager.init(241).close() }
        private val random = object : GameRandom {
            override fun of(maxExclusive: Int) = 0
            override fun of(minInclusive: Int, maxInclusive: Int) = minInclusive
            override fun randomDouble() = 0.0
        }
        private fun player() = Player().apply {
            currentMapClock = 100
            processedMapClock = 99
            client = RecordingClient()
            coords = TearsData.rules.entry
            inv = Inventory.create("inv.inv")
            worn = Inventory.create("inv.worn")
            ui.topLevel = UserInterface("interface.toplevel_osrs_stretch".asRSCM())
            ui.overlays[Component("component.toplevel_osrs_stretch:side4".asRSCM())] =
                UserInterface("interface.wornitems".asRSCM())
        }
    }
}
