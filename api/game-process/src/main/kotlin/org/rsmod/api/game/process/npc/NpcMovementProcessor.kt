package org.rsmod.api.game.process.npc

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import org.rsmod.api.npc.events.NpcMovementEvent
import org.rsmod.api.route.StepFactory
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.game.movement.RouteRequest
import org.rsmod.game.movement.RouteRequestCoord
import org.rsmod.game.movement.RouteRequestLoc
import org.rsmod.game.movement.RouteRequestPathingEntity
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.RouteCoordinates
import org.rsmod.routefinder.RouteFinding
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.collision.CollisionStrategy

public class NpcMovementProcessor
@Inject
constructor(
    private val collision: CollisionFlagMap,
    private val stepFactory: StepFactory,
    private val eventBus: EventBus,
) {
    private var lastCrossLevelWarnCycle: Int? = null

    public fun process(npc: Npc) {
        val freezeEnd = npc.vars["varn.freeze_end_clock"]
        if (npc.movementLocked || (freezeEnd > 0 && freezeEnd > npc.currentMapClock)) {
            npc.routeRequest = null
            npc.routeDestination.clear()
            return
        }
        npc.routeRequest?.let { consumeRequest(npc, it) }
        npc.routeRequest = null
        npc.processMovement()
        npc.resetTempSpeed()
    }

    public fun consumeRequest(npc: Npc, request: RouteRequest) {
        npc.routeTo(request)
    }

    private fun Npc.routeTo(request: RouteRequest) {
        val dest = request.destination(this)
        cachedMoveSpeed = tempMoveSpeed ?: defaultMoveSpeed
        moveSpeed = cachedMoveSpeed
        routeDestination.clear()
        routeDestination.add(dest)
    }

    private fun Npc.isFrozen(): Boolean = vars["varn.freeze_end_clock"] > currentMapClock

    private fun Npc.processMovement() {
        val collision = collisionStrategy
        if (collision != null) {
            processMoveSpeed(collision)
        } else if (routeDestination.isNotEmpty()) {
            resetMovement()
        }
    }

    private fun Npc.processMoveSpeed(collision: CollisionStrategy) {
        if (routeDestination.isEmpty()) {
            return
        }
        processWalkTrigger()

        val completeCrawlStep = moveSpeed == MoveSpeed.Crawl && !hasMovedPreviousCycle
        val steps = if (completeCrawlStep) 1 else moveSpeed.steps
        move(steps, collision)
    }

    private fun Npc.move(steps: Int, collision: CollisionStrategy) {
        val destination = routeDestination
        val waypoint = destination.peekFirst() ?: return
        if (waypoint.level != coords.level) {
            warnCrossLevelWaypoint(waypoint)
        }
        val start = coords
        var current = start
        var target = waypoint
        var stepCount = 0
        removeBlockWalkCollision(current)
        for (i in 0 until steps) {
            if (current == target) {
                target = destination.pollFirst() ?: break
                if (current == target) {
                    target = destination.peekFirst() ?: break
                }
            }

            val step = stepFactory.validated(this, current, target, collision)
            if (step == CoordGrid.NULL) {
                break
            }
            // Important to set this before `current` is assigned for this iteration. This serves
            // as a way to track the intermediate coord when running.
            lastProcessedCoord = current
            current = step
            stepCount++
        }
        if (current == target) {
            destination.pollFirst()
        }
        addBlockWalkCollision(current)
        updateMovementClock(current, start)
        pendingStepCount = stepCount
        coords = current
    }

    /**
     * A cross-plane waypoint cannot be reached and would stall the NPC forever; discard it
     * before planar stepping.
     */
    private fun Npc.warnCrossLevelWaypoint(waypoint: CoordGrid) {
        val cycle = currentMapClock
        val last = lastCrossLevelWarnCycle
        if (last != null && cycle - last < CROSS_LEVEL_WARN_INTERVAL) {
            return
        }
        lastCrossLevelWarnCycle = cycle
        logger.warn {
            "Npc route waypoint is on another level and can never be reached; npc is stuck " +
                "(further warnings suppressed for $CROSS_LEVEL_WARN_INTERVAL cycles): " +
                "npc=$this, waypoint=$waypoint."
        }
    }

    private fun Npc.addBlockWalkCollision(coords: CoordGrid) {
        addBlockWalkCollision(collision, coords)
    }

    private fun Npc.removeBlockWalkCollision(coords: CoordGrid) {
        removeBlockWalkCollision(collision, coords)
    }

    private fun Npc.resetTempSpeed() {
        if (routeDestination.isEmpty()) {
            tempMoveSpeed = null
        }
    }

    private fun Npc.updateMovementClock(current: CoordGrid, previous: CoordGrid) {
        if (current != previous) {
            lastMovement = currentMapClock
        }
    }

    private fun RouteRequest.destination(source: Npc): CoordGrid =
        when (this) {
            is RouteRequestCoord -> destination
            is RouteRequestPathingEntity ->
                RouteFinding.naiveDestination(
                        sourceX = source.coords.x,
                        sourceZ = source.coords.z,
                        sourceWidth = source.size,
                        sourceLength = source.size,
                        targetX = destination.coords.x,
                        targetZ = destination.coords.z,
                        targetWidth = destination.size,
                        targetLength = destination.size,
                    )
                    .toCoordGrid(source.coords.level)
            is RouteRequestLoc -> destination
        }

    private fun Npc.processWalkTrigger() {
        val trigger = walkTrigger ?: return
        clearWalkTrigger()
        val event = NpcMovementEvent.WalkTrigger(this, trigger)
        eventBus.publish(event)
    }

    private companion object {
        private val logger = InlineLogger()

        private const val CROSS_LEVEL_WARN_INTERVAL: Int = 100

        /**
         * naiveDestination returns plane 0 because it takes no level; restore the source plane
         * before enqueuing.
         */
        private fun RouteCoordinates.toCoordGrid(level: Int) = CoordGrid(x, z, level)
    }
}
