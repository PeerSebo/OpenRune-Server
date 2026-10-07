package org.rsmod.content.skills.agility.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.cache.filestore.definition.InterfaceType
import dev.openrune.definition.type.widget.ComponentType
import dev.openrune.rscm.RSCM
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.game.process.player.PlayerMovementProcessor
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.route.RouteFactory
import org.rsmod.api.route.StepFactory
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class TightropeApproachTest {
    private val restore = mutableListOf<() -> Unit>()

    @BeforeAll
    fun installDefinitions() {
        replaceMap(
            RSCM::class.java,
            "cache",
            mapOf(
                "component.toplevel_osrs_stretch:mainmodal" to 65536,
                "seq.human_walk_logbalance" to 762,
                "seq.human_walk_logbalance_ready" to 763,
            ),
        )
        val component = emptyComponent(65536)
        val interf = InterfaceType::class.java.declaredConstructors.single().newInstance(
            mapOf(0 to component), 1, null,
        ) as InterfaceType
        replaceMap(ServerCacheManager::class.java, "interfaces", mapOf(1 to interf))
    }

    @AfterAll
    fun restoreDefinitions() = restore.asReversed().forEach { it() }

    @Test
    fun `adjacent blocked mounts take the next movement step in every direction`() {
        val start = CoordGrid(3100, 3270, 3)
        for (dx in -1..1) for (dz in -1..1) {
            if (dx == 0 && dz == 0) continue
            val mount = start.translate(dx, dz)
            var reached = false
            val fixture = Fixture(start, blocked = listOf(mount)) { reached = approachMount(mount) }

            assertEquals(start, fixture.player.coords, "The approach must walk, not relocate immediately")
            fixture.tick()
            assertEquals(mount, fixture.player.coords, "Blocked mount ($dx, $dz) must not add idle ticks")
            assertFalse(reached, "Arrival is observed when the protected coroutine next resumes")
            fixture.tick()
            assertTrue(reached)
            assertTrue(fixture.coroutine.isIdle)
            assertFalse(fixture.player.forcedRoute)
            assertTrue(fixture.player.routeDestination.isEmpty())
        }
    }

    @Test
    fun `tightrope starts moving immediately and keeps moving after mounting`() {
        val start = CoordGrid(3100, 3270, 3)
        val mount = start.translateX(1)
        val dest = mount.translateX(3)
        var completed = false
        val fixture = Fixture(start, blocked = (1..4).map { start.translateX(it) }) {
            completed = balanceCross(mount, dest, "seq.human_walk_logbalance", synth = null)
        }

        for (distance in 1..4) {
            fixture.tick()
            assertEquals(start.translateX(distance), fixture.player.coords, "Crossing tick $distance")
            assertFalse(completed)
            if (distance > 1) {
                assertEquals(762, checkNotNull(fixture.player.bas).walkForward)
                val bas = checkNotNull(fixture.player.bas)
                assertEquals(listOf(763, 762, 762, 762, 762, 762, -1), listOf(
                    bas.readyAnim, bas.turnOnSpot, bas.walkForward, bas.walkBack, bas.walkLeft, bas.walkRight, bas.running,
                ))
                assertNotEquals(fixture.originalBas, fixture.player.bas)
            }
        }
        fixture.player.appearance.clearRebuildFlag()
        fixture.tick()
        assertTrue(completed)
        assertTrue(fixture.coroutine.isIdle)
        assertEquals(fixture.originalBas, fixture.player.bas)
        assertTrue(fixture.player.appearance.rebuild, "Restore the normal appearance after leaving the rope")
        assertFalse(fixture.player.forcedRoute)
        assertTrue(fixture.player.routeDestination.isEmpty())
        assertNull(fixture.player.tempMoveSpeed)
    }

    @Test
    fun `mount on a different plane is rejected without movement`() {
        val start = CoordGrid(3100, 3270, 0)
        var reached: Boolean? = null
        val fixture = Fixture(start) { reached = approachMount(CoordGrid(3101, 3270, 3)) }

        assertEquals(false, reached)
        assertTrue(fixture.coroutine.isIdle)
        fixture.tick(5)
        assertEquals(start, fixture.player.coords)
        assertFalse(fixture.player.forcedRoute)
        assertTrue(fixture.player.routeDestination.isEmpty())
    }

    @Test
    fun `blocked approach outside bridge radius does not drag the player to the rope`() {
        val start = CoordGrid(3100, 3270, 3)
        val mount = start.translateX(5)
        var reached: Boolean? = null
        val fixture = Fixture(start, blocked = listOf(start.translateX(1))) {
            reached = approachMount(mount)
        }

        repeat(8) {
            fixture.tick()
            assertEquals(start, fixture.player.coords)
            assertFalse(fixture.player.forcedRoute)
        }
        assertEquals(false, reached)
        assertTrue(fixture.coroutine.isIdle)
    }

    @Test
    fun `standing on the mount succeeds without waiting for movement`() {
        val mount = CoordGrid(3100, 3270, 3)
        var reached: Boolean? = null
        val fixture = Fixture(mount, blocked = listOf(mount)) { reached = approachMount(mount) }

        assertEquals(true, reached)
        assertTrue(fixture.coroutine.isIdle)
        assertEquals(mount, fixture.player.coords)
        assertFalse(fixture.player.forcedRoute)
        assertTrue(fixture.player.routeDestination.isEmpty())
    }

    @Test
    fun `cancelling before the mounting step clears the route without beginning the crossing`() {
        val start = CoordGrid(3100, 3270, 3)
        val mount = start.translateX(1)
        var completed = false
        val fixture = Fixture(start, blocked = listOf(mount)) {
            completed = balanceCross(mount, mount.translateX(3), "seq.human_walk_logbalance", synth = null)
        }

        fixture.player.cancelActiveCoroutine()

        assertFalse(completed)
        assertTrue(fixture.coroutine.isIdle)
        assertFalse(fixture.player.forcedRoute)
        assertTrue(fixture.player.routeDestination.isEmpty())
        assertNull(fixture.player.tempMoveSpeed)
        assertEquals(fixture.originalBas, fixture.player.bas)
        fixture.tick(5)
        assertEquals(start, fixture.player.coords)
    }

    @Test
    fun `cancelling on the rope restores the pose and cannot finish the crossing`() {
        val start = CoordGrid(3100, 3270, 3)
        val mount = start.translateX(1)
        var completed = false
        val fixture = Fixture(start, blocked = (1..4).map { start.translateX(it) }) {
            completed = balanceCross(mount, mount.translateX(3), "seq.human_walk_logbalance", synth = null)
        }
        fixture.tick(2)
        assertEquals(start.translateX(2), fixture.player.coords)
        assertEquals(762, checkNotNull(fixture.player.bas).walkForward)

        fixture.player.cancelActiveCoroutine()

        assertFalse(completed)
        assertTrue(fixture.coroutine.isIdle)
        assertFalse(fixture.player.forcedRoute)
        assertTrue(fixture.player.routeDestination.isEmpty())
        assertNull(fixture.player.tempMoveSpeed)
        assertEquals(fixture.originalBas, fixture.player.bas)
        assertEquals(EntitySeq.ZERO, fixture.player.pendingSequence)
        fixture.tick(5)
        assertEquals(start.translateX(2), fixture.player.coords)
    }

    private class Fixture(
        start: CoordGrid,
        blocked: List<CoordGrid> = emptyList(),
        action: suspend ProtectedAccess.() -> Unit,
    ) {
        val player = Player().apply { coords = start; currentMapClock = 0; processedMapClock = 0 }
        val originalBas = player.bas
        private val collision = CollisionFlagMap().apply {
            for (x in 3088..3112 step 8) for (z in 3264..3280 step 8) {
                allocateIfAbsent(x, z, start.level)
            }
            blocked.forEach { add(it.x, it.z, it.level, CollisionFlag.LOC) }
        }
        private val events = EventBus()
        private val movement = PlayerMovementProcessor(collision, RouteFactory(collision), StepFactory(collision), events)
        val coroutine: GameCoroutine = player.launch { action(ProtectedAccess(player, this, context())) }

        fun tick(count: Int = 1) = repeat(count) {
            player.previousCoords = player.coords
            player.processedMapClock = player.currentMapClock
            // Resume coroutines before interaction/movement, matching PlayerMainProcess.
            if (coroutine.isSuspended) coroutine.advance()
            movement.process(player)
            player.currentMapClock++
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
