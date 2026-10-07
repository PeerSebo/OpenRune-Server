package org.rsmod.api.game.process.npc.mode

import jakarta.inject.Inject
import org.rsmod.api.route.StepFactory
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid

public class NpcPlayerEscapeModeProcessor
@Inject
constructor(private val playerList: PlayerList, private val stepFactory: StepFactory) {
    private val Npc.retreatRange: Int
        get() = type.maxRange

    public fun process(npc: Npc) {
        val target = npc.facingTarget(playerList)
        if (target == null) {
            npc.resetMode()
        } else if (!npc.inValidDistance(target)) {
            npc.resetMode()
        } else {
            npc.retreatFrom(target)
        }
    }

    /**
     * Cross-plane targets are outside escape range; planar distance checks cannot represent
     * them.
     */
    private fun Npc.inValidDistance(target: Player): Boolean =
        level == target.level && isWithinDistance(target, VALID_DISTANCE)

    private fun Npc.retreatFrom(target: Player) {
        val collision = collisionStrategy
        if (collision == null) {
            resetMode()
            return
        }
        val dest = retreatDest(target.coords)
        val reachedMaxRange = coords == dest
        if (reachedMaxRange) {
            return
        }
        val validatedDest = stepFactory.validated(this, coords, dest, collision)
        if (validatedDest != CoordGrid.NULL) {
            walk(validatedDest)
        } else if (cyclesWithoutMovement > COLLISION_RETREAT_RESET_DELAY) {
            resetMode()
        }
    }

    private fun Npc.retreatDest(target: CoordGrid): CoordGrid {
        val minX = spawnCoords.x - retreatRange
        val maxX = spawnCoords.x + retreatRange
        val minZ = spawnCoords.z - retreatRange
        val maxZ = spawnCoords.z + retreatRange
        val retreatDir = retreatDirection(target)
        val coercedDestX = (coords.x + retreatDir.xOff).coerceIn(minX, maxX)
        val coercedDestZ = (coords.z + retreatDir.zOff).coerceIn(minZ, maxZ)
        return CoordGrid(coercedDestX, coercedDestZ, level)
    }

    private fun Npc.retreatDirection(other: CoordGrid): Direction =
        when {
            x <= other.x && z <= other.z -> Direction.SouthWest
            x <= other.x -> Direction.NorthWest
            z <= other.z -> Direction.SouthEast
            else -> Direction.NorthEast
        }

    public companion object {
        public const val VALID_DISTANCE: Int = 25
        public const val COLLISION_RETREAT_RESET_DELAY: Int = 3
    }
}
