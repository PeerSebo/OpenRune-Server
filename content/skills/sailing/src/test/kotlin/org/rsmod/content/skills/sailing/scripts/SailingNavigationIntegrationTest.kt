package org.rsmod.content.skills.sailing.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.types.aconverted.interf.IfButtonOp
import dev.openrune.util.Wearpos
import java.util.BitSet
import kotlinx.coroutines.runBlocking
import net.rsprot.protocol.game.incoming.misc.user.SetHeading
import net.rsprot.protocol.game.outgoing.camera.CamTargetV4
import net.rsprot.protocol.game.outgoing.misc.client.ResetInteractionMode
import net.rsprot.protocol.game.outgoing.misc.client.SetInteractionMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.net.rsprot.handlers.SetHeadingHandler
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.ui.IfOverlayButton
import org.rsmod.api.player.ui.ifOpenMain
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.api.route.BoundValidator
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.skills.sailing.ON_PLAYER_BOAT_ABOARD
import org.rsmod.content.skills.sailing.ON_PLAYER_BOAT_ASHORE
import org.rsmod.content.skills.sailing.SailingBoatVars
import org.rsmod.content.skills.sailing.SailingMoveMode
import org.rsmod.content.skills.sailing.SailingNavigationService
import org.rsmod.content.skills.sailing.SailingSeaMap
import org.rsmod.content.skills.sailing.SailingWind
import org.rsmod.content.skills.sailing.boardedBoat
import org.rsmod.content.skills.sailing.boardedBoatType
import org.rsmod.content.skills.sailing.boardedBoatWorld
import org.rsmod.content.skills.sailing.ownedBoatSlots
import org.rsmod.content.skills.sailing.readSailingVar
import org.rsmod.content.skills.sailing.sailingLastDock
import org.rsmod.content.skills.sailing.sailingOnPlayerBoat
import org.rsmod.content.skills.sailing.sailingSelectedBoatSlot
import org.rsmod.content.skills.sailing.writeSailingVar
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.client.NoopClient
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SailingNavigationIntegrationTest {
    private val collision = CollisionFlagMap()
    private val water = mutableMapOf<Int, BitSet>()
    private val sea = SailingSeaMap(collision, water)
    private lateinit var fixture: SailingBoardingLifecycleTest.Fixture
    private lateinit var client: RecordingClient

    @BeforeAll
    fun loadNativeBounds() {
        ServerCacheManager.init(241).close()
        sea.initialize()
    }

    @BeforeEach
    fun createVoyage() {
        water.clear()
        for (squareX in 45..54) {
            for (squareZ in 44..54) {
                water[(squareX shl 8) or squareZ] = BitSet(4096).apply { set(0, 4096) }
                for (zoneX in 0..7) {
                    for (zoneZ in 0..7) {
                        collision.allocateIfAbsent(squareX * 64 + zoneX * 8, squareZ * 64 + zoneZ * 8, 0).fill(0)
                    }
                }
            }
        }
        fixture = SailingBoardingLifecycleTest.Fixture(collision = collision, seaMap = sea)
        client = RecordingClient()
        fixture.player.client = client
        fixture.player.xpRate = 1.0
        fixture.boardDirectly().rootCoords = CoordGrid(3200, 3200)
    }

    @Test
    fun `borrowed tutorial skiff moves through native controls without consuming an owned slot`() = runBlocking {
        val owned = fixture.player.ownedBoatSlots().associateWith {
            fixture.player.readSailingVar(SailingBoatVars.field(it, "type")) to
                fixture.player.readSailingVar(SailingBoatVars.port(it))
        }
        val selected = fixture.player.sailingSelectedBoatSlot
        val entity = boardBorrowedSkiff()
        assertTrue(fixture.boats.isTutorial(fixture.player))
        assertEquals(0, fixture.boats.boardedSlot(fixture.player))
        val context = ScriptContext(fixture.eventBus, CheatCommandMap(), EngineQueueCache())
        with(SailingNavigationScript(fixture.navigation, fixture.boats)) { context.startup() }
        fixture.navigation.navigate(fixture.access())
        assertTrue(fixture.navigation.isNavigating(fixture.player))
        assertHeadingControls(entity.slotId)
        val controls = ServerCacheManager.fromComponent(SailingNavigationService.COMPONENT_CONTROLS)
        val start = entity.fineX to entity.fineZ
        SetHeadingHandler(fixture.eventBus).handle(fixture.player, SetHeading(4))
        assertTrue(fixture.eventBus.publish(fixture.access(), IfOverlayButton(controls, 0, null, IfButtonOp.Op1)))
        tick()
        assertNotEquals(start, entity.fineX to entity.fineZ)
        assertEquals(SailingMoveMode.Forward.value, mode())
        assertEquals(selected, fixture.player.sailingSelectedBoatSlot)
        assertEquals(owned.keys.toList(), fixture.player.ownedBoatSlots())
        for ((slot, saved) in owned) {
            assertEquals(saved.first, fixture.player.readSailingVar(SailingBoatVars.field(slot, "type")))
            assertEquals(saved.second, fixture.player.readSailingVar(SailingBoatVars.port(slot)))
        }
    }

    @Test
    fun `tutorial helm works without ownership and logout releases the borrowed deck`() {
        fixture.player.writeSailingVar(SailingBoatVars.owned(1), 0)
        val entity = boardBorrowedSkiff()
        fixture.navigation.navigate(fixture.access())
        assertTrue(fixture.navigation.isNavigating(fixture.player))
        fixture.navigation.control(fixture.player, 0)
        val start = entity.fineX to entity.fineZ
        tick()
        assertNotEquals(start, entity.fineX to entity.fineZ)
        fixture.logout()
        tick()
        assertEquals(fixture.shore, fixture.player.coords)
        assertNull(fixture.boats.boarded(fixture.player))
        assertFalse(fixture.navigation.isNavigating(fixture.player))
        assertTrue(fixture.player.ownedBoatSlots().isEmpty())
        assertEquals(ON_PLAYER_BOAT_ASHORE, fixture.player.sailingOnPlayerBoat)
        assertEquals(0, fixture.registry.count())
    }

    private fun boardBorrowedSkiff(): org.rsmod.game.entity.WorldEntity {
        fixture.logout()
        val source = ZoneKey(480, 800, 0)
        for (level in 0..3) {
            collision.allocateIfAbsent(source.x * 8, source.z * 8, level)
        }
        val template = RegionTemplate.create {
            for (level in 0..3) this[0, 0, level] = ZoneKey(source.x, source.z, level)
        }
        val entity = checkNotNull(fixture.boats.spawnTutorial(fixture.player, template, CoordGrid(3200, 3200)))
        fixture.player.coords = CoordGrid(entity.instanceSouthWest.x + 4, entity.instanceSouthWest.z + 4, 1)
        fixture.player.sailingOnPlayerBoat = ON_PLAYER_BOAT_ABOARD
        fixture.player.boardedBoat = 1
        fixture.player.boardedBoatType = 1
        fixture.player.boardedBoatWorld = entity.slotId
        fixture.player.sailingLastDock = fixture.dock.row.dockId
        fixture.navigation.registerTutorial(fixture.player, entity)
        return entity
    }

    @Test
    fun `spawned raft helm and native heading and sidepanel events start a real voyage`() = runBlocking {
        val context = ScriptContext(fixture.eventBus, CheatCommandMap(), EngineQueueCache())
        val interactions = LocInteractions(BoundValidator(fixture.collision), fixture.eventBus)
        with(GenericPassageScript(fixture.locs, fixture.collision, interactions)) { context.startup() }
        with(HelmScript(fixture.eventBus, fixture.boats, fixture.navigation)) { context.startup() }
        with(SailingNavigationScript(fixture.navigation, fixture.boats)) { context.startup() }
        val entity = checkNotNull(fixture.boats.boarded(fixture.player))
        val base = entity.instanceSouthWest
        val helm = checkNotNull(fixture.locRegistry.findType(CoordGrid(base.x + 3, base.z + 4, 1), 59554))
        val type = checkNotNull(ServerCacheManager.getObject(helm.id))
        val bound = BoundLocInfo(helm, type)
        fixture.player.coords = helm.coords.translate(0, -1)
        assertTrue(BoundValidator(fixture.collision).touches(fixture.player.avatar, bound))
        assertEquals(0, fixture.collision[fixture.player.x, fixture.player.z, fixture.player.level] and CollisionFlag.BLOCK_WALK)
        // Dispatch from the actual multiloc; publishing Op1 directly bypasses competing default handlers.
        val event = checkNotNull(interactions.opTrigger(fixture.player, bound, InteractionOp.Op1))
        assertTrue(event is LocEvents.Op1, "The visible helm fell through to a generic handler")
        assertTrue(fixture.eventBus.publish(fixture.access(), event))
        assertTrue(fixture.navigation.isNavigating(fixture.player))
        // Heading packets require the server to enable Heading tile mode first.
        assertHeadingControls(entity.slotId)
        SetHeadingHandler(fixture.eventBus).handle(fixture.player, SetHeading(4))
        val controls = ServerCacheManager.fromComponent((937 shl 16) or 25)
        assertTrue(fixture.eventBus.publish(fixture.access(), IfOverlayButton(controls, 0, null, IfButtonOp.Op1)))
        val start = entity.fineX to entity.fineZ
        tick()
        assertNotEquals(start, entity.fineX to entity.fineZ)
        assertEquals(SailingMoveMode.Forward.value, mode())
        assertEquals(128, entity.angle)
    }

    @Test
    fun `heading and speed buttons cannot control a boat before taking its helm`() {
        val entity = checkNotNull(fixture.boats.boarded(fixture.player))
        val start = entity.rootCoords
        fixture.navigation.heading(fixture.player, 4)
        fixture.navigation.control(fixture.player, 2)
        tick()
        assertEquals(start, entity.rootCoords)
        assertEquals(0, entity.angle)
        assertFalse(fixture.navigation.isNavigating(fixture.player))
        assertTrue(client.interactionModes().isEmpty())
    }

    @Test
    fun `taking helm targets actual hull and native controls move the registered boat`() {
        val entity = checkNotNull(fixture.boats.boarded(fixture.player))
        fixture.navigation.navigate(fixture.access())
        assertTrue(fixture.navigation.isNavigating(fixture.player))
        assertEquals(CamTargetV4.WorldEntityTarget(entity.slotId), client.camera().type)
        assertHeadingControls(entity.slotId)
        val startX = entity.fineX
        val startZ = entity.fineZ
        fixture.navigation.heading(fixture.player, 4)
        fixture.navigation.control(fixture.player, 0)
        tick()
        assertEquals(128, entity.angle)
        assertNotEquals(startX to startZ, entity.fineX to entity.fineZ)
        assertEquals(SailingMoveMode.Forward.value, mode())
        assertTrue(fixture.player.coords in entity)
    }

    @Test
    fun `new solid obstruction stops the complete hull without moving it into land`() {
        val entity = checkNotNull(fixture.boats.boarded(fixture.player))
        fixture.navigation.navigate(fixture.access())
        fixture.navigation.control(fixture.player, 0)
        val start = entity.fineX to entity.fineZ
        for (x in 3190..3210) {
            for (z in 3190..3210) collision[x, z, 0] = CollisionFlag.LOC
        }
        tick()
        assertEquals(start, entity.fineX to entity.fineZ)
        assertEquals(SailingMoveMode.Stopped.value, mode())
        assertEquals(0, fixture.player.readSailingVar("varbit.sailing_boat_time_trim_window"))
    }

    @Test
    fun `walking away from helm rejects new controls and returns camera to player`() {
        val entity = checkNotNull(fixture.boats.boarded(fixture.player))
        fixture.navigation.navigate(fixture.access())
        fixture.player.coords = fixture.player.coords.translate(1, 0)
        fixture.navigation.heading(fixture.player, 8)
        fixture.navigation.control(fixture.player, 0)
        tick()
        assertEquals(0, entity.angle)
        assertEquals(SailingMoveMode.Stopped.value, mode())
        assertFalse(fixture.navigation.isNavigating(fixture.player))
        assertEquals(0, fixture.player.readSailingVar(SailingNavigationService.VAR_AT_HELM))
        assertEquals(CamTargetV4.PlayerCamTarget(fixture.player.slotId), client.camera().type)
        assertWalkingControls(entity.slotId)
    }

    @Test
    fun `releasing and retaking the helm restores native click modes without stale overrides`() {
        val world = checkNotNull(fixture.boats.boarded(fixture.player)).slotId
        fixture.navigation.navigate(fixture.access())
        assertHeadingControls(world)
        fixture.navigation.navigate(fixture.access())
        assertFalse(fixture.navigation.isNavigating(fixture.player))
        assertWalkingControls(world)
        val releasedModes = client.interactionModes()
        tick()
        assertEquals(releasedModes, client.interactionModes())
        fixture.navigation.navigate(fixture.access())
        assertHeadingControls(world)
        assertTrue(fixture.navigation.isNavigating(fixture.player))
    }

    @Test
    fun `opening cargo releases native steering and rejects subsequent heading packets`() {
        val context = ScriptContext(fixture.eventBus, CheatCommandMap(), EngineQueueCache())
        with(SailingNavigationScript(fixture.navigation, fixture.boats)) { context.startup() }
        val entity = checkNotNull(fixture.boats.boarded(fixture.player))
        fixture.navigation.navigate(fixture.access())
        fixture.player.ifOpenMain("interface.sailing_boat_cargohold", fixture.eventBus)
        assertFalse(fixture.navigation.isNavigating(fixture.player))
        assertWalkingControls(entity.slotId)
        SetHeadingHandler(fixture.eventBus).handle(fixture.player, SetHeading(4))
        tick()
        assertEquals(0, entity.angle)
    }

    @Test
    fun `closing the sailing panel restores walking click modes on the next cycle`() {
        val entity = checkNotNull(fixture.boats.boarded(fixture.player))
        fixture.navigation.navigate(fixture.access())
        SailingSidepanel.close(fixture.player, fixture.eventBus)
        fixture.navigation.heading(fixture.player, 4)
        tick()
        assertFalse(fixture.navigation.isNavigating(fixture.player))
        assertWalkingControls(entity.slotId)
        assertEquals(0, entity.angle)
    }

    @Test
    fun `disembarking resets the original deck override after its avatar slot is released`() {
        val entity = checkNotNull(fixture.boats.boarded(fixture.player))
        val world = entity.slotId
        fixture.navigation.navigate(fixture.access())
        fixture.finishDisembark(fixture.disembarkTask())
        fixture.assertAshore()
        assertNotEquals(world, entity.slotId)
        assertWalkingControls(world)
        val departureModes = client.interactionModes()
        tick()
        assertEquals(departureModes, client.interactionModes())
    }

    @Test
    fun `logout cleanup resets heading mode before a new boat can reuse its deck slot`() {
        val world = checkNotNull(fixture.boats.boarded(fixture.player)).slotId
        fixture.navigation.navigate(fixture.access())
        fixture.logout()
        fixture.assertAshore()
        assertWalkingControls(world)
    }

    @Test
    fun `one gust awards trim xp once and invalid or repeated trimming awards none`() {
        fixture.navigation.navigate(fixture.access())
        val before = xp()
        fixture.navigation.trim(fixture.player)
        assertEquals(before, xp())
        fixture.navigation.setSails(fixture.player, true)
        repeat(SailingWind.OFFER_INTERVAL) { tick() }
        assertTrue(fixture.player.readSailingVar("varbit.sailing_boat_time_trim_window") > 0)
        fixture.navigation.trim(fixture.player)
        val earned = xp()
        assertEquals((SailingWind.TRIM_XP.first() * 10 * fixture.player.globalXpRate).toInt(), earned - before)
        fixture.navigation.trim(fixture.player)
        assertEquals(earned, xp())
        assertEquals(0, fixture.player.readSailingVar("varbit.sailing_boat_time_trim_window"))
        fixture.navigation.halt(fixture.player)
        repeat(SailingWind.OFFER_INTERVAL) { tick() }
        fixture.navigation.trim(fixture.player)
        assertEquals(earned, xp())
    }

    @Test
    fun `carrying courier cargo blocks a gust without consuming its reward`() {
        fixture.navigation.navigate(fixture.access())
        fixture.navigation.setSails(fixture.player, true)
        repeat(SailingWind.OFFER_INTERVAL) { tick() }
        val before = xp()
        fixture.player.worn[Wearpos.RightHand.slot] = InvObj(checkNotNull(ServerCacheManager.getItem(32435)))
        fixture.navigation.trim(fixture.player)
        assertEquals(before, xp())
        assertTrue(fixture.player.readSailingVar("varbit.sailing_boat_time_trim_window") > 0)
        fixture.player.worn[Wearpos.RightHand.slot] = null
        fixture.navigation.trim(fixture.player)
        assertTrue(xp() > before)
    }

    @Test
    fun `lost ownership cannot create helm authority`() {
        val entity = checkNotNull(fixture.boats.boarded(fixture.player))
        entity.ownerSlot = fixture.player.slotId + 1
        fixture.navigation.navigate(fixture.access())
        fixture.navigation.heading(fixture.player, 4)
        fixture.navigation.control(fixture.player, 0)
        tick()
        assertFalse(fixture.navigation.isNavigating(fixture.player))
        assertEquals(0, entity.angle)
        assertTrue(client.packets.none { it is CamTargetV4 })
        assertTrue(client.interactionModes().isEmpty())
    }

    @Test
    fun `teleporting off deck restores combat tab and clears sailing timers`() {
        val world = checkNotNull(fixture.boats.boarded(fixture.player)).slotId
        fixture.navigation.navigate(fixture.access())
        fixture.navigation.setSails(fixture.player, true)
        repeat(SailingWind.OFFER_INTERVAL) { tick() }
        assertTrue(SailingSidepanel.INTERFACE in fixture.player.ui)
        fixture.player.coords = fixture.shore
        fixture.boats.tick()
        tick()
        assertFalse(SailingSidepanel.INTERFACE in fixture.player.ui)
        assertTrue(fixture.player.ui.containsOverlay("interface.combat_interface"))
        assertEquals(0, fixture.player.readSailingVar("varbit.sailing_boat_time_trim_window"))
        assertEquals(0, fixture.player.readSailingVar("varbit.sailing_boat_speedboost_duration"))
        assertEquals(0, fixture.player.readSailingVar(SailingNavigationService.VAR_AT_HELM))
        assertEquals(SailingMoveMode.Stopped.value, mode())
        assertEquals(CamTargetV4.PlayerCamTarget(fixture.player.slotId), client.camera().type)
        assertWalkingControls(world)
    }

    @Test
    fun `leaving boat clears camera and controls and rejects pending reward`() {
        val world = checkNotNull(fixture.boats.boarded(fixture.player)).slotId
        fixture.navigation.navigate(fixture.access())
        fixture.navigation.setSails(fixture.player, true)
        repeat(SailingWind.OFFER_INTERVAL) { tick() }
        val before = xp()
        fixture.player.sailingOnPlayerBoat = ON_PLAYER_BOAT_ASHORE
        fixture.navigation.trim(fixture.player)
        tick()
        assertEquals(before, xp())
        assertFalse(fixture.navigation.isNavigating(fixture.player))
        assertEquals(0, fixture.player.readSailingVar(SailingNavigationService.VAR_AT_HELM))
        assertEquals(CamTargetV4.PlayerCamTarget(fixture.player.slotId), client.camera().type)
        assertWalkingControls(world)
    }

    @Test
    fun `changing selected slot cannot rebind an existing hull or borrow upgraded trim rewards`() {
        val entity = checkNotNull(fixture.boats.boarded(fixture.player))
        fixture.navigation.navigate(fixture.access())
        fixture.navigation.setSails(fixture.player, true)
        repeat(SailingWind.OFFER_INTERVAL) { tick() }
        val before = xp()
        val position = entity.fineX to entity.fineZ
        val angle = entity.angle
        fixture.player.writeSailingVar(SailingBoatVars.owned(2), 1)
        fixture.player.writeSailingVar(SailingBoatVars.field(2, "type"), fixture.hull.typeId)
        fixture.player.writeSailingVar(SailingBoatVars.sail(2), 6)
        fixture.player.sailingSelectedBoatSlot = 2

        fixture.navigation.navigate(fixture.access())
        fixture.navigation.heading(fixture.player, 4)
        fixture.navigation.control(fixture.player, 0)
        fixture.navigation.setSails(fixture.player, true)
        fixture.navigation.trim(fixture.player)
        repeat(SailingWind.OFFER_INTERVAL) { tick() }
        fixture.navigation.trim(fixture.player)

        assertEquals(1, fixture.boats.boardedSlot(fixture.player))
        assertEquals(position, entity.fineX to entity.fineZ)
        assertEquals(angle, entity.angle)
        assertEquals(before, xp())
        assertFalse(fixture.navigation.isNavigating(fixture.player))
        assertEquals(0, fixture.player.readSailingVar(SailingNavigationService.VAR_AT_HELM))
        assertEquals(CamTargetV4.PlayerCamTarget(fixture.player.slotId), client.camera().type)
    }

    private fun tick() {
        fixture.clock.tick()
        fixture.navigation.tick()
    }

    private fun mode() = fixture.player.readSailingVar(SailingNavigationService.VAR_MOVE_MODE)

    private fun xp() = fixture.player.statMap.getFineXP("stat.sailing")

    private fun assertHeadingControls(world: Int) {
        assertEquals(listOf(SetInteractionMode(-2, 2, 1), SetInteractionMode(world, 1, 1)), client.interactionModes().takeLast(2))
    }

    private fun assertWalkingControls(world: Int) {
        assertEquals(listOf(ResetInteractionMode(world), SetInteractionMode(-2, 1, 1)), client.interactionModes().takeLast(2))
    }

    private class RecordingClient : Client<Any, Any> by NoopClient {
        val packets = mutableListOf<Any>()
        override fun write(message: Any) { packets += message }
        fun camera(): CamTargetV4 = packets.filterIsInstance<CamTargetV4>().last()
        fun interactionModes(): List<Any> = packets.filter { it is SetInteractionMode || it is ResetInteractionMode }
    }
}
