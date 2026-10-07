package org.rsmod.api.player

import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.EntityExactMove
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

public fun Player.knockback(destination: CoordGrid, direction: Int, collision: CollisionFlagMap) {
    require(destination.level == level) { "Knockback cannot change floors." }
    val start = coords
    cancelActiveCoroutine()
    clearInteraction()
    abortRoute()
    resetFaceEntity()
    resetAnim()
    anim("seq.knockback_30")
    delay = maxOf(delay, currentMapClock + 3)
    if (destination == start) return

    // Keep the client's path origin; telejump's 127 speed snaps its model to the destination.
    PathingEntityCommon.teleport(this, collision, destination)
    pendingTelejump = false
    pendingExactMove = EntityExactMove(
        deltaX1 = start.x - destination.x,
        deltaZ1 = start.z - destination.z,
        deltaX2 = 0,
        deltaZ2 = 0,
        clientDelay1 = 0,
        clientDelay2 = 30,
        direction = direction,
    )
    faceAngle = direction
}
