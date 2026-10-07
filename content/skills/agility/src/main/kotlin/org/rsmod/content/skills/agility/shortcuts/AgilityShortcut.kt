package org.rsmod.content.skills.agility.shortcuts

import org.rsmod.content.skills.agility.AgilityAnims
import org.rsmod.map.CoordGrid

sealed class ShortcutMove {
    data class Climb(
        val seq: String = AgilityAnims.CLIMB,
        val ticks: Int? = null,
        val sound: String? = null,
    ) : ShortcutMove()

    data class Jump(val seq: String = AgilityAnims.JUMP_UP, val ticks: Int? = null) : ShortcutMove()

    data class Squeeze(
        val enter: String = AgilityAnims.CRACK_ENTER,
        val leave: String = AgilityAnims.CRACK_LEAVE,
        val ticks: Int = 2,
    ) : ShortcutMove()

    data class Tunnel(
        val enter: String = AgilityAnims.TUNNEL_ENTER,
        val walk: String = AgilityAnims.TUNNEL_WALK,
        val leave: String = AgilityAnims.TUNNEL_EXIT,
    ) : ShortcutMove()

    data class Balance(val tiles: List<CoordGrid>) : ShortcutMove()

    data class Hop(val stones: List<CoordGrid>) : ShortcutMove()

    companion object {
        val PIPE: Squeeze = Squeeze(AgilityAnims.PIPE_SQUEEZE, AgilityAnims.PIPE_UNSQUEEZE, ticks = 3)
    }
}

data class AgilityShortcut(
    val name: String,
    val level: Int,
    val xp: Double,
    val locs: List<String>,
    val sideA: CoordGrid,
    val sideB: CoordGrid,
    val move: ShortcutMove,
) {
    init {
        require(sideA != sideB) { "Shortcut '$name' needs two distinct sides." }
        require(locs.isNotEmpty()) { "Shortcut '$name' needs at least one loc." }
    }

    fun distanceTo(coords: CoordGrid): Int = minOf(coords.distanceTo(sideA), coords.distanceTo(sideB))

    fun startsFromA(coords: CoordGrid): Boolean =
        coords.distanceTo(sideA) <= coords.distanceTo(sideB)

    private fun CoordGrid.distanceTo(other: CoordGrid): Int =
        chebyshevDistance(other) + LEVEL_PENALTY * kotlin.math.abs(level - other.level)

    private companion object {
        const val LEVEL_PENALTY = 1000
    }
}
