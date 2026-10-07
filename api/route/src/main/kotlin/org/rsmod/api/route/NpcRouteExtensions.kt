package org.rsmod.api.route

import dev.openrune.types.MoveRestrict
import org.rsmod.game.entity.Npc
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionStrategy

/**
 * onArrival also fires for a nearest-tile route or next post-tick when no route exists.
 * PassThru ignores entities, not walls; restore the original restriction afterward.
 */
public fun Npc.walkTo(
    routeFactory: RouteFactory,
    dest: CoordGrid,
    collision: CollisionStrategy = CollisionStrategy.Normal,
    passThroughEntities: Boolean = true,
    speed: MoveSpeed = MoveSpeed.Walk,
    onArrival: (() -> Unit)? = null,
) {
    val route = routeFactory.create(avatar, dest, collision)
    val previousMoveRestrict = moveRestrict
    if (passThroughEntities) {
        moveRestrict = MoveRestrict.PassThru
    }
    walk(route.map { CoordGrid(it.x, it.z, it.level) }) {
        // Restore the original move restriction once the scripted walk finishes, before the caller's
        // arrival logic (which may re-engage combat) runs.
        moveRestrict = previousMoveRestrict
        onArrival?.invoke()
    }
    moveSpeed = speed
}
