package org.rsmod.content.bosses.scorpia

import org.rsmod.api.npc.clearInteractionRoute
import org.rsmod.api.route.RayCastValidator
import org.rsmod.game.entity.Npc
import org.rsmod.game.movement.RouteRequestPathingEntity
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

internal fun CollisionFlagMap.scorpionBlocked(tile: CoordGrid): Boolean =
    this[tile.x, tile.z, tile.level] and (CollisionFlag.BLOCK_WALK or CollisionFlag.LOC or CollisionFlag.GROUND_DECOR) != 0

internal fun offspringCanReach(offspring: Npc, target: CoordGrid, ray: RayCastValidator): Boolean {
    val distance = ScorpiaRules.footprintDistance(offspring.coords, offspring.size, target)
    if (distance !in 1..offspring.attackRange.coerceAtMost(7)) return false
    return ray.hasLineOfSight(
        source = target,
        destination = offspring.coords,
        destWidth = offspring.size,
        destLength = offspring.size,
        extraFlag = CollisionFlag.BLOCK_PLAYERS,
    )
}

internal fun guideGuardian(guardian: Npc, queen: Npc, inRange: Boolean) {
    if (inRange) guardian.clearInteractionRoute()
    else guardian.routeRequest = RouteRequestPathingEntity(queen.avatar)
}

internal class ScorpiaLife {
    var summoned = false
        private set

    fun claimSummon(currentHp: Int, playerAttack: Boolean): Boolean {
        if (summoned || !playerAttack || currentHp !in 1..99) return false
        summoned = true
        return true
    }
}

internal enum class GuardianAction { WAIT, FOLLOW, HEAL, REMOVE }

internal class GuardianCycle(spawnTick: Int) {
    private var lastHeal = spawnTick
    private var nextHeal = spawnTick + 3

    fun tick(now: Int, ownerAlive: Boolean, inRange: Boolean): GuardianAction {
        if (!ownerAlive || now - lastHeal >= 25) return GuardianAction.REMOVE
        if (!inRange) return GuardianAction.FOLLOW
        if (now < nextHeal) return GuardianAction.WAIT
        lastHeal = now
        nextHeal = now + 3
        return GuardianAction.HEAL
    }
}

internal object ScorpiaRules {
    fun footprintDistance(origin: CoordGrid, size: Int, tile: CoordGrid): Int {
        if (origin.level != tile.level) return Int.MAX_VALUE
        val dx = maxOf(origin.x - tile.x, tile.x - (origin.x + size - 1), 0)
        val dz = maxOf(origin.z - tile.z, tile.z - (origin.z + size - 1), 0)
        return maxOf(dx, dz)
    }

    fun guardianSites(origin: CoordGrid, size: Int, open: (CoordGrid) -> Boolean): List<CoordGrid> {
        val middle = size / 2
        val preferred = listOf(origin.translate(-1, middle), origin.translate(size, middle))
        val ring = buildList {
            for (x in -2..size + 1) for (z in -2..size + 1) {
                val tile = origin.translate(x, z)
                if (footprintDistance(origin, size, tile) in 1..2) add(tile)
            }
        }
        return (preferred + ring).distinct().filter(open).take(2)
    }

    fun offspringAggressive(poisoned: Boolean, attackedByQueen: Boolean, retaliating: Boolean = false): Boolean =
        !poisoned || attackedByQueen || retaliating
}
