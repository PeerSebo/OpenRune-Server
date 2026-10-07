package org.rsmod.content.skills.agility.scripts

import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.game.entity.util.EntityFaceAngle
import org.rsmod.map.CoordGrid

internal data class AgilityMovement(
    val startDelay: Int,
    val endDelay: Int,
    val angle: Int? = null,
    val visualStart: CoordGrid? = null,
    val visualEnd: CoordGrid? = null,
) {
    init {
        require(startDelay >= 0 && endDelay > startDelay)
        require(angle == null || angle in 0..2047)
    }
}

// Resolve bearing before relocation; deferred faceSquare can otherwise turn back or lose its target.
internal fun ProtectedAccess.faceObstacle(target: CoordGrid) {
    val bearing = player.calculateAngle(CoordGrid(target.x, target.z, player.coords.level), 1, 1)
    faceObstacleAngle(bearing ?: player.faceAngle)
}

internal fun ProtectedAccess.faceObstacleAngle(angle: Int) {
    player.resetPendingFaceSquare()
    player.faceAngle = angle
    player.pendingFaceAngle = EntityFaceAngle(angle)
}

// Keep visual and committed endpoints separate: the client interpolates after the server commits the landing.
internal fun ProtectedAccess.glideTo(dest: CoordGrid, movement: AgilityMovement) {
    val start = movement.visualStart ?: player.coords
    val visualEnd = movement.visualEnd ?: dest
    val angle = movement.angle ?: player.calculateAngle(CoordGrid(dest.x, dest.z, player.coords.level), 1, 1) ?: player.faceAngle
    exactMove(start, dest, movement.startDelay, movement.endDelay, angle, TeleportType.Exempt)
    if (visualEnd != dest) {
        player.pendingExactMove = player.pendingExactMove?.copy(
            deltaX2 = visualEnd.x - dest.x,
            deltaZ2 = visualEnd.z - dest.z,
        )
    }
    faceObstacleAngle(angle)
}
