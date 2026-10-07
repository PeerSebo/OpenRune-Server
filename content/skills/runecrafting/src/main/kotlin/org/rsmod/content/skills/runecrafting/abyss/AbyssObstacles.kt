package org.rsmod.content.skills.runecrafting.abyss

internal enum class AbyssObstacle(
    val loc: String,
    val statKey: String?,
    val xp: Double,
    val requiredTool: String? = null,
) {
    TEETH("loc.rcu_abyssal_barrier_teeth1", "stat.mining", 25.0),
    TENDRILS("loc.rcu_abyssal_barrier_tendrils1", "stat.woodcutting", 25.0),
    BOIL("loc.rcu_abyssal_barrier_boil1", "stat.firemaking", 25.0, requiredTool = "obj.tinderbox"),
    EYES("loc.rcu_abyssal_barrier_eyes1", "stat.thieving", 25.0),
    GAP("loc.rcu_abyssal_barrier_agility", "stat.agility", 25.0),

    PASSAGE("loc.rcu_blankmodel", null, 0.0),
}
