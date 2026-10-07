package org.rsmod.content.bosses.callisto

import org.rsmod.api.game.process.npc.NpcMovementProcessor
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.game.movement.RouteRequestPathingEntity
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

internal fun CollisionFlagMap.bearBlocked(tile: CoordGrid): Boolean =
    this[tile.x, tile.z, tile.level] and (CollisionFlag.BLOCK_WALK or CollisionFlag.LOC or CollisionFlag.GROUND_DECOR) != 0

internal fun chaseBear(npc: Npc, target: Player, movement: NpcMovementProcessor) {
    // Post-movement AP callbacks must not move the NPC twice in one cycle.
    if (npc.hasMovedThisCycle || npc.coords.level != target.coords.level || npc.movementLocked || CallistoRules.footprintDistance(npc.coords, npc.size, target.coords) <= 1) return
    npc.routeRequest = RouteRequestPathingEntity(target.avatar)
    npc.tempMoveSpeed = MoveSpeed.Run
    movement.process(npc)
}

internal object CallistoRules {
    fun enragedStage(hp: Int, maximum: Int): Int = when {
        hp <= 0 || maximum <= 0 -> 0
        hp.toLong() * 3 <= maximum -> 2
        hp.toLong() * 3 <= maximum.toLong() * 2 -> 1
        else -> 0
    }

    fun footprintDistance(origin: CoordGrid, size: Int, target: CoordGrid): Int {
        if (origin.level != target.level) return Int.MAX_VALUE
        val dx = maxOf(origin.x - target.x, target.x - (origin.x + size - 1), 0)
        val dz = maxOf(origin.z - target.z, target.z - (origin.z + size - 1), 0)
        return maxOf(dx, dz)
    }

    fun knockbackPath(
        source: CoordGrid,
        victim: CoordGrid,
        canStep: (CoordGrid, CoordGrid) -> Boolean,
    ): List<CoordGrid> {
        val dx = (victim.x - source.x).compareTo(0)
        val dz = (victim.z - source.z).compareTo(0)
        if (source.level != victim.level || (dx == 0 && dz == 0)) return emptyList()
        val steps = mutableListOf<CoordGrid>()
        var from = victim
        repeat(3) {
            val next = from.translate(dx, dz)
            if (!canStep(from, next)) return steps
            steps += next
            from = next
        }
        return steps
    }

    fun knockbackDamage(completedSteps: Int): Int = 5 + 15 * (3 - completedSteps.coerceIn(0, 3))

    fun meleeDamage(raw: Int, protectMelee: Boolean): Int = if (protectMelee) raw / 2 else raw

    fun canResolve(active: Boolean, currentEpoch: Int, attackEpoch: Int, hp: Int): Boolean =
        active && currentEpoch == attackEpoch && hp > 0
}

internal enum class BearVariant(
    val npc: String,
    val attackTicks: Int,
    val melee: IntRange,
    val rangedMax: Int,
    val killcount: String,
) {
    CALLISTO("npc.callisto", 4, 35..55, 31, "varp.total_callisto_kills"),
    ARTIO("npc.callisto_singles", 5, 15..35, 17, "varp.total_artio_kills"),
}
