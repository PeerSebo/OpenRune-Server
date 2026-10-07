package org.rsmod.content.bosses.wildernessarchaeologists

import org.rsmod.api.random.GameRandom
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid

internal class GroundVolley(private val queues: WorldQueueList) {
    fun launch(
        centres: List<CoordGrid>,
        delay: Int,
        radius: Int,
        active: () -> Boolean,
        victimPosition: () -> CoordGrid?,
        impact: (CoordGrid) -> Unit,
        hit: () -> Unit,
    ) {
        require(delay >= 1)
        require(radius >= 0)
        val snapshot = centres.toList()
        queues.add(delay) {
            if (!active()) return@add
            // Read the victim once at impact. Every overlapping explosion is a separate hit.
            val position = victimPosition()
            for (centre in snapshot) {
                impact(centre)
                if (position != null && contains(centre, position, radius)) hit()
            }
        }
    }

    companion object {
        fun contains(centre: CoordGrid, tile: CoordGrid, radius: Int): Boolean =
            centre.level == tile.level && centre.chebyshevDistance(tile) <= radius

        fun scatter(target: CoordGrid, random: GameRandom): List<CoordGrid> =
            listOf(target, random.of(target, 2), random.of(target, 2))
    }
}
