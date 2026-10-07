package org.rsmod.content.generic.locs.passages

import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid

enum class PassageAction {
    OpenDoor,
    CloseDoor,
    OpenTrapdoor,
    ClimbUp,
    ClimbDown,
    ClimbEither,
    Enter,
    ClimbOver,
}

object Passages {
    private val DOOR_NAMES =
        setOf(
            "Door",
            "Doors",
            "Gate",
            "Gates",
            "Large door",
            "Big door",
            "Wooden door",
            "Metal door",
            "Iron door",
            "Cell door",
            "Prison door",
            "Wooden gate",
            "Iron gate",
        )

    private val CLIMB_NAMES =
        setOf(
            "Ladder",
            "Staircase",
            "Stairs",
            "Rope",
            "Trapdoor",
            "Hole",
            "Exit",
            "Steps",
            "Spiral staircase",
            "Rope ladder",
            "Vine",
            "Climbing rocks",
        )

    private val ENTER_NAMES =
        setOf(
            "Cave",
            "Cave entrance",
            "Cave exit",
            "Tunnel",
            "Tunnel entrance",
            "Hole",
            "Passage",
            "Passageway",
            "Crevice",
            "Entrance",
            "Exit",
            "Opening",
            "Dungeon entrance",
            "Cavern",
            "Cavern entrance",
            "Mine entrance",
            "Crack",
            "Gap",
        )

    private val CLIMB_OVER_NAMES = setOf("Stile", "Fence", "Low fence", "Broken fence", "Wall")

    private val ENTER_OPS =
        setOf(
            "Enter",
            "Exit",
            "Leave",
            "Pass",
            "Pass-through",
            "Squeeze-through",
            "Crawl",
            "Crawl-through",
            "Go-through",
            "Walk-through",
        )

    private val WALL_SHAPES =
        setOf(
            LocShape.WallStraight,
            LocShape.WallDiagonalCorner,
            LocShape.WallL,
            LocShape.WallSquareCorner,
            LocShape.WallDiagonal,
        )

    const val UNDERGROUND_Z = 6400

    fun classify(name: String, op: String, shape: LocShape): PassageAction? {
        val isWall = shape in WALL_SHAPES
        return when (op) {
            "Open" ->
                when {
                    name == "Trapdoor" -> PassageAction.OpenTrapdoor
                    name in DOOR_NAMES || isWall -> PassageAction.OpenDoor
                    else -> null
                }
            "Close" -> if (name in DOOR_NAMES || isWall) PassageAction.CloseDoor else null
            "Climb-up",
            "Walk-up" -> if (name in CLIMB_NAMES) PassageAction.ClimbUp else null
            "Climb-down",
            "Walk-down" -> if (name in CLIMB_NAMES) PassageAction.ClimbDown else null
            "Climb" -> if (name in CLIMB_NAMES) PassageAction.ClimbEither else null
            "Climb-over" -> if (name in CLIMB_OVER_NAMES) PassageAction.ClimbOver else null
            in ENTER_OPS ->
                when {
                    name in DOOR_NAMES -> PassageAction.ClimbOver
                    name in ENTER_NAMES -> PassageAction.Enter
                    else -> null
                }
            else -> null
        }
    }

    fun climbDestination(from: CoordGrid, up: Boolean): CoordGrid? {
        val underground = from.z >= UNDERGROUND_Z
        return if (up) {
            when {
                underground && from.level == 0 -> from.translateZ(-UNDERGROUND_Z)
                from.level < 3 -> from.translateLevel(1)
                else -> null
            }
        } else {
            when {
                from.level > 0 -> from.translateLevel(-1)
                !underground -> from.translateZ(UNDERGROUND_Z)
                else -> null
            }
        }
    }

    fun enterDestination(from: CoordGrid): CoordGrid =
        if (from.z >= UNDERGROUND_Z) {
            from.translateZ(-UNDERGROUND_Z)
        } else {
            from.translateZ(UNDERGROUND_Z)
        }

    fun farSide(loc: BoundLocInfo, from: CoordGrid): CoordGrid? {
        val minX = loc.x
        val maxX = loc.x + loc.adjustedWidth - 1
        val minZ = loc.z
        val maxZ = loc.z + loc.adjustedLength - 1
        val x =
            when {
                from.x < minX -> maxX + 1
                from.x > maxX -> minX - 1
                else -> from.x
            }
        val z =
            when {
                from.z < minZ -> maxZ + 1
                from.z > maxZ -> minZ - 1
                else -> from.z
            }
        if (x == from.x && z == from.z) {
            return null
        }
        return CoordGrid(x, z, from.level)
    }

    fun landingCandidates(dest: CoordGrid, radius: Int = 2): List<CoordGrid> {
        val tiles = mutableListOf<CoordGrid>()
        for (dz in -radius..radius) {
            for (dx in -radius..radius) {
                tiles += dest.translate(dx, dz)
            }
        }
        return tiles.sortedWith(compareBy({ it.chebyshevDistance(dest) }, { it.z }, { it.x }))
    }
}
