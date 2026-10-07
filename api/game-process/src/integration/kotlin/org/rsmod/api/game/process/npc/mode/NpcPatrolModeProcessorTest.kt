package org.rsmod.api.game.process.npc.mode

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.game.process.npc.NpcMovementProcessor
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.factory.npcFactory
import org.rsmod.api.testing.factory.npcTypeFactory
import org.rsmod.game.entity.npc.NpcPatrol
import org.rsmod.game.entity.npc.NpcPatrolWaypoint
import org.rsmod.game.map.collision.add
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.routefinder.flag.CollisionFlag

class NpcPatrolModeProcessorTest {
    /**
     * Blocked patrol waypoints teleport only after the idle delay; the arrival pause must start
     * at the teleport.
     */
    @Test
    fun GameTestState.`immediately queue movement after teleport`() = runBasicGameTest {
        withCollisionState {
            val patrol = NpcPatrol(LUMBRIDGE_CASTLE_PATROL)
            val start = patrol[0].destination.translateX(-2)
            val type = npcTypeFactory.create { this.patrol = patrol }
            val hans = npcFactory.create(type, start) { patrolWaypointIndex = 0 }
            it.allocateCollision(MapSquareKey(50, 50))
            it.collision.add(patrol[0].destination, CollisionFlag.LOC)
            withNpc(hans) {
                val movement = NpcMovementProcessor(it.collision, it.stepFactory, eventBus)
                val mode = NpcPatrolModeProcessor(it.collision)
                fun process() {
                    previousCoords = coords
                    currentMapClock++
                    processedMapClock++
                    mode.process(this)
                    movement.process(this)
                }
                process()

                val teleDelay = IDLE_TELE_DELAY + 1
                repeat(teleDelay) {
                    process()
                    assertEquals(start.translateX(1), coords)
                    assertEquals(patrol[0].destination, routeDestination.peekLast())
                }

                // This is the point where the npc will teleport to the waypoint destination, but
                // should also end up walking one tile towards the following waypoint.
                process()
                assertNotEquals(patrol[0].destination, coords)
                assertEquals(patrol[1].destination, routeDestination.peekLast())

                val overstep = patrol[0].destination.translate(1, -1)
                assertEquals(overstep, coords)
            }
        }
    }

    /**
     * A blocked waypoint teleport must start its pause on arrival rather than inheriting the
     * earlier idle clock.
     */
    @Test
    fun GameTestState.`emulate pause delay timing after teleport`() = runBasicGameTest {
        withCollisionState {
            val patrol = NpcPatrol(LUMBRIDGE_CASTLE_PATROL)
            val start = patrol[2].destination
            val blocked = patrol[3].destination.translateZ(4)
            val type = npcTypeFactory.create { this.patrol = patrol }
            val hans = npcFactory.create(type, start) { patrolWaypointIndex = 2 }
            it.allocateCollision(MapSquareKey(50, 50))
            it.collision.add(blocked, CollisionFlag.LOC)
            withNpc(hans) {
                val movement = NpcMovementProcessor(it.collision, it.stepFactory, eventBus)
                val mode = NpcPatrolModeProcessor(it.collision)
                fun process() {
                    previousCoords = coords
                    currentMapClock++
                    processedMapClock++
                    mode.process(this)
                    movement.process(this)
                }
                repeat(3) { process() }
                check(coords == blocked.translateZ(1))

                val startClock = currentMapClock

                val teleDelay = IDLE_TELE_DELAY + 1
                repeat(teleDelay) {
                    process()
                    assertEquals(blocked.translateZ(1), coords)
                    assertEquals(patrol[3].destination, routeDestination.peekLast())
                    assertEquals(0, patrolPauseCycles)
                }

                // When the teleport delay hits, there is still a one clock delay before the npc
                // is actually teleported.
                process()
                assertEquals(patrol[3].destination, coords)
                assertEquals(IDLE_TELE_DELAY + 2, currentMapClock - startClock)

                assertEquals(1, patrolPauseCycles)

                val pauseDelay = patrol[3].pauseDelay
                repeat(pauseDelay - patrolPauseCycles) {
                    process()
                    assertEquals(patrol[3].destination, coords)
                }

                process()
                assertEquals(patrol[3].destination.translateX(1), coords)

                val sequenceDuration = IDLE_TELE_DELAY + 2 + pauseDelay
                assertEquals(sequenceDuration, currentMapClock - startClock)
            }
        }
    }

    /**
     * NPCs should have to wait [NpcPatrolWaypoint.pauseDelay] + 1 cycles standing still after
     * reaching said waypoint before they can resume patrolling.
     */
    @Test
    fun GameTestState.`emulate pause delay timing`() = runBasicGameTest {
        withCollisionState {
            val patrol = NpcPatrol(LUMBRIDGE_CASTLE_PATROL)
            val start = patrol[2].destination
            val type = npcTypeFactory.create { this.patrol = patrol }
            val hans = npcFactory.create(type, start) { patrolWaypointIndex = 2 }
            it.allocateCollision(MapSquareKey(50, 50))
            withNpc(hans) {
                val movement = NpcMovementProcessor(it.collision, it.stepFactory, eventBus)
                val mode = NpcPatrolModeProcessor(it.collision)
                fun process() {
                    previousCoords = coords
                    currentMapClock++
                    processedMapClock++
                    mode.process(this)
                    movement.process(this)
                }

                repeat(7) { index ->
                    val steps = index + 1
                    process()
                    assertEquals(start.translateZ(-steps), coords)
                }

                assertNotEquals(patrol[3].destination, coords)

                process()

                val startClock = currentMapClock

                val pauseDelay = patrol[3].pauseDelay
                repeat(pauseDelay) {
                    process()
                    assertEquals(patrol[3].destination, coords)
                    assertTrue(routeDestination.isEmpty())
                }

                // When the pause delay has passed, there is still a one cycle delay before the npc
                // begins moving.
                process()

                assertEquals(patrol[4].destination, routeDestination.peekLast())
                assertEquals(patrol[3].destination.translateX(1), coords)

                assertEquals(pauseDelay + 1, currentMapClock - startClock)
            }
        }
    }

    /**
     * NPCs should have to wait [IDLE_TELE_DELAY] + 2 cycles standing still before they actually
     * teleport.
     */
    @Test
    fun GameTestState.`emulate teleport delay timing`() = runBasicGameTest {
        withCollisionState {
            val patrol = NpcPatrol(LUMBRIDGE_CASTLE_PATROL)
            val start = patrol[2].destination
            val type = npcTypeFactory.create { this.patrol = patrol }
            val hans = npcFactory.create(type, start) { patrolWaypointIndex = 2 }
            it.allocateCollision(MapSquareKey(50, 50))
            it.collision.add(start.translateZ(-3), CollisionFlag.LOC)
            withNpc(hans) {
                val movement = NpcMovementProcessor(it.collision, it.stepFactory, eventBus)
                val mode = NpcPatrolModeProcessor(it.collision)
                fun process() {
                    previousCoords = coords
                    currentMapClock++
                    processedMapClock++
                    mode.process(this)
                    movement.process(this)
                }

                repeat(2) { process() }
                assertEquals(start.translateZ(-2), coords)
                assertEquals(patrol[3].destination, routeDestination.peekLast())

                val startClock = currentMapClock

                val teleDelay = IDLE_TELE_DELAY + 1
                repeat(teleDelay) {
                    process()
                    assertEquals(start.translateZ(-2), coords)
                    assertEquals(patrol[3].destination, routeDestination.peekLast())
                }

                // When the teleport delay hits, there is still a one clock delay before the npc is
                // actually teleported.
                process()

                assertEquals(patrol[3].destination, coords)

                assertEquals(teleDelay + 1, currentMapClock - startClock)
            }
        }
    }

    @Test
    fun GameTestState.`process hans lumbridge castle patrol`() = runBasicGameTest {
        withCollisionState {
            val patrol = NpcPatrol(LUMBRIDGE_CASTLE_PATROL)
            val start = patrol[0].destination.translateX(-1)
            val type = npcTypeFactory.create { this.patrol = patrol }
            val hans = npcFactory.create(type, start)
            it.allocateCollision(MapSquareKey(50, 50))
            withNpc(hans) {
                val movement = NpcMovementProcessor(it.collision, it.stepFactory, eventBus)
                val mode = NpcPatrolModeProcessor(it.collision)
                fun process() {
                    previousCoords = coords
                    currentMapClock++
                    processedMapClock++
                    mode.process(this)
                    movement.process(this)
                }

                val startClock = currentMapClock

                repeat(1) { process() }
                assertEquals(patrol[0].destination, coords)

                repeat(4) { process() }
                assertEquals(patrol[1].destination, coords)

                repeat(8) { process() }
                assertEquals(patrol[2].destination, coords)

                repeat(8) { process() }
                assertEquals(patrol[3].destination, coords)

                repeat(10) {
                    process()
                    assertEquals(patrol[3].destination, coords)
                }

                repeat(2) { process() }
                assertEquals(patrol[4].destination, coords)

                repeat(10) { process() }
                assertEquals(patrol[5].destination, coords)

                repeat(3) { process() }
                assertEquals(patrol[6].destination, coords)

                repeat(4) { process() }
                assertEquals(patrol[7].destination, coords)

                repeat(12) { process() }
                assertEquals(patrol[8].destination, coords)

                repeat(27) { process() }
                assertEquals(patrol[9].destination, coords)

                repeat(5) { process() }
                assertEquals(patrol[0].destination, coords)

                assertEquals(94, currentMapClock - startClock)

                repeat(4) { process() }
                assertEquals(patrol[1].destination, coords)
            }
        }
    }

    private companion object {
        private const val IDLE_TELE_DELAY = NpcPatrolModeProcessor.IDLE_TELE_DELAY

        private val LUMBRIDGE_CASTLE_PATROL =
            listOf(
                NpcPatrolWaypoint(CoordGrid(0, 50, 50, 7, 33), 0),
                NpcPatrolWaypoint(CoordGrid(0, 50, 50, 11, 30), 0),
                NpcPatrolWaypoint(CoordGrid(0, 50, 50, 19, 30), 0),
                NpcPatrolWaypoint(CoordGrid(0, 50, 50, 19, 22), 10),
                NpcPatrolWaypoint(CoordGrid(0, 50, 50, 21, 22), 0),
                NpcPatrolWaypoint(CoordGrid(0, 50, 50, 21, 12), 0),
                NpcPatrolWaypoint(CoordGrid(0, 50, 50, 18, 9), 0),
                NpcPatrolWaypoint(CoordGrid(0, 50, 50, 14, 5), 0),
                NpcPatrolWaypoint(CoordGrid(0, 50, 50, 2, 5), 0),
                NpcPatrolWaypoint(CoordGrid(0, 50, 50, 2, 32), 0),
            )
    }
}
