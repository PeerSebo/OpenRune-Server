package org.rsmod.content.skills.agility.configs

import org.rsmod.map.CoordGrid

object AgilityPyramidCourse {
    val COURSE = AgilityCourse(
        id = 14, displayName = "Agility Pyramid", lapCountVarp = "varp.agility_laps_pyramid",
        levelReq = 30, obstacleCount = 31, lapBonusXp = 0.0, restoresRunEnergy = false,
    )
    const val TOP_OBJ = "obj.agility_pyramid_gold_pyramid"
    const val SIMON_NPC = "npc.agility_pyramid_simon"
    const val SIMON_REWARD_GP = 10_000
    val EXIT_DEST = CoordGrid(3364, 2830, 0)
    val TOP_ROCKS = setOf(CoordGrid(3043, 4697, 3), CoordGrid(3043, 4698, 3))

    enum class Kind(val xp: Double, val failDamage: Int) {
        Stairs(0.0, 0), Stone(12.0, 6), Wall(8.0, 4), Ledge(52.0, 10),
        Plank(56.4, 10), Wallhang(56.4, 8), Jump(22.0, 8), Exit(0.0, 0),
    }
    data class Obstacle(
        val index: Int, val kind: Kind, val mount: CoordGrid, val dest: CoordGrid,
        val clicks: List<CoordGrid>,
    )
    private fun tile(x: Int, z: Int, level: Int) = CoordGrid(x, z, level)
    private fun step(index: Int, kind: Kind, mount: CoordGrid, dest: CoordGrid, clicks: List<CoordGrid> = emptyList()) =
        Obstacle(index, kind, mount, dest, clicks)

    val OBSTACLES = listOf(
        step(0, Kind.Stairs, tile(3355, 2830, 0), tile(3355, 2833, 1), listOf(tile(3354, 2831, 0))),
        step(1, Kind.Stone, tile(3355, 2841, 1), tile(3355, 2843, 1)),
        step(2, Kind.Wall, tile(3355, 2848, 1), tile(3355, 2850, 1), listOf(tile(3354, 2849, 1))),
        step(3, Kind.Ledge, tile(3363, 2851, 1), tile(3368, 2851, 1), listOf(tile(3364, 2851, 1), tile(3366, 2851, 1))),
        step(4, Kind.Plank, tile(3375, 2845, 1), tile(3375, 2840, 1), listOf(tile(3375, 2845, 1), tile(3375, 2840, 1))),
        step(5, Kind.Stone, tile(3375, 2836, 1), tile(3375, 2834, 1)),
        step(6, Kind.Wallhang, tile(3372, 2832, 1), tile(3367, 2832, 1), listOf(tile(3370, 2831, 1), tile(3368, 2831, 1), tile(3372, 2832, 1), tile(3367, 2832, 1))),
        step(7, Kind.Ledge, tile(3364, 2832, 1), tile(3359, 2832, 1), listOf(tile(3362, 2831, 1), tile(3360, 2831, 1))),
        step(8, Kind.Stairs, tile(3357, 2832, 1), tile(3356, 2835, 2), listOf(tile(3356, 2833, 1))),
        step(9, Kind.Wallhang, tile(3357, 2836, 2), tile(3357, 2841, 2), listOf(tile(3356, 2837, 2), tile(3356, 2839, 2), tile(3357, 2836, 2), tile(3357, 2841, 2))),
        step(10, Kind.Jump, tile(3357, 2846, 2), tile(3357, 2849, 2), listOf(tile(3356, 2847, 2))),
        step(11, Kind.Wallhang, tile(3359, 2849, 2), tile(3364, 2849, 2), listOf(tile(3360, 2849, 2), tile(3362, 2849, 2), tile(3359, 2849, 2), tile(3364, 2849, 2))),
        step(12, Kind.Stone, tile(3368, 2849, 2), tile(3370, 2849, 2)),
        step(13, Kind.Ledge, tile(3372, 2841, 2), tile(3372, 2836, 2), listOf(tile(3372, 2839, 2), tile(3372, 2837, 2))),
        step(14, Kind.Wall, tile(3371, 2834, 2), tile(3369, 2834, 2), listOf(tile(3370, 2833, 2))),
        step(15, Kind.Jump, tile(3366, 2834, 2), tile(3363, 2834, 2), listOf(tile(3364, 2833, 2))),
        step(16, Kind.Stairs, tile(3359, 2834, 2), tile(3358, 2837, 3), listOf(tile(3358, 2835, 2))),
        step(17, Kind.Wall, tile(3358, 2838, 3), tile(3358, 2840, 3), listOf(tile(3358, 2839, 3))),
        step(18, Kind.Ledge, tile(3359, 2842, 3), tile(3359, 2847, 3), listOf(tile(3358, 2843, 3), tile(3358, 2845, 3))),
        step(19, Kind.Jump, tile(3370, 2843, 3), tile(3370, 2840, 3), listOf(tile(3370, 2841, 3))),
        step(20, Kind.Plank, tile(3370, 2835, 3), tile(3365, 2835, 3), listOf(tile(3370, 2835, 3), tile(3365, 2835, 3))),
        step(21, Kind.Stairs, tile(3361, 2836, 3), tile(3041, 4695, 2), listOf(tile(3360, 2837, 3))),
        step(22, Kind.Jump, tile(3041, 4696, 2), tile(3041, 4699, 2), listOf(tile(3040, 4697, 2))),
        step(23, Kind.Wall, tile(3041, 4701, 2), tile(3043, 4701, 2), listOf(tile(3042, 4701, 2))),
        step(24, Kind.Stone, tile(3048, 4700, 2), tile(3048, 4698, 2)),
        step(25, Kind.Jump, tile(3048, 4697, 2), tile(3048, 4694, 2), listOf(tile(3048, 4695, 2))),
        step(26, Kind.Wall, tile(3048, 4694, 2), tile(3046, 4694, 2), listOf(tile(3047, 4693, 2))),
        step(27, Kind.Stairs, tile(3043, 4694, 2), tile(3043, 4697, 3), listOf(tile(3042, 4695, 2))),
        step(28, Kind.Stone, tile(3044, 4699, 3), tile(3046, 4699, 3)),
        step(29, Kind.Jump, tile(3046, 4699, 3), tile(3046, 4696, 3), listOf(tile(3046, 4697, 3))),
        step(30, Kind.Exit, tile(3045, 4696, 3), EXIT_DEST, listOf(tile(3045, 4696, 3), tile(3044, 4696, 3))),
    )
    private val byClick = OBSTACLES.flatMap { step -> step.clicks.map { it to step } }.toMap()
    private val byStoneMount = OBSTACLES.filter { it.kind == Kind.Stone }.associateBy { it.mount }
    fun clickedAt(tile: CoordGrid): Obstacle? = byClick[tile]
    fun stoneAt(tile: CoordGrid): Obstacle? = byStoneMount[tile]

    // Running may cross the trigger on an intermediate tile; check both movement steps.
    fun crossedStone(previous: CoordGrid, intermediate: CoordGrid, current: CoordGrid): Obstacle? {
        if (previous == current || previous.level != current.level || previous.chebyshevDistance(current) > 2) return null
        if (intermediate != CoordGrid.NULL && (intermediate.level != current.level || intermediate.chebyshevDistance(current) > 1)) return null
        val step = stoneAt(current) ?: stoneAt(intermediate) ?: return null
        val dx = step.dest.x - step.mount.x
        val dz = step.dest.z - step.mount.z
        // Signed projection distinguishes opposite travel through the midpoint.
        val approach = (previous.x - step.mount.x) * dx + (previous.z - step.mount.z) * dz
        val movement = (current.x - previous.x) * dx + (current.z - previous.z) * dz
        return step.takeIf { approach <= 0 && movement >= 0 && current.level == it.mount.level }
    }

    data class FailureLanding(val dest: CoordGrid, val expectedIndex: Int?)

    fun failureLanding(step: Obstacle): FailureLanding {
        if (step.kind != Kind.Stone && step.kind != Kind.Ledge) return FailureLanding(step.mount, null)
        val checkpoint = when {
            step.mount.x in 3040..3048 && step.mount.level == 3 -> 21
            step.mount.x in 3040..3048 && step.mount.level == 2 -> 16
            step.mount.level == 3 -> 8
            step.mount.level == 2 -> 0
            else -> return FailureLanding(OBSTACLES[0].mount, 0)
        }
        return FailureLanding(OBSTACLES[checkpoint].dest, checkpoint + 1)
    }

    fun isForwardApproach(step: Obstacle, from: CoordGrid): Boolean =
        from.level == step.mount.level &&
            (step.kind == Kind.Stairs || step.kind == Kind.Exit ||
                from.chebyshevDistance(step.mount) <= from.chebyshevDistance(step.dest))

    fun completionXp(baseLevel: Int): Double = minOf(1000, 300 + 8 * baseLevel.coerceIn(1, 99)).toDouble()

    val CLICKABLE_LOCS = listOf(
        "loc.agility_pyramid_steps1", "loc.agility_pyramid_jump_hotspot",
        "loc.agility_pyramid_low_wall", "loc.agility_pyramid_ledge_hotspot",
        "loc.agility_pyramid_plank_start", "loc.agility_pyramid_plank_end",
        "loc.agility_pyramid_wallhang_start_south_hotspot", "loc.agility_pyramid_wallhang_end_south_hotspot",
        "loc.agility_pyramid_wallhang_start_west_hotspot", "loc.agility_pyramid_wallhang_end_west_hotspot",
        "loc.agility_pyramid_wallhang_start_hotspot_ground", "loc.agility_pyramid_wallhang_end_hotspot_ground",
        "loc.agility_pyramid_wallhang_start_hotspot", "loc.agility_pyramid_wallhang_end_hotspot",
        "loc.agility_pyramid_ledgebalance_start_south_hotspot", "loc.agility_pyramid_ledgebalance_end_south_hotspot",
        "loc.agility_pyramid_ledgebalance_start_west_hotspot", "loc.agility_pyramid_ledgebalance_end_west_hotspot",
        "loc.agility_pyramid_door_hotspot", "loc.agility_pyramid_door_hotspot_mirror",
    )
}
