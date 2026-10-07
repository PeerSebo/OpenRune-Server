package org.rsmod.content.bosses.derangedarchaeologist

import org.rsmod.api.random.GameRandom
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid

internal class ArchaeologistBooks(private val random: GameRandom, private val queues: WorldQueueList) {
    fun throwBooks(
        source: CoordGrid,
        target: CoordGrid,
        launch: (CoordGrid, CoordGrid, Int) -> Unit,
        impact: (CoordGrid) -> Unit,
    ): List<CoordGrid> {
        val tiles = mutableListOf(target)
        val candidates = buildList {
            for (dx in -4..4) for (dz in -4..4) if (dx != 0 || dz != 0) add(target.translate(dx, dz))
        }.toMutableList()
        repeat(4) {
            val preferred = candidates.filter { it.chebyshevDistance(target) <= 2 }
            val pool = if (preferred.isNotEmpty() && random.of(8) != 0) preferred else candidates
            val tile = pool[random.of(pool.size)]
            candidates.remove(tile)
            tiles += tile
        }
        tiles.take(3).forEach { launch(source, it, FIRST_IMPACT_TICKS) }
        // Queue both waves now: nested WorldQueue additions can otherwise lose a countdown tick.
        queues.add(FIRST_IMPACT_TICKS) {
            tiles.take(3).forEach(impact)
            tiles.drop(3).forEach { launch(target, it, SECOND_IMPACT_TICKS - FIRST_IMPACT_TICKS) }
        }
        queues.add(SECOND_IMPACT_TICKS) { tiles.drop(3).forEach(impact) }
        return tiles
    }

    companion object {
        const val FIRST_IMPACT_TICKS = 4
        const val SECOND_IMPACT_TICKS = 6
        fun maxDamage(player: CoordGrid, explosion: CoordGrid): Int =
            when {
                player.level != explosion.level -> 0
                player == explosion -> 56
                player.chebyshevDistance(explosion) == 1 -> 18
                else -> 0
            }
    }
}
