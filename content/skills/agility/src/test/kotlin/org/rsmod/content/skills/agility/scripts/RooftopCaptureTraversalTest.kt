package org.rsmod.content.skills.agility.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.cache.filestore.definition.InterfaceType
import dev.openrune.definition.type.widget.ComponentType
import dev.openrune.rscm.RSCM
import dev.openrune.types.SequenceServerType
import net.rsprot.protocol.game.outgoing.sound.SynthSound
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.config.constants
import org.rsmod.api.game.process.player.PlayerFaceSquareProcessor
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.content.skills.agility.configs.ArdougneRooftopCourse
import org.rsmod.content.skills.agility.configs.GnomeStrongholdCourse
import org.rsmod.content.skills.agility.shortcuts.scripts.squeezeManorFence
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.client.Client
import org.rsmod.game.client.NoopClient
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.EntityExactMove
import org.rsmod.game.entity.util.EntityFaceAngle
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class RooftopCaptureTraversalTest {
    private val restore = mutableListOf<() -> Unit>()

    @BeforeAll
    fun installDefinitions() {
        val symbols = mapOf(
            "component.toplevel_osrs_stretch:mainmodal" to 65536,
            "seq.human_reachforladder" to 828,
            "seq.human_climbing" to 737,
            "seq.human_walk_logbalance" to 762,
            "seq.human_walk_logbalance_ready" to 763,
            "seq.human_spot_jump" to 741,
            "seq.human_walk_crumbledwall" to 839,
            "seq.human_jump_hurdle" to 1603,
            "seq.human_longjump" to 807,
            "seq.human_steppingstonejump" to 769,
            "seq.human_squeeze" to 14418,
            "seq.mdaughter_tree_climb_combi" to 1765,
            "seq.agility_shortcut_wall_jump" to 2583,
            "seq.agility_shortcut_wall_jump2" to 2585,
            "seq.agility_shortcut_wall_jumpdown" to 2586,
            "seq.agility_shortcut_wall_jumpdown2" to 2588,
            "seq.agilityarena_handholds_middle" to 1118,
            "seq.agilityarena_handholds_last" to 1120,
            "seq.agilityarena_handholds_middlereverse" to 1122,
            "seq.agilityarena_handholds_lastreverse" to 1124,
            "seq.human_into_sidestepl" to 753,
            "seq.human_walk_sidestepl" to 756,
            "seq.human_ready_sidestepl" to 757,
            "seq.human_outof_sidestepl" to 759,
            "seq.human_monkeybars_on" to 742,
            "seq.human_monkeybars_off" to 743,
            "seq.human_monkeybars_walk" to 744,
            "seq.human_monkeybars_ready" to 745,
            "synth.JUMP_UP" to 2468,
            "synth.JUMP" to 2461,
            "synth.CLIMB_WALL" to 2454,
            "synth.CLIMBING_LOOP" to 2453,
            "synth.TREE_CLIMBING" to 1705,
            "synth.JUMP2" to 2462,
            "synth.HANDHOLDS_LOOP" to 2459,
            "synth.FALL_LAND" to 2455,
            "synth.BALANCING_LEDGE" to 2451,
            "synth.TIGHTROPE" to 2495,
            "synth.MONKEYBARS_ON" to 2474,
            "synth.MONKEYBARS_LOOP" to 2466,
            "synth.MONKEYBARS_OFF" to 2473,
        )
        replaceMap(RSCM::class.java, "cache", symbols)
        // Sequence priority is resolved before the animation update is written.
        val sequences = symbols.filterKeys { it.startsWith("seq.") }.values.associateWith {
            SequenceServerType(id = it, priority = 5).apply {
                if (it == 769) { totalDelay = 68; tickDuration = 3 }
            }
        }
        replaceMap(ServerCacheManager::class.java, "sequences", sequences)
        val component = emptyComponent(65536)
        val interf = InterfaceType::class.java.declaredConstructors.single().newInstance(
            mapOf(0 to component), 1, null,
        ) as InterfaceType
        replaceMap(ServerCacheManager::class.java, "interfaces", mapOf(1 to interf))
    }

    @AfterAll
    fun restoreDefinitions() = restore.asReversed().forEach { it() }

    @Test
    fun `Gnome tree traversal reaches before relocating in either direction`() {
        val routes = listOf(
            Triple(CoordGrid(2473, 3423, 1), CoordGrid(2473, 3422, 1), GnomeStrongholdCourse.BRANCH_UP_DEST),
            Triple(CoordGrid(2485, 3419, 2), CoordGrid(2486, 3419, 2), GnomeStrongholdCourse.BRANCH_DOWN_DEST),
        )
        for ((start, face, end) in routes) {
            val fixture = Fixture(start) { gnomeBranchClimb(end, face) }
            assertEquals(828, fixture.player.pendingSequence.id)
            assertEquals(start, fixture.player.coords)
            fixture.tick(GnomeStrongholdCourse.BRANCH_TICKS)
            assertEquals(end, fixture.player.coords)
            assertEquals(EntitySeq.ZERO, fixture.player.pendingSequence)
            assertTrue(fixture.coroutine.isIdle)
        }
    }

    @Test
    fun `Wilderness stones use six smooth one tile jumps spaced two ticks apart`() {
        var finished = false
        val fixture = Fixture(CoordGrid(3002, 3960), clearTransientUpdates = true) {
            finished = wildernessStoneHops { true }
        }
        for (x in 3001 downTo 2996) {
            assertEquals(CoordGrid(x, 3960), fixture.player.coords)
            assertEquals(741, fixture.player.pendingSequence.id)
            assertEquals(EntityExactMove(deltaX1 = 1, deltaZ1 = 0, deltaX2 = 0, deltaZ2 = 0,
                clientDelay1 = 15, clientDelay2 = 30, direction = 512), fixture.player.pendingExactMove)
            assertFalse(finished)
            fixture.tick()
            if (x > 2996) {
                assertEquals(CoordGrid(x, 3960), fixture.player.coords)
                assertNull(fixture.player.pendingExactMove)
                fixture.tick()
            }
        }
        assertTrue(finished)
        assertEquals(11, fixture.player.currentMapClock)
        assertEquals(EntitySeq.ZERO, fixture.player.pendingSequence)
    }

    @Test
    fun `Wilderness failed final stone does not award completion or jump to the shore`() {
        var finished = true
        val fixture = Fixture(CoordGrid(3002, 3960), clearTransientUpdates = true) {
            finished = wildernessStoneHops { false }
        }
        fixture.tick(10)
        assertFalse(finished)
        assertEquals(CoordGrid(2997, 3960), fixture.player.coords)
        assertEquals(EntitySeq.ZERO, fixture.player.pendingSequence)
        assertTrue(fixture.coroutine.isIdle)
    }

    @Test
    fun `Wilderness rocks walk one tile per tick and stop at the top edge`() {
        val fixture = Fixture(CoordGrid(2994, 3937), clearTransientUpdates = true) {
            wildernessRockClimb(2994)
        }
        assertEquals(839, checkNotNull(fixture.player.bas).walkForward)
        for (z in 3936 downTo 3933) {
            fixture.tick()
            assertEquals(CoordGrid(2994, z), fixture.player.coords)
            assertNull(fixture.player.pendingExactMove)
        }
        assertEquals(fixture.originalBas, fixture.player.bas)
        assertFalse(fixture.player.forcedRoute)
        assertTrue(fixture.coroutine.isIdle)
    }

    @Test
    fun `stepping stone jumps complete their animations and pause one tick before jumping again`() {
        val west = CoordGrid(3149, 3363)
        val east = CoordGrid(3154, 3363)
        val stone = CoordGrid(3151, 3363)
        for ((start, end) in listOf(west to east, east to west)) {
            val f = Fixture(start, clearTransientUpdates = true) {
                player.faceAngle = if (end.x > start.x) 1536 else 512
                hopAcross(listOf(stone, end))
            }
            val first = checkNotNull(f.player.pendingExactMove)
            assertEquals(47, first.clientDelay1)
            assertEquals(59, first.clientDelay2)
            assertEquals(start.x - stone.x, first.deltaX1)
            assertEquals(0, first.deltaX2)
            assertEquals(if (end.x > start.x) 1536 else 512, first.direction)
            assertEquals(769, f.player.pendingSequence.id)
            assertEquals(19, f.player.pendingSequence.delay)
            assertEquals(listOf(SynthSound(2461, 1, 59)), f.client.sounds)
            f.tick(2)
            assertNull(f.player.pendingExactMove)
            assertFalse(f.coroutine.isIdle)
            f.tick()
            assertEquals(EntitySeq.ZERO, f.player.pendingSequence)
            assertEquals(stone, f.player.coords)
            assertNull(f.player.pendingExactMove)
            f.tick()
            assertEquals(769, f.player.pendingSequence.id)
            assertEquals(end, f.player.coords)
            val second = checkNotNull(f.player.pendingExactMove)
            assertEquals(47, second.clientDelay1)
            assertEquals(59, second.clientDelay2)
            assertEquals(stone.x - end.x, second.deltaX1)
            assertEquals(19, f.player.pendingSequence.delay)
            assertEquals(listOf(SynthSound(2461, 1, 59), SynthSound(2461, 1, 59)), f.client.sounds)
            f.tick(3)
            assertTrue(f.coroutine.isIdle)
            assertEquals(EntitySeq.ZERO, f.player.pendingSequence)
        }
    }

    @Test
    fun `stepping stone allows the ordinary turn before forcing a jump from any initial bearing`() {
        val stone = CoordGrid(3151, 3363)
        for (start in listOf(CoordGrid(3149, 3363), CoordGrid(3154, 3363))) {
            val bearing = if (start.x < stone.x) 1536 else 512
            for (initial in 0..1792 step 256) {
                if (initial == bearing) continue
                val f = Fixture(start, clearTransientUpdates = true) {
                    player.faceAngle = initial
                    hopAcross(listOf(stone))
                }
                assertEquals(start, f.player.coords)
                assertEquals(bearing, f.player.pendingFaceAngle.intValue)
                assertNull(f.player.pendingExactMove)
                assertEquals(EntitySeq.ZERO, f.player.pendingSequence)
                assertTrue(f.client.sounds.isEmpty())
                f.tick()
                assertEquals(stone, f.player.coords)
                assertEquals(bearing, checkNotNull(f.player.pendingExactMove).direction)
                assertEquals(19, f.player.pendingSequence.delay)
                f.tick(2)
                assertFalse(f.coroutine.isIdle)
                f.tick()
                assertTrue(f.coroutine.isIdle)
                assertEquals(EntitySeq.ZERO, f.player.pendingSequence)
            }
        }
    }

    @Test
    fun `manor fence crossing uses a facing squeeze and smooth movement in both directions`() {
        val west = CoordGrid(3085, 3352)
        val east = CoordGrid(3086, 3352)
        for ((start, end) in listOf(west to east, east to west)) {
            val f = Fixture(start) { squeezeManorFence(end) }
            assertEquals(14418, f.player.pendingSequence.id)
            assertEquals(end, f.player.coords)
            val move = checkNotNull(f.player.pendingExactMove)
            assertEquals(10, move.clientDelay1)
            assertEquals(40, move.clientDelay2)
            assertEquals(if (end.x > start.x) 1536 else 512, move.direction)
            f.tick(2)
            assertTrue(f.coroutine.isIdle)
            assertEquals(EntitySeq.ZERO, f.player.pendingSequence)
        }
    }

    @Test
    fun `Draynor climb lands west on the north roof edge after two ticks`() {
        val fixture = Fixture(CoordGrid(3103, 3279, 0)) { draynorRoughWall() }
        assertEquals(828, fixture.player.pendingSequence.id)
        assertEquals(15, fixture.player.pendingSequence.delay)
        fixture.tick()
        assertEquals(CoordGrid(3103, 3279, 0), fixture.player.coords)
        fixture.tick()
        assertEquals(CoordGrid(3102, 3279, 3), fixture.player.coords)
        assertTrue(fixture.coroutine.isIdle)
    }

    @Test
    fun `Draynor scramble lands and settles on x3088`() {
        val fixture = Fixture(CoordGrid(3088, 3257, 3)) { draynorWallScramble() }
        assertEquals(CoordGrid(3088, 3256, 3), fixture.player.coords)
        assertEquals(2583, fixture.player.pendingSequence.id)
        fixture.tick()
        assertEquals(2585, fixture.player.pendingSequence.id)
        fixture.tick()
        assertEquals(CoordGrid(3088, 3255, 3), fixture.player.coords)
        fixture.tick()
        assertEquals(EntitySeq.ZERO, fixture.player.pendingSequence)
    }

    @Test
    fun `Draynor crate rewards only on the second ground landing`() {
        var rewards = 0
        val fixture = Fixture(CoordGrid(3101, 3261, 3)) { draynorCrateDown { rewards++ } }
        fixture.tick()
        assertEquals(CoordGrid(3102, 3261, 1), fixture.player.coords)
        assertEquals(2588, fixture.player.pendingSequence.id)
        assertEquals(0, rewards)
        fixture.tick(2)
        assertEquals(2586, fixture.player.pendingSequence.id)
        assertEquals(0, rewards)
        fixture.tick()
        assertEquals(CoordGrid(3103, 3261, 0), fixture.player.coords)
        assertEquals(1, rewards)
        fixture.tick()
        assertTrue(fixture.coroutine.isIdle)
        assertEquals(1, rewards)
    }

    @Test
    fun `cancelling Draynor descent on the crate does not grant a lap or jump again`() {
        var rewards = 0
        val fixture = Fixture(CoordGrid(3101, 3261, 3)) { draynorCrateDown { rewards++ } }
        fixture.tick()
        fixture.player.cancelActiveCoroutine()
        fixture.tick(5)
        assertEquals(CoordGrid(3102, 3261, 1), fixture.player.coords)
        assertEquals(0, rewards)
        assertEquals(EntitySeq.ZERO, fixture.player.pendingSequence)
    }

    @Test
    fun `Al Kharid palm uses both handholds before changing to the roof plane`() {
        val fixture = Fixture(CoordGrid(3318, 3165, 1)) { alKharidTreeSwing() }
        assertEquals(CoordGrid(3318, 3169, 1), fixture.player.coords)
        assertEquals(2583, fixture.player.pendingSequence.id)
        fixture.tick(2)
        assertEquals(CoordGrid(3318, 3170, 1), fixture.player.coords)
        assertEquals(1122, fixture.player.pendingSequence.id)
        fixture.tick(2)
        assertEquals(CoordGrid(3317, 3174, 1), fixture.player.coords)
        assertEquals(1124, fixture.player.pendingSequence.id)
        fixture.tick(2)
        assertEquals(CoordGrid(3317, 3174, 2), fixture.player.coords)
        assertEquals(2588, fixture.player.pendingSequence.id)
        fixture.tick()
        assertTrue(fixture.coroutine.isIdle)
    }

    @Test
    fun `both Al Kharid wall climbs use the two tick reach sequence`() {
        val routes = listOf(
            CoordGrid(3273, 3195, 0) to CoordGrid(3273, 3192, 3),
            CoordGrid(3316, 3179, 2) to CoordGrid(3316, 3180, 3),
        )
        for ((start, end) in routes) {
            val fixture = Fixture(start) { alKharidRoofClimb(end, if (end.z > start.z) constants.em_face_north else constants.em_face_south) }
            assertEquals(828, fixture.player.pendingSequence.id)
            fixture.tick()
            assertEquals(start, fixture.player.coords)
            fixture.tick()
            assertEquals(end, fixture.player.coords)
            assertTrue(fixture.coroutine.isIdle)
        }
    }

    @Test
    fun `Draynor narrow wall walks south before its final diagonal and restores the pose`() {
        var rewards = 0
        val fixture = Fixture(CoordGrid(3089, 3265, 3)) { draynorNarrowWall { rewards++ } }
        assertEquals(753, fixture.player.pendingSequence.id)
        assertEquals(757, checkNotNull(fixture.player.bas).readyAnim)
        assertEquals(756, checkNotNull(fixture.player.bas).walkForward)
        val bas = checkNotNull(fixture.player.bas)
        assertEquals(listOf(757, 757, 756, 756, 756, 756, -1), listOf(
            bas.readyAnim, bas.turnOnSpot, bas.walkForward, bas.walkBack, bas.walkLeft, bas.walkRight, bas.running,
        ))
        val expected = listOf(
            CoordGrid(3089, 3264, 3), CoordGrid(3089, 3263, 3),
            CoordGrid(3089, 3262, 3), CoordGrid(3088, 3261, 3),
        )
        expected.forEach { tile -> fixture.tick(); assertEquals(tile, fixture.player.coords) }
        assertEquals(759, fixture.player.pendingSequence.id)
        assertEquals(1, rewards)
        assertEquals(fixture.originalBas, fixture.player.bas)
        fixture.tick()
        assertFalse(fixture.player.forcedRoute)
        assertTrue(fixture.coroutine.isIdle)
    }

    @Test
    fun `Pollnivneach entry animation survives mounting and reward follows dismount`() {
        var completed = false
        val fixture = Fixture(CoordGrid(3358, 2984, 2)) { completed = pollnivneachMonkeyBars() }
        assertEquals(742, fixture.player.pendingSequence.id)
        assertEquals(745, checkNotNull(fixture.player.bas).readyAnim)
        val bas = checkNotNull(fixture.player.bas)
        assertEquals(listOf(745, 745, 744, 745, 745, 745, -1), listOf(
            bas.readyAnim, bas.turnOnSpot, bas.walkForward, bas.walkBack, bas.walkLeft, bas.walkRight, bas.running,
        ))
        fixture.tick()
        assertEquals(CoordGrid(3358, 2985, 2), fixture.player.coords)
        assertEquals(EntitySeq.ZERO, fixture.player.pendingSequence)
        fixture.tick(6)
        assertEquals(CoordGrid(3358, 2991, 2), fixture.player.coords)
        assertFalse(completed)
        fixture.tick()
        assertEquals(743, fixture.player.pendingSequence.id)
        assertEquals(fixture.originalBas, fixture.player.bas)
        assertFalse(completed)
        fixture.tick()
        assertTrue(completed)
    }

    @Test
    fun `cancelling monkeybars clears forced routing animation and pose without completing`() {
        var completed = false
        val fixture = Fixture(CoordGrid(3358, 2984, 2)) { completed = pollnivneachMonkeyBars() }
        fixture.player.cancelActiveCoroutine()
        assertFalse(completed)
        assertFalse(fixture.player.forcedRoute)
        assertTrue(fixture.player.routeDestination.isEmpty())
        assertEquals(fixture.originalBas, fixture.player.bas)
        assertEquals(EntitySeq.ZERO, fixture.player.pendingSequence)
    }

    @Test
    fun `Varrock clothesline sends three smooth westward hops at the captured delays`() {
        var rewards = 0
        var squawks = 0
        val fixture = Fixture(CoordGrid(3214, 3414, 3), clearTransientUpdates = true) {
            varrockClothesLineCross { squawks++ }
            rewards++
        }
        assertEquals(512, fixture.player.pendingFaceAngle.intValue)
        assertNull(fixture.player.pendingExactMove)
        fixture.tick()
        assertEquals(CoordGrid(3212, 3414, 3), fixture.player.coords)
        assertEquals(EntityExactMove(deltaX1 = 2, deltaZ1 = 0, deltaX2 = 0, deltaZ2 = 0, clientDelay1 = 15, clientDelay2 = 30, direction = 512), fixture.player.pendingExactMove)
        assertEquals(741, fixture.player.pendingSequence.id)
        assertEquals(0, fixture.player.pendingSequence.delay)
        fixture.tick()
        assertEquals(CoordGrid(3210, 3414, 3), fixture.player.coords)
        assertEquals(0, squawks)
        assertEquals(EntityExactMove(deltaX1 = 2, deltaZ1 = 0, deltaX2 = 0, deltaZ2 = 0, clientDelay1 = 30, clientDelay2 = 45, direction = 512), fixture.player.pendingExactMove)
        assertEquals(15, fixture.player.pendingSequence.delay)
        fixture.tick()
        assertNull(fixture.player.pendingExactMove, "The middle hop must finish before another move begins")
        assertEquals(1, squawks)
        assertEquals(0, rewards)
        fixture.tick()
        assertEquals(CoordGrid(3208, 3414, 3), fixture.player.coords)
        assertEquals(EntityExactMove(deltaX1 = 2, deltaZ1 = 0, deltaX2 = 0, deltaZ2 = 0, clientDelay1 = 15, clientDelay2 = 30, direction = 512), fixture.player.pendingExactMove)
        assertEquals(512, fixture.player.pendingFaceAngle.intValue)
        fixture.tick()
        assertEquals(0, rewards)
        fixture.tick()
        assertEquals(1, rewards)
        assertEquals(1, squawks)
        assertEquals(EntitySeq.ZERO, fixture.player.pendingSequence)
        assertTrue(fixture.coroutine.isIdle)
    }

    @Test
    fun `cancelling a clothesline hop cannot start another hop or pay completion`() {
        var rewards = 0
        var squawks = 0
        val fixture = Fixture(CoordGrid(3214, 3414, 3), clearTransientUpdates = true) {
            varrockClothesLineCross { squawks++ }
            rewards++
        }
        fixture.tick()
        fixture.player.cancelActiveCoroutine()
        assertEquals(EntitySeq.ZERO, fixture.player.pendingSequence)
        fixture.tick(6)
        assertEquals(CoordGrid(3212, 3414, 3), fixture.player.coords)
        assertNull(fixture.player.pendingExactMove)
        assertEquals(CoordGrid.NULL, fixture.player.pendingFaceSquare)
        assertEquals(0, rewards)
        assertEquals(0, squawks)
    }

    @Test
    fun `Falador handholds go east around the outer corner before heading north`() {
        val fixture = Fixture(CoordGrid(3050, 3349, 3), clearTransientUpdates = true) {
            faladorHandholdsCross()
        }
        assertEquals(CoordGrid(3050, 3351, 3), fixture.player.coords)
        assertEquals(2583, fixture.player.pendingSequence.id)
        fixture.tick()
        assertEquals(CoordGrid(3050, 3351, 2), fixture.player.coords)
        assertEquals(1118, fixture.player.pendingSequence.id)

        fixture.tick()
        assertEquals(CoordGrid(3051, 3351, 2), fixture.player.coords)
        assertEquals(EntityExactMove(deltaX1 = -1, deltaZ1 = 0, deltaX2 = 0, deltaZ2 = 0,
            clientDelay1 = 13, clientDelay2 = 22, direction = 1024), fixture.player.pendingExactMove)
        assertEquals(EntitySeq.NULL, fixture.player.pendingSequence)

        fixture.tick()
        assertEquals(CoordGrid(3051, 3352, 2), fixture.player.coords)
        assertEquals(EntityExactMove(deltaX1 = 0, deltaZ1 = -1, deltaX2 = 0, deltaZ2 = 0,
            clientDelay1 = 0, clientDelay2 = 9, direction = 512), fixture.player.pendingExactMove)
        assertEquals(EntitySeq.NULL, fixture.player.pendingSequence)

        fixture.tick()
        for (z in 3353..3355) {
            assertEquals(CoordGrid(3051, z, 2), fixture.player.coords)
            assertEquals(EntityExactMove(deltaX1 = 0, deltaZ1 = -1, deltaX2 = 0, deltaZ2 = 0,
                clientDelay1 = 34, clientDelay2 = 52, direction = 512), fixture.player.pendingExactMove)
            assertEquals(1118, fixture.player.pendingSequence.id)
            fixture.tick(2)
        }
        assertEquals(CoordGrid(3050, 3357, 2), fixture.player.coords)
        assertEquals(1120, fixture.player.pendingSequence.id)
        fixture.tick()
        assertFalse(fixture.coroutine.isIdle)
        fixture.tick()
        assertEquals(12, fixture.player.currentMapClock)
        assertEquals(CoordGrid(3050, 3357, 3), fixture.player.coords)
        assertEquals(EntitySeq.ZERO, fixture.player.pendingSequence)
        assertTrue(fixture.coroutine.isIdle)
    }

    @Test
    fun `Falador ledge approach aligns before crossing a building corner`() {
        var finished = false
        val mount = CoordGrid(3013, 3335, 3)
        val dest = CoordGrid(3013, 3333, 3)
        val fixture = Fixture(CoordGrid(3012, 3335, 3), clearTransientUpdates = true) {
            finished = faladorLedgeCross(mount, dest)
        }
        assertNull(fixture.player.pendingExactMove)
        assertEquals(mount, fixture.player.routeDestination.peekFirst())
        fixture.tick()
        assertEquals(dest, fixture.player.coords)
        assertEquals(EntityExactMove(deltaX1 = 0, deltaZ1 = 2, deltaX2 = 0, deltaZ2 = 0, clientDelay1 = 15, clientDelay2 = 30, direction = 0), fixture.player.pendingExactMove)
        assertEquals(1603, fixture.player.pendingSequence.id)
        assertFalse(finished)
        fixture.tick()
        assertTrue(finished)
    }

    @Test
    fun `Falador ledge cannot jump or finish from another plane`() {
        var finished = true
        val start = CoordGrid(3013, 3335, 0)
        val fixture = Fixture(start) {
            finished = faladorLedgeCross(CoordGrid(3013, 3335, 3), CoordGrid(3013, 3333, 3))
        }
        assertFalse(finished)
        assertEquals(start, fixture.player.coords)
        assertNull(fixture.player.pendingExactMove)
    }

    @Test
    fun `Varrock fence crosses both ways only during the airborne animation frames`() {
        val north = CoordGrid(3240, 3335, 0)
        val south = CoordGrid(3240, 3334, 0)
        for ((start, dest) in listOf(north to south, south to north)) {
            var finished = false
            val fixture = Fixture(start) { finished = varrockFenceCross(start, dest) }
            assertEquals(807, fixture.player.pendingSequence.id)
            assertEquals(EntityExactMove(deltaX1 = 0, deltaZ1 = start.z - dest.z,
                deltaX2 = 0, deltaZ2 = 0, clientDelay1 = 18, clientDelay2 = 54,
                direction = if (dest.z > start.z) 1024 else 0), fixture.player.pendingExactMove)
            fixture.tick(2)
            assertFalse(finished)
            fixture.tick()
            assertFalse(finished)
            assertEquals(dest, fixture.player.coords)
            assertEquals(EntitySeq.ZERO, fixture.player.pendingSequence)
            val exit = dest.translate(0, (dest.z - start.z).compareTo(0))
            assertEquals(listOf(exit), fixture.player.routeDestination.toList())
            fixture.player.coords = exit
            fixture.tick()
            assertTrue(finished)
            assertEquals(exit, fixture.player.coords)
            assertFalse(fixture.player.forcedRoute)
        }
    }

    @Test
    fun `relocation resolves facing before arrival in all eight directions and across planes`() {
        val start = CoordGrid(3200, 3400, 3)
        val facings = listOf(
            Triple(0, -1, 0), Triple(-1, -1, 256), Triple(-1, 0, 512), Triple(-1, 1, 768),
            Triple(0, 1, 1024), Triple(1, 1, 1280), Triple(1, 0, 1536), Triple(1, -1, 1792),
        )
        for ((dx, dz, angle) in facings) {
            val target = CoordGrid(start.x + dx * 2, start.z + dz * 2, 1)
            val fixture = Fixture(start) {
                // Simulate the engine's still-pending loc-facing request.
                player.faceSquare(start.translate(-dx, -dz))
                moveBeat(target, "seq.human_spot_jump")
            }
            PlayerFaceSquareProcessor().process(fixture.player)
            assertEquals(angle, fixture.player.pendingFaceAngle.intValue, "Bearing ($dx, $dz)")
            assertEquals(angle, fixture.player.faceAngle)
            assertEquals(CoordGrid.NULL, fixture.player.pendingFaceSquare)
        }
    }

    @Test
    fun `Varrock wall plays the reach before the landing and preserves it through the settle glide`() {
        val fixture = Fixture(CoordGrid(3221, 3414, 0)) { varrockRoughWall() }
        assertEquals(828, fixture.player.pendingSequence.id)
        assertEquals(15, fixture.player.pendingSequence.delay)
        assertEquals(512, fixture.player.pendingFaceAngle.intValue)
        fixture.tick()
        assertEquals(CoordGrid(3221, 3414, 0), fixture.player.coords)
        fixture.tick()
        assertEquals(CoordGrid(3220, 3414, 3), fixture.player.coords)
        assertEquals(2585, fixture.player.pendingSequence.id)
        fixture.tick()
        assertEquals(EntityExactMove(deltaX1 = 1, deltaZ1 = 0, deltaX2 = 0, deltaZ2 = 0, clientDelay1 = 17, clientDelay2 = 26, direction = 512), fixture.player.pendingExactMove)
        assertEquals(2585, fixture.player.pendingSequence.id)
        fixture.tick()
        assertEquals(CoordGrid(3219, 3414, 3), fixture.player.coords)
        assertTrue(fixture.coroutine.isIdle)
    }

    @Test
    fun `handholds retain sideways body directions instead of turning along the path`() {
        val fixture = Fixture(CoordGrid(3318, 3165, 1), clearTransientUpdates = true) { alKharidTreeSwing() }
        assertEquals(EntityExactMove(deltaX1 = 0, deltaZ1 = -4, deltaX2 = 0, deltaZ2 = 0, clientDelay1 = 35, clientDelay2 = 49, direction = 1024), fixture.player.pendingExactMove)
        fixture.tick(2)
        assertEquals(EntityExactMove(deltaX1 = 0, deltaZ1 = -1, deltaX2 = 0, deltaZ2 = 0, clientDelay1 = 34, clientDelay2 = 42, direction = 1536), fixture.player.pendingExactMove)
        assertEquals(1536, fixture.player.pendingFaceAngle.intValue)
        fixture.tick(2)
        assertEquals(EntityExactMove(deltaX1 = 1, deltaZ1 = -4, deltaX2 = 0, deltaZ2 = 0, clientDelay1 = 34, clientDelay2 = 52, direction = 1024), fixture.player.pendingExactMove)
        assertEquals(1024, fixture.player.pendingFaceAngle.intValue)
    }

    @Test
    fun `Pollnivneach tree waits for each two tick glide before its next handhold`() {
        var rewards = 0
        val fixture = Fixture(CoordGrid(3368, 2976, 1), clearTransientUpdates = true) {
            pollnivneachTreeCross()
            rewards++
        }
        assertEquals(EntityExactMove(deltaX1 = 0, deltaZ1 = -2, deltaX2 = 0, deltaZ2 = 0, clientDelay1 = 35, clientDelay2 = 49, direction = 1024), fixture.player.pendingExactMove)
        fixture.tick()
        assertNull(fixture.player.pendingExactMove)
        fixture.tick()
        assertEquals(EntityExactMove(deltaX1 = 0, deltaZ1 = -1, deltaX2 = 0, deltaZ2 = 0, clientDelay1 = 34, clientDelay2 = 52, direction = 1536), fixture.player.pendingExactMove)
        assertEquals(1122, fixture.player.pendingSequence.id)
        fixture.tick(2)
        assertEquals(EntityExactMove(deltaX1 = 0, deltaZ1 = -3, deltaX2 = 0, deltaZ2 = 0, clientDelay1 = 34, clientDelay2 = 52, direction = 1536), fixture.player.pendingExactMove)
        assertEquals(1124, fixture.player.pendingSequence.id)
        fixture.tick()
        assertEquals(0, rewards)
        fixture.tick()
        assertEquals(CoordGrid(3368, 2982, 1), fixture.player.coords)
        assertEquals(2588, fixture.player.pendingSequence.id)
        assertEquals(0, rewards)
        fixture.tick()
        assertEquals(1, rewards)
    }

    @Test
    fun `Canifis tree preserves its visual endpoint and waits for the delayed climb`() {
        val fixture = Fixture(CoordGrid(3505, 3488, 0), clearTransientUpdates = true) { canifisTreeClimb() }
        assertEquals(CoordGrid(3507, 3489, 0), fixture.player.coords)
        assertEquals(EntityExactMove(deltaX1 = -2, deltaZ1 = -1, deltaX2 = -2, deltaZ2 = 0, clientDelay1 = 30, clientDelay2 = 60, direction = 1024), fixture.player.pendingExactMove)
        assertEquals(1765, fixture.player.pendingSequence.id)
        assertEquals(60, fixture.player.pendingSequence.delay)
        fixture.tick(2)
        assertEquals(EntityExactMove(deltaX1 = 2, deltaZ1 = 0, deltaX2 = 0, deltaZ2 = 0, clientDelay1 = 60, clientDelay2 = 115, direction = 1536), fixture.player.pendingExactMove)
        fixture.tick(3)
        assertEquals(CoordGrid(3505, 3489, 0), fixture.player.coords)
        assertFalse(fixture.coroutine.isIdle)
        fixture.tick()
        assertEquals(CoordGrid(3506, 3492, 2), fixture.player.coords)
        assertTrue(fixture.coroutine.isIdle)
    }

    @Test
    fun `a movement only handhold beat does not reset the active animation`() {
        val fixture = Fixture(CoordGrid(3050, 3351, 2)) {
            animBeat("seq.agilityarena_handholds_middlereverse")
            moveBeat(CoordGrid(3051, 3351, 2), movement = AgilityMovement(13, 22, 1024))
        }
        assertEquals(1122, fixture.player.pendingSequence.id)
        assertEquals(EntityExactMove(deltaX1 = -1, deltaZ1 = 0, deltaX2 = 0, deltaZ2 = 0, clientDelay1 = 13, clientDelay2 = 22, direction = 1024), fixture.player.pendingExactMove)
        assertEquals(1024, fixture.player.pendingFaceAngle.intValue)
    }

    @Test
    fun `Ardougne beams face north even when their landing is northwest`() {
        val fixture = Fixture(CoordGrid(2673, 3298)) {
            player.faceSquare(ArdougneRooftopCourse.WOODEN_BEAMS_DEST)
            ardougneWoodenBeams()
        }
        PlayerFaceSquareProcessor().process(fixture.player)
        assertEquals(constants.em_face_north, fixture.player.pendingFaceAngle.intValue)
        fixture.tick(ArdougneRooftopCourse.WOODEN_BEAMS_TICKS)
        assertEquals(ArdougneRooftopCourse.WOODEN_BEAMS_DEST, fixture.player.coords)
        assertEquals(constants.em_face_north, fixture.player.faceAngle)
    }

    @Test
    fun `wall climbs keep their wall bearing from a diagonally offset approach`() {
        val draynor = Fixture(CoordGrid(3103, 3278)) { draynorRoughWall() }
        val varrock = Fixture(CoordGrid(3221, 3413)) { varrockRoughWall() }
        val kharid = Fixture(CoordGrid(3274, 3195)) {
            alKharidRoofClimb(CoordGrid(3273, 3192, 3), constants.em_face_south)
        }
        assertEquals(constants.em_face_west, draynor.player.pendingFaceAngle.intValue)
        assertEquals(constants.em_face_west, varrock.player.pendingFaceAngle.intValue)
        assertEquals(constants.em_face_south, kharid.player.pendingFaceAngle.intValue)
    }

    @Test
    fun `Seers climb and sign hang last two ticks each without turning away from the wall`() {
        val fixture = Fixture(CoordGrid(2729, 3489), clearTransientUpdates = true) { seersWallClimb() }
        assertEquals(737, fixture.player.pendingSequence.id)
        assertEquals(15, fixture.player.pendingSequence.delay)
        assertEquals(constants.em_face_north, fixture.player.faceAngle)
        fixture.tick()
        assertEquals(CoordGrid(2729, 3489), fixture.player.coords)
        fixture.tick()
        assertEquals(CoordGrid(2729, 3488, 1), fixture.player.coords)
        assertEquals(1118, fixture.player.pendingSequence.id)
        assertEquals(constants.em_face_north, fixture.player.faceAngle)
        fixture.tick()
        assertFalse(fixture.coroutine.isIdle)
        fixture.tick()
        assertEquals(CoordGrid(2729, 3491, 3), fixture.player.coords)
        assertTrue(fixture.coroutine.isIdle)
        assertEquals(EntitySeq.ZERO, fixture.player.pendingSequence)
    }

    @Test
    fun `recorded drop starts its sequence and sound halfway through the launch tick`() {
        val fixture = Fixture(CoordGrid(3201, 3416, 3)) {
            climbTo(CoordGrid(3197, 3416, 1), 2, "seq.agility_shortcut_wall_jumpdown",
                "synth.JUMP2", landSeq = "seq.agility_shortcut_wall_jumpdown2",
                animDelayClientCycles = 15)
        }
        assertEquals(2586, fixture.player.pendingSequence.id)
        assertEquals(15, fixture.player.pendingSequence.delay)
        assertEquals(listOf(SynthSound(2462, 1, 15)), fixture.client.sounds)
        fixture.tick()
        assertEquals(2588, fixture.player.pendingSequence.id)
        assertEquals(0, fixture.player.pendingSequence.delay)
        fixture.tick()
        assertTrue(fixture.coroutine.isIdle)
    }

    @Test
    fun `Falador handhold sounds follow the grip without delaying its animation`() {
        val fixture = Fixture(CoordGrid(3050, 3349, 3)) { faladorHandholdsCross() }
        assertEquals(listOf(SynthSound(2468, 1, 20)), fixture.client.sounds)
        fixture.tick()
        assertEquals(1118, fixture.player.pendingSequence.id)
        assertEquals(0, fixture.player.pendingSequence.delay)
        assertEquals(SynthSound(2459, 1, 35), fixture.client.sounds.last())
        fixture.tick(11)
        assertEquals(5, fixture.client.sounds.count { it == SynthSound(2459, 1, 35) })
        assertTrue(fixture.coroutine.isIdle)
    }

    @Test
    fun `Pollnivneach reverse handholds use delayed grip sounds`() {
        val fixture = Fixture(CoordGrid(3368, 2976, 1)) { pollnivneachTreeCross() }
        fixture.tick(4)
        assertEquals(listOf(SynthSound(2468, 1, 10), SynthSound(2459, 1, 35),
            SynthSound(2459, 1, 35)), fixture.client.sounds)
        assertEquals(1124, fixture.player.pendingSequence.id)
        assertEquals(0, fixture.player.pendingSequence.delay)
    }

    @Test
    fun `tightrope sound bursts wait for mounting and retain their recorded loop counts`() {
        val mount = CoordGrid(3098, 3277, 3)
        val fixture = Fixture(CoordGrid(3099, 3277, 3)) {
            balanceCross(mount, CoordGrid(3090, 3277, 3), "seq.human_walk_logbalance",
                "synth.TIGHTROPE", sounds = listOf(BalanceSound(3), BalanceSound(3, 5)))
        }
        assertTrue(fixture.client.sounds.isEmpty())
        fixture.tick()
        assertEquals(mount, fixture.player.coords)
        assertEquals(listOf(SynthSound(2495, 3, 30), SynthSound(2495, 3, 150)), fixture.client.sounds)
        fixture.tick(8)
        assertTrue(fixture.coroutine.isIdle)
        assertEquals(CoordGrid(3090, 3277, 3), fixture.player.coords)
    }

    @Test
    fun `Varrock sidestep walks on each tick after mounting and sounds both steps`() {
        val fixture = Fixture(CoordGrid(3190, 3409, 1)) { varrockWallSidestep() }
        assertEquals(753, fixture.player.pendingSequence.id)
        assertEquals(756, checkNotNull(fixture.player.bas).walkForward)
        assertEquals(listOf(SynthSound(2451, 2, 30), SynthSound(2451, 2, 60)), fixture.client.sounds)
        fixture.tick()
        assertEquals(CoordGrid(3190, 3408, 1), fixture.player.coords)
        fixture.tick()
        assertEquals(CoordGrid(3190, 3407, 1), fixture.player.coords)
        assertFalse(fixture.coroutine.isIdle)
        fixture.tick()
        assertTrue(fixture.coroutine.isIdle)
        assertEquals(fixture.originalBas, fixture.player.bas)
    }

    private class RecordingClient : Client<Any, Any> by NoopClient {
        val sounds = mutableListOf<SynthSound>()
        override fun write(message: Any) {
            if (message is SynthSound) sounds += message
        }
    }

    private class Fixture(start: CoordGrid, private val clearTransientUpdates: Boolean = false, action: suspend ProtectedAccess.() -> Unit) {
        val client = RecordingClient()
        val player = Player(client = client).apply { coords = start; currentMapClock = 0; processedMapClock = 0 }
        val originalBas = player.bas
        private val collision = CollisionFlagMap().apply {
            for (level in 0..3) for (x in start.x - 40..start.x + 40 step 8) for (z in start.z - 40..start.z + 40 step 8) {
                allocateIfAbsent(x, z, level)
            }
        }
        private val events = EventBus()
        val coroutine: GameCoroutine = player.launch {
            val access = ProtectedAccess(player, this, context())
            action(access)
        }

        fun tick(count: Int = 1) = repeat(count) {
            if (clearTransientUpdates) {
                player.pendingExactMove = null
                player.pendingFaceAngle = EntityFaceAngle.NULL
                player.pendingSequence = EntitySeq.NULL
            }
            if (player.forcedRoute) {
                while (player.routeDestination.peekFirst() == player.coords) player.routeDestination.pollFirst()
                player.routeDestination.peekFirst()?.let { destination ->
                    player.coords = CoordGrid(
                        player.coords.x + (destination.x - player.coords.x).coerceIn(-1, 1),
                        player.coords.z + (destination.z - player.coords.z).coerceIn(-1, 1),
                        player.coords.level,
                    )
                }
            }
            player.currentMapClock++
            player.processedMapClock = player.currentMapClock
            if (coroutine.isSuspended) coroutine.advance()
            PlayerFaceSquareProcessor().process(player)
        }

        private fun context() = ProtectedAccessContext(
            getRandom = { error("Unexpected random dependency") },
            getEventBus = { events },
            getNpcList = { error("Unexpected NPC dependency") },
            getPlayerList = { error("Unexpected player-list dependency") },
            getCollision = { collision },
            getAreaChecker = { error("Unexpected area dependency") },
            getAlignment = { error("Unexpected dialogue dependency") },
            getLocInteractions = { error("Unexpected loc dependency") },
            getNpcInteractions = { error("Unexpected NPC interaction") },
            getPlayerInteractions = { error("Unexpected player interaction") },
            getHeldInteractions = { error("Unexpected held interaction") },
            getWornInteractions = { error("Unexpected worn interaction") },
            getMusicPlayer = { error("Unexpected music dependency") },
            getMarketPrices = { error("Unexpected market dependency") },
            getInstantHitProcessor = { error("Unexpected damage dependency") },
            getTeleportValidator = { error("Unexpected standard teleport") },
            getWorldRepo = { error("Unexpected world dependency") },
            getHitModifier = { error("Unexpected hit modifier") },
        )
    }

    private fun emptyComponent(packed: Int): ComponentType {
        val constructor = ComponentType::class.java.declaredConstructors.single { !it.isSynthetic }
        val args = constructor.parameterTypes.map { type ->
            when {
                type == Boolean::class.javaPrimitiveType -> false
                type == Int::class.javaPrimitiveType -> 0
                type == String::class.java -> ""
                type.isArray -> java.lang.reflect.Array.newInstance(type.componentType, 0)
                else -> null
            }
        }.toTypedArray()
        return (constructor.newInstance(*args) as ComponentType).apply {
            id = packed and 0xFFFF
            // getPacked reads internalId independently of the child definition ID.
            internalId = packed
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <K, V> replaceMap(owner: Class<*>, fieldName: String, values: Map<K, V>) {
        val field = owner.getDeclaredField(fieldName).apply { isAccessible = true }
        val map = field.get(null) as MutableMap<K, V>
        val previous = map.toMap()
        restore += { map.clear(); map.putAll(previous) }
        map.clear()
        map.putAll(values)
    }
}
