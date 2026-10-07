package org.rsmod.api.game.process.player

import jakarta.inject.Inject
import org.rsmod.api.mechanics.stun.Stun
import org.rsmod.api.mechanics.stun.isStunMovementBlocked
import org.rsmod.api.player.cheat.adminNoClip
import org.rsmod.api.player.events.PlayerMovementEvent
import org.rsmod.api.player.hook.PlayerMovementSpeedHook
import org.rsmod.api.player.output.MapFlag.setMapFlag
import org.rsmod.api.player.output.clearMapFlag
import org.rsmod.api.player.vars.varMoveSpeed
import org.rsmod.api.route.RouteFactory
import org.rsmod.api.route.StepFactory
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.map.Direction
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.game.movement.RouteDestination
import org.rsmod.game.movement.RouteRequest
import org.rsmod.game.movement.RouteRequestCoord
import org.rsmod.game.movement.RouteRequestLoc
import org.rsmod.game.movement.RouteRequestPathingEntity
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.Route
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

public class PlayerMovementProcessor
@Inject
constructor(
    private val collision: CollisionFlagMap,
    private val routeFactory: RouteFactory,
    private val stepFactory: StepFactory,
    private val eventBus: EventBus,
    private val speedHooks: Set<PlayerMovementSpeedHook> = emptySet(),
) {
    public fun process(player: Player) {
        player.routeRequest?.let { consumeRequest(player, it) }
        player.routeRequest = null
        player.processMoveSpeed()
        player.resetTempSpeed()
    }

    public fun consumeRequest(player: Player, request: RouteRequest) {
        player.routeTo(request)
    }

    private fun Player.routeTo(request: RouteRequest) {
        if (Stun.rejectBlockedMovement(this)) {
            return
        }
        cachedMoveSpeed = speedHooks.firstNotNullOfOrNull { it.overrideSpeed(this) }
            ?: tempMoveSpeed ?: varMoveSpeed
        moveSpeed = cachedMoveSpeed
        val route = routeFactory.create(avatar, request)
        // Admin "no clip" cheat: only take over when normal pathfinding cannot actually reach the
        // requested destination. Routes that do reach it are kept as-is so that loc and npc
        // interactions still resolve at their proper approach tile.
        if (adminNoClip && (route.failed || route.alternative)) {
            consumeNoClipRoute(request)
            return
        }
        consumeRoute(route)
    }

    private fun Player.consumeNoClipRoute(request: RouteRequest) {
        routeDestination.clear()
        val dest = request.noClipDestination()
        if (dest == coords) {
            clearMapFlag()
            return
        }
        routeDestination.add(dest)
        setMapFlag(this, dest.x, dest.z)
    }

    private fun RouteRequest.noClipDestination(): CoordGrid =
        when (this) {
            is RouteRequestCoord -> destination
            is RouteRequestLoc -> destination
            is RouteRequestPathingEntity -> destination.coords
        }

    private fun Player.consumeRoute(route: Route) {
        routeDestination.clear()
        routeDestination.addAll(route)
        if (route.isEmpty()) {
            clearMapFlag()
        } else {
            val dest = route.last()
            setMapFlag(this, dest.x, dest.z)
        }
    }

    private fun Player.processMoveSpeed() {
        // Unlike `isFrozen`, a stun does not merely pause stepping: any route that made it this far
        // (e.g. one appended by the interaction processor) is discarded and the map flag cleared, so
        // nothing is walked when the stun expires. The message is suppressed here because the
        // player was already told when the route was requested.
        if (isStunMovementBlocked) {
            Stun.rejectBlockedMovement(this, message = false)
            return
        }
        if (routeDestination.isEmpty() || !canProcessMovement || isFrozen) {
            return
        }
        processWalkTrigger()

        speedHooks.firstNotNullOfOrNull { it.overrideSpeed(this) }?.let { moveSpeed = it }

        val completeCrawlStep = moveSpeed == MoveSpeed.Crawl && !hasMovedPreviousCycle
        val steps = if (completeCrawlStep) 1 else moveSpeed.steps
        move(steps)

        // Keep the server run budget after a blocked second step. pendingStepCount separately
        // encodes one-tile movement as walking; downgrading moveSpeed would slow the remaining
        // route.
    }

    private fun Player.move(steps: Int) {
        val destination = routeDestination
        val waypoint = destination.peekFirst() ?: return
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

            val step = validatedStep(current, target)
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
        if (destination.isEmpty()) {
            clearMapFlag()
        }
        addBlockWalkCollision(current)
        updateMovementClock(current, start)
        pendingStepCount = stepCount
        coords = current
        if (stepCount > 0) {
            updateFaceAngle(current)
        }
    }

    private fun Player.updateFaceAngle(current: CoordGrid) {
        // `lastProcessedCoord` holds the tile occupied before the final step this cycle, so the
        // delta between the two maps to the direction of the last step taken.
        val previous = lastProcessedCoord
        val dx = current.x - previous.x
        val dz = current.z - previous.z
        val direction = Direction.entries.firstOrNull { it.xOff == dx && it.zOff == dz } ?: return
        faceAngle = direction.angle
    }

    private fun Player.validatedStep(current: CoordGrid, target: CoordGrid): CoordGrid =
        if (forcedRoute || adminNoClip) {
            // Scripted obstacle walk: step straight at the waypoint, ignoring collision. See
            // PathingEntity.forcedRoute for why this bypass exists and who is allowed to set it.
            naiveStep(current, target)
        } else {
            stepFactory.validated(
                source = current,
                dest = target,
                size = size,
                extraFlag = CollisionFlag.BLOCK_PLAYERS,
            )
        }

    private fun naiveStep(current: CoordGrid, target: CoordGrid): CoordGrid {
        val dx = (target.x - current.x).coerceIn(-1, 1)
        val dz = (target.z - current.z).coerceIn(-1, 1)
        if (dx == 0 && dz == 0) {
            return CoordGrid.NULL
        }
        return CoordGrid(current.x + dx, current.z + dz, current.level)
    }

    private fun Player.addBlockWalkCollision(coords: CoordGrid) {
        if (!hidden) {
            addBlockWalkCollision(collision, coords)
        }
    }

    private fun Player.removeBlockWalkCollision(coords: CoordGrid) {
        if (!hidden) {
            removeBlockWalkCollision(collision, coords)
        }
    }

    private fun Player.resetTempSpeed() {
        if (routeDestination.isEmpty()) {
            tempMoveSpeed = null
        }
    }

    private fun Player.updateMovementClock(current: CoordGrid, previous: CoordGrid) {
        if (current != previous) {
            lastMovement = currentMapClock
        }
    }

    private fun Player.processWalkTrigger() {
        val trigger = walkTrigger ?: return
        if (isBusy) {
            return
        }
        clearWalkTrigger()
        val event = PlayerMovementEvent.WalkTrigger(this, trigger)
        eventBus.publish(event)
    }

    private companion object {
        private fun RouteDestination.addAll(route: Route) {
            for (coord in route) {
                add(CoordGrid(coord.x, coord.z, coord.level))
            }
        }
    }
}
