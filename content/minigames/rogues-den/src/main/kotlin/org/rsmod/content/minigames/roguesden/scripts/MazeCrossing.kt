package org.rsmod.content.minigames.roguesden.scripts

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.BasType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.forcedWalk
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid

// Movement replaces ordinary animations; hold the obstacle pose through a BAS override.
internal suspend fun ProtectedAccess.crossMazeObstacle(loc: BoundLocInfo, seq: String) {
    val (dx, dz) = stepDirection(from = player.coords, to = loc.coords)
    val span = maxOf(loc.adjustedWidth, loc.adjustedLength, 1)
    val dest = loc.coords.translate(dx * span, dz * span)
    val crossTiles = span + 1
    withCrossingPose(seq) {
        resetAnim()
        forcedWalk(listOf(loc.coords, dest), crossTiles)
    }
}

private fun stepDirection(from: CoordGrid, to: CoordGrid): Pair<Int, Int> {
    val dx = (to.x - from.x).coerceIn(-1, 1)
    val dz = (to.z - from.z).coerceIn(-1, 1)
    return dx to dz
}

private suspend fun ProtectedAccess.withCrossingPose(seq: String, block: suspend () -> Unit) {
    val anim = seq.asRSCM(RSCMType.SEQ)
    val previous = player.bas
    player.bas =
        BasType(
            readyAnim = anim,
            turnOnSpot = anim,
            walkForward = anim,
            walkBack = anim,
            walkLeft = anim,
            walkRight = anim,
            running = anim,
        )
    player.rebuildAppearance()
    try {
        block()
    } finally {
        player.bas = previous
        player.rebuildAppearance()
    }
}
