package org.rsmod.content.skills.sailing

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import org.rsmod.game.entity.WorldEntity

internal enum class SailingMoveMode(val value: Int) {
    Stopped(0),
    Slow(1),
    Forward(2),
    Reverse(3),
}

internal data class SailingMotionStats(
    val baseSpeed: Int,
    val acceleration: Int,
    val windDuration: Int,
) {
    init {
        require(baseSpeed > 0 && acceleration > 0 && windDuration > 0)
    }
}

internal data class SailingStep(val angle: Int, val dx: Int, val dz: Int)

internal class SailingMotion(initialAngle: Int) {
    var requestedAngle: Int = initialAngle
        private set

    var mode: SailingMoveMode = SailingMoveMode.Stopped
        private set

    var speed: Int = 0
        private set

    var windUntil: Int = 0
        private set

    fun heading(heading: Int) {
        require(heading in 0..15)
        requestedAngle = heading * 128
    }

    fun increase(): Boolean = when (mode) {
        SailingMoveMode.Reverse -> { stop(); true }
        SailingMoveMode.Stopped -> { mode = SailingMoveMode.Slow; true }
        SailingMoveMode.Slow -> { mode = SailingMoveMode.Forward; true }
        SailingMoveMode.Forward -> false
    }

    fun decrease(): Boolean = when (mode) {
        SailingMoveMode.Forward -> { mode = SailingMoveMode.Slow; true }
        SailingMoveMode.Slow -> { stop(); true }
        SailingMoveMode.Stopped -> { mode = SailingMoveMode.Reverse; true }
        SailingMoveMode.Reverse -> false
    }

    fun forward() { mode = SailingMoveMode.Forward }

    fun toggle() {
        if (mode == SailingMoveMode.Stopped) forward() else stop()
    }

    fun stop() {
        mode = SailingMoveMode.Stopped
    }

    fun halt() {
        stop()
        speed = 0
        windUntil = 0
    }

    fun boost(cycle: Int, stats: SailingMotionStats) {
        windUntil = cycle + stats.windDuration
    }

    fun step(actualAngle: Int, cycle: Int, stats: SailingMotionStats): SailingStep {
        val angle = turnTowards(actualAngle, requestedAngle)
        val target = when (mode) {
            SailingMoveMode.Stopped -> 0
            SailingMoveMode.Reverse -> -SLOW_SPEED
            SailingMoveMode.Slow -> minOf(SLOW_SPEED, stats.baseSpeed)
            SailingMoveMode.Forward -> stats.baseSpeed
        }
        speed = approach(speed, target, stats.acceleration)
        val boosted = speed + if (speed > 0 && cycle < windUntil) WIND_SPEED else 0
        val radians = angle * (2.0 * PI / WorldEntity.ANGLE_UNITS_PER_TURN)
        // WorldEntityInfoV3 deltas use quarter tiles; round vector axes independently to 32 fine units.
        val dx = (-sin(radians) * boosted / 32.0).roundToInt() * 32
        val dz = (-cos(radians) * boosted / 32.0).roundToInt() * 32
        return SailingStep(angle, dx, dz)
    }

    companion object {
        const val SLOW_SPEED = 64
        const val WIND_SPEED = 64

        fun turnTowards(actual: Int, target: Int): Int {
            val difference = ((target - actual + 1024) and 2047) - 1024
            return (actual + difference.coerceIn(-128, 128)) and 2047
        }

        private fun approach(current: Int, target: Int, step: Int): Int = when {
            current < target -> minOf(current + step, target)
            current > target -> maxOf(current - step, target)
            else -> current
        }
    }
}
