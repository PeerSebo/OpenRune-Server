package org.rsmod.content.skills.agility.scripts

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.skills.agility.AgilityAnims
import org.rsmod.map.CoordGrid

internal suspend fun ProtectedAccess.hopAcross(stones: List<CoordGrid>) {
    val first = stones.firstOrNull() ?: return
    val bearing = player.calculateAngle(first, 1, 1) ?: player.faceAngle
    if (player.faceAngle != bearing) {
        faceObstacleAngle(bearing)
        // Exact movement fixes client rotation; finish the ordinary turn before starting it.
        delay(1)
    }
    for ((index, stone) in stones.withIndex()) {
        resetAnim()
        anim(AgilityAnims.STEPPING_STONE, delay = ANIMATION_DELAY)
        soundSynth("synth.JUMP", delay = LANDING_DELAY)
        glideTo(stone, AgilityMovement(TAKEOFF_DELAY, LANDING_DELAY))
        delay(HOP_TICKS)
        resetAnim()
        if (index < stones.lastIndex) delay(1)
    }
}

private const val ANIMATION_DELAY = 19
private const val TAKEOFF_DELAY = 47
private const val LANDING_DELAY = 59
private const val HOP_TICKS = 3
