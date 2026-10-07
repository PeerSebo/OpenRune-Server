package org.rsmod.content.skills.hunter.tracking

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid

internal data class TrackingSpot(val type: String, val coords: CoordGrid) {
    val id: Int by lazy { type.asRSCM(RSCMType.LOC) }
    fun matches(loc: BoundLocInfo): Boolean = loc.id == id && loc.coords == coords
}
internal data class TrackingNode(val name: String, val coords: CoordGrid, val start: TrackingSpot? = null, val den: TrackingSpot? = null, val denIndex: Int = 0)
internal data class TrackingEdge(val suffix: String, val from: String, val to: String, val forward: Boolean, val fromClue: TrackingSpot?, val toClue: TrackingSpot?) {
    val varbit = "varbit.hunting_trail_state$suffix"
    fun other(node: String) = if (node == from) to else from
    fun clue(node: String) = if (node == from) fromClue else toClue
    fun state(node: String) = if ((node == from) == forward) 3 else 4
}
internal data class TrackingArea(val prey: TrackingPrey, val nodes: List<TrackingNode>, val edges: List<TrackingEdge>) {
    val byName = nodes.associateBy { it.name }
    val starts = nodes.mapNotNull { it.start }
    val dens = nodes.mapNotNull { it.den }
    val clues = edges.flatMap { listOfNotNull(it.fromClue, it.toClue) }.distinct()
}

internal object TrackingRoutes {
    private fun spot(type: String, x: Int, z: Int, level: Int = 0) = TrackingSpot("loc.$type", CoordGrid(x, z, level))
    val razor = TrackingArea(TrackingPrey.RAZOR, listOf(
        TrackingNode("S1", CoordGrid(2357, 3624, 0), spot("hunting_trail_spawn1", 2357, 3624, 0), null),
        TrackingNode("S2", CoordGrid(2353, 3595, 0), spot("hunting_trail_spawn2", 2353, 3595, 0), null),
        TrackingNode("S3", CoordGrid(2360, 3611, 0), spot("hunting_trail_spawn3", 2360, 3611, 0), null),
        TrackingNode("A", CoordGrid(2349, 3604, 0), null, spot("hunting_trail_end_bush29", 2349, 3604, 0)),
        TrackingNode("B", CoordGrid(2355, 3601, 0), null, spot("hunting_trail_end_bush29", 2355, 3601, 0)),
        TrackingNode("C", CoordGrid(2360, 3602, 0), null, spot("hunting_trail_end_bush29", 2360, 3602, 0)),
        TrackingNode("D", CoordGrid(2357, 3607, 0), null, spot("hunting_trail_end_bush29", 2357, 3607, 0)),
        TrackingNode("E", CoordGrid(2354, 3609, 0), null, spot("hunting_trail_end_bush29", 2354, 3609, 0)),
        TrackingNode("F", CoordGrid(2345, 3612, 0), null, null),
        TrackingNode("G", CoordGrid(2351, 3619, 0), null, spot("hunting_trail_end_bush29", 2351, 3619, 0)),
        TrackingNode("H", CoordGrid(2358, 3620, 0), null, spot("hunting_trail_end_bush29", 2358, 3620, 0)),
        TrackingNode("I", CoordGrid(2362, 3615, 0), null, spot("hunting_trail_end_bush29", 2362, 3615, 0)),
    ), listOf(
        TrackingEdge("1_0", "S2", "C", true, spot("hunting_trail_clue1_0", 2362, 3598, 0), spot("hunting_trail_clue1_0", 2362, 3598, 0)),
        TrackingEdge("1_1", "S2", "B", true, spot("hunting_trail_clue1_1", 2355, 3598, 0), spot("hunting_trail_clue1_1", 2355, 3598, 0)),
        TrackingEdge("1_2", "S2", "A", true, spot("hunting_trail_clue1_2", 2347, 3603, 0), spot("hunting_trail_clue1_2", 2347, 3603, 0)),
        TrackingEdge("1_3", "B", "C", false, spot("hunting_trail_clue1_3", 2358, 3599, 0), spot("hunting_trail_clue1_3", 2358, 3599, 0)),
        TrackingEdge("1_4", "A", "B", false, spot("hunting_trail_clue1_4", 2352, 3603, 0), spot("hunting_trail_clue1_4", 2352, 3603, 0)),
        TrackingEdge("1_5", "D", "C", false, spot("hunting_trail_clue1_5", 2358, 3603, 0), spot("hunting_trail_clue1_5", 2358, 3603, 0)),
        TrackingEdge("1_6", "S3", "C", false, spot("hunting_trail_clue1_6", 2362, 3610, 0), spot("hunting_trail_clue1_6", 2363, 3602, 0)),
        TrackingEdge("1_7", "D", "S3", true, spot("hunting_trail_clue1_7", 2358, 3607, 0), spot("hunting_trail_clue1_7", 2359, 3613, 0)),
        TrackingEdge("1_8", "E", "D", false, spot("hunting_trail_clue1_8", 2355, 3608, 0), spot("hunting_trail_clue1_8", 2355, 3608, 0)),
        TrackingEdge("1_9", "A", "E", false, spot("hunting_trail_clue1_9", 2351, 3608, 0), spot("hunting_trail_clue1_9", 2351, 3608, 0)),
        TrackingEdge("2_0", "S1", "I", true, spot("hunting_trail_clue2_0", 2363, 3617, 0), spot("hunting_trail_clue2_0", 2363, 3617, 0)),
        TrackingEdge("2_1", "G", "S1", true, spot("hunting_trail_clue2_1", 2349, 3620, 0), spot("hunting_trail_clue2_1", 2349, 3620, 0)),
        TrackingEdge("2_2", "S1", "H", false, spot("hunting_trail_clue2_2", 2356, 3620, 0), spot("hunting_trail_clue2_2", 2356, 3620, 0)),
        TrackingEdge("2_3", "F", "A", true, spot("hunting_trail_clue2_3", 2344, 3612, 0), spot("hunting_trail_clue2_3", 2347, 3607, 0)),
        TrackingEdge("2_4", "F", "E", false, spot("hunting_trail_clue2_4", 2348, 3612, 0), spot("hunting_trail_clue2_4", 2352, 3612, 0)),
        TrackingEdge("2_5", "F", "G", false, spot("hunting_trail_clue2_5", 2345, 3614, 0), spot("hunting_trail_clue2_5", 2349, 3617, 0)),
        TrackingEdge("2_6", "H", "G", true, spot("hunting_trail_clue2_6", 2356, 3618, 0), spot("hunting_trail_clue2_6", 2352, 3618, 0)),
        TrackingEdge("2_7", "S3", "I", false, spot("hunting_trail_clue2_7", 2362, 3614, 0), spot("hunting_trail_clue2_7", 2362, 3614, 0)),
        TrackingEdge("2_8", "H", "I", true, spot("hunting_trail_clue2_8", 2360, 3618, 0), spot("hunting_trail_clue2_8", 2360, 3618, 0)),
    ))

    val common = TrackingArea(TrackingPrey.COMMON, listOf(
        TrackingNode("S1", CoordGrid(2331, 3562, 0), spot("hunting_trail_spawn4", 2331, 3562, 0), null),
        TrackingNode("S2", CoordGrid(2322, 3576, 0), spot("hunting_trail_spawn5", 2322, 3576, 0), null),
        TrackingNode("S3", CoordGrid(2341, 3577, 0), spot("hunting_trail_spawn6", 2341, 3577, 0), null),
        TrackingNode("A", CoordGrid(2323, 3563, 0), null, spot("hunting_trail_end_bush35_55", 2323, 3563, 0)),
        TrackingNode("B", CoordGrid(2332, 3568, 0), null, spot("hunting_trail_end_bush35_55", 2332, 3568, 0)),
        TrackingNode("C", CoordGrid(2337, 3565, 0), null, spot("hunting_trail_end_bush35_55", 2337, 3565, 0)),
        TrackingNode("D", CoordGrid(2343, 3568, 0), null, spot("hunting_trail_end_bush35_55", 2343, 3568, 0)),
        TrackingNode("E", CoordGrid(2322, 3570, 0), null, spot("hunting_trail_end_bush35_55", 2322, 3570, 0)),
        TrackingNode("F", CoordGrid(2327, 3573, 0), null, spot("hunting_trail_end_bush35_55", 2327, 3573, 0)),
        TrackingNode("G", CoordGrid(2336, 3571, 0), null, spot("hunting_trail_end_bush35_55", 2336, 3571, 0)),
        TrackingNode("H", CoordGrid(2332, 3578, 0), null, spot("hunting_trail_end_bush35_55", 2332, 3578, 0)),
    ), listOf(
        TrackingEdge("3_0", "A", "S1", true, spot("hunting_trail_clue3_0", 2323, 3562, 0), spot("hunting_trail_clue3_0", 2330, 3562, 0)),
        TrackingEdge("3_1", "S1", "B", false, spot("hunting_trail_clue3_1", 2333, 3565, 0), spot("hunting_trail_clue3_1", 2333, 3565, 0)),
        TrackingEdge("3_2", "C", "S1", true, spot("hunting_trail_clue3_2", 2335, 3561, 0), spot("hunting_trail_clue3_2", 2335, 3561, 0)),
        TrackingEdge("3_3", "C", "D", true, spot("hunting_trail_clue3_3", 2338, 3563, 0), spot("hunting_trail_clue3_3", 2346, 3566, 0)),
        TrackingEdge("3_4", "E", "A", true, spot("hunting_trail_clue3_4", 2321, 3565, 0), spot("hunting_trail_clue3_4", 2321, 3565, 0)),
        TrackingEdge("3_5", "A", "B", true, spot("hunting_trail_clue3_5", 2325, 3565, 0), spot("hunting_trail_clue3_5", 2330, 3568, 0)),
        TrackingEdge("3_6", "C", "G", false, spot("hunting_trail_clue3_6", 2335, 3564, 0), spot("hunting_trail_clue3_6", 2337, 3573, 0)),
        TrackingEdge("3_7", "G", "D", true, spot("hunting_trail_clue3_7", 2338, 3570, 0), spot("hunting_trail_clue3_7", 2343, 3566, 0)),
        TrackingEdge("3_8", "S3", "D", false, spot("hunting_trail_clue3_8", 2341, 3579, 0), spot("hunting_trail_clue3_8", 2345, 3567, 0)),
        TrackingEdge("3_9", "E", "S2", true, spot("hunting_trail_clue3_9", 2320, 3573, 0), spot("hunting_trail_clue3_9", 2320, 3573, 0)),
        TrackingEdge("4_0", "E", "F", false, spot("hunting_trail_clue4_0", 2324, 3571, 0), spot("hunting_trail_clue4_0", 2324, 3571, 0)),
        TrackingEdge("4_1", "F", "B", false, spot("hunting_trail_clue4_1", 2331, 3572, 0), spot("hunting_trail_clue4_1", 2331, 3572, 0)),
        TrackingEdge("4_2", "S2", "F", false, spot("hunting_trail_clue4_2", 2326, 3575, 0), spot("hunting_trail_clue4_2", 2326, 3575, 0)),
        TrackingEdge("4_3", "S2", "H", false, spot("hunting_trail_clue4_3", 2323, 3577, 0), spot("hunting_trail_clue4_3", 2331, 3576, 0)),
        TrackingEdge("4_4", "H", "G", false, spot("hunting_trail_clue4_4", 2335, 3574, 0), spot("hunting_trail_clue4_4", 2335, 3574, 0)),
        TrackingEdge("4_5", "G", "S3", true, spot("hunting_trail_clue4_5", 2340, 3574, 0), spot("hunting_trail_clue4_5", 2340, 3574, 0)),
        TrackingEdge("4_6", "S3", "H", true, spot("hunting_trail_clue4_6", 2336, 3579, 0), spot("hunting_trail_clue4_6", 2336, 3579, 0)),
    ))

    val desert = TrackingArea(TrackingPrey.DESERT, listOf(
        TrackingNode("S1", CoordGrid(3396, 3106, 0), spot("hunting_trail_spawn_desert1", 3396, 3106, 0), null),
        TrackingNode("S2", CoordGrid(3402, 3131, 0), spot("hunting_trail_spawn_desert2", 3402, 3131, 0), null),
        TrackingNode("A", CoordGrid(3393, 3122, 0), null, spot("hunting_trail_end_desert", 3393, 3122, 0)),
        TrackingNode("B", CoordGrid(3398, 3121, 0), null, null),
        TrackingNode("C", CoordGrid(3405, 3124, 0), null, spot("hunting_trail_end_desert", 3405, 3124, 0)),
        TrackingNode("D", CoordGrid(3407, 3121, 0), null, spot("hunting_trail_end_desert", 3407, 3121, 0)),
        TrackingNode("E", CoordGrid(3414, 3121, 0), null, spot("hunting_trail_end_desert", 3414, 3121, 0)),
        TrackingNode("F", CoordGrid(3400, 3114, 0), null, spot("hunting_trail_end_desert", 3400, 3114, 0)),
        TrackingNode("G", CoordGrid(3407, 3113, 0), null, null),
        TrackingNode("H", CoordGrid(3411, 3108, 0), null, spot("hunting_trail_end_desert", 3411, 3108, 0)),
    ), listOf(
        TrackingEdge("4_7", "A", "S2", false, spot("hunting_trail_clue4_7", 3393, 3129, 0), spot("hunting_trail_clue4_7", 3393, 3129, 0)),
        TrackingEdge("4_8", "S2", "C", true, spot("hunting_trail_clue4_8", 3403, 3126, 0), spot("hunting_trail_clue4_8", 3403, 3126, 0)),
        TrackingEdge("5_0", "S2", "E", false, spot("hunting_trail_clue5_0", 3408, 3128, 0), spot("hunting_trail_clue5_0", 3408, 3128, 0)),
        TrackingEdge("5_1", "A", "B", true, spot("hunting_trail_clue5_1", 3396, 3121, 0), spot("hunting_trail_clue5_1", 3396, 3121, 0)),
        TrackingEdge("5_2", "B", "C", true, spot("hunting_trail_clue5_2", 3401, 3123, 0), spot("hunting_trail_clue5_2", 3401, 3123, 0)),
        TrackingEdge("5_3", "C", "D", true, spot("hunting_trail_clue5_3", 3405, 3122, 0), spot("hunting_trail_clue5_3", 3405, 3122, 0)),
        TrackingEdge("5_4", "D", "E", false, spot("hunting_trail_clue5_4", 3409, 3121, 0), spot("hunting_trail_clue5_4", 3409, 3121, 0)),
        TrackingEdge("5_5", "A", "S1", true, spot("hunting_trail_clue5_5", 3392, 3112, 0), spot("hunting_trail_clue5_5", 3392, 3112, 0)),
        TrackingEdge("5_6", "B", "F", false, spot("hunting_trail_clue5_6", 3402, 3116, 0), spot("hunting_trail_clue5_6", 3402, 3116, 0)),
        TrackingEdge("5_7", "G", "D", false, spot("hunting_trail_clue5_7", 3406, 3118, 0), spot("hunting_trail_clue5_7", 3406, 3118, 0)),
        TrackingEdge("5_8", "H", "E", true, spot("hunting_trail_clue5_8", 3411, 3114, 0), spot("hunting_trail_clue5_8", 3411, 3114, 0)),
        TrackingEdge("5_9", "S1", "F", true, spot("hunting_trail_clue5_9", 3399, 3111, 0), spot("hunting_trail_clue5_9", 3399, 3111, 0)),
        TrackingEdge("6_0", "F", "G", false, spot("hunting_trail_clue6_0", 3403, 3110, 0), spot("hunting_trail_clue6_0", 3403, 3110, 0)),
        TrackingEdge("6_1", "G", "H", true, spot("hunting_trail_clue6_1", 3406, 3111, 0), spot("hunting_trail_clue6_1", 3406, 3111, 0)),
        TrackingEdge("6_2", "H", "S1", false, spot("hunting_trail_clue6_2", 3402, 3101, 0), spot("hunting_trail_clue6_2", 3402, 3101, 0)),
    ))

    val weasel = TrackingArea(TrackingPrey.WEASEL, listOf(
        TrackingNode("S1", CoordGrid(2554, 2882, 0), spot("hunting_trail_spawn_jungle1", 2554, 2882, 0), null),
        TrackingNode("S2", CoordGrid(2525, 2889, 0), spot("hunting_trail_spawn_jungle2", 2525, 2889, 0), null),
        TrackingNode("A", CoordGrid(2525, 2882, 0), null, spot("hunting_trail_end_jungle", 2525, 2882, 0)),
        TrackingNode("B", CoordGrid(2531, 2890, 0), null, spot("hunting_trail_end_jungle", 2531, 2890, 0)),
        TrackingNode("C", CoordGrid(2533, 2885, 0), null, spot("hunting_trail_end_jungle", 2533, 2885, 0)),
        TrackingNode("D", CoordGrid(2533, 2880, 0), null, null),
        TrackingNode("E", CoordGrid(2540, 2886, 0), null, spot("hunting_trail_end_jungle", 2540, 2886, 0)),
        TrackingNode("F", CoordGrid(2541, 2890, 0), null, null),
        TrackingNode("G", CoordGrid(2542, 2881, 0), null, spot("hunting_trail_end_jungle", 2542, 2881, 0)),
        TrackingNode("H", CoordGrid(2553, 2888, 0), null, spot("hunting_trail_end_jungle", 2553, 2888, 0)),
    ), listOf(
        TrackingEdge("6_3", "A", "S2", false, spot("hunting_trail_clue6_3", 2522, 2881, 0), spot("hunting_trail_clue6_3", 2524, 2891, 0)),
        TrackingEdge("6_4", "A", "S2", true, spot("hunting_trail_clue6_4", 2524, 2886, 0), spot("hunting_trail_clue6_4", 2524, 2886, 0)),
        TrackingEdge("6_5", "S2", "B", false, spot("hunting_trail_clue6_5", 2527, 2890, 0), spot("hunting_trail_clue6_5", 2527, 2890, 0)),
        TrackingEdge("6_6", "B", "C", false, spot("hunting_trail_clue6_6", 2530, 2887, 0), spot("hunting_trail_clue6_6", 2530, 2887, 0)),
        TrackingEdge("6_7", "A", "D", true, spot("hunting_trail_clue6_7", 2526, 2882, 0), spot("hunting_trail_clue6_7", 2531, 2881, 0)),
        TrackingEdge("6_8", "C", "D", true, spot("hunting_trail_clue6_8", 2533, 2882, 0), spot("hunting_trail_clue6_8", 2533, 2882, 0)),
        TrackingEdge("7_0", "F", "B", true, spot("hunting_trail_clue7_0", 2539, 2890, 0), spot("hunting_trail_clue7_0", 2533, 2889, 0)),
        TrackingEdge("7_1", "C", "E", false, spot("hunting_trail_clue7_1", 2534, 2886, 0), spot("hunting_trail_clue7_1", 2540, 2884, 0)),
        TrackingEdge("7_2", "D", "G", true, spot("hunting_trail_clue7_2", 2537, 2880, 0), spot("hunting_trail_clue7_2", 2537, 2880, 0)),
        TrackingEdge("7_3", "E", "G", true, spot("hunting_trail_clue7_3", 2541, 2883, 0), spot("hunting_trail_clue7_3", 2541, 2883, 0)),
        TrackingEdge("7_4", "E", "F", false, spot("hunting_trail_clue7_4", 2540, 2887, 0), spot("hunting_trail_clue7_4", 2540, 2887, 0)),
        TrackingEdge("7_5", "F", "H", false, spot("hunting_trail_clue7_5", 2543, 2890, 0), spot("hunting_trail_clue7_5", 2551, 2888, 0)),
        TrackingEdge("7_6", "G", "S1", true, spot("hunting_trail_clue7_6", 2544, 2881, 0), spot("hunting_trail_clue7_6", 2551, 2881, 0)),
        TrackingEdge("7_7", "H", "S1", false, spot("hunting_trail_clue7_7", 2552, 2885, 0), spot("hunting_trail_clue7_7", 2552, 2885, 0)),
        TrackingEdge("7_8", "H", "S1", false, spot("hunting_trail_clue7_8", 2554, 2888, 0), spot("hunting_trail_clue7_8", 2555, 2881, 0)),
    ))

    val polar = TrackingArea(TrackingPrey.POLAR, listOf(
        TrackingNode("A", CoordGrid(2708, 3819, 1), null, spot("hunting_trail_end_polar", 2708, 3819, 1)),
        TrackingNode("B", CoordGrid(2718, 3820, 1), spot("hunting_trail_spawn_polar2", 2717, 3819, 1), spot("hunting_trail_end_polar", 2718, 3820, 1)),
        TrackingNode("C", CoordGrid(2716, 3827, 1), null, spot("hunting_trail_end_polar", 2716, 3827, 1)),
        TrackingNode("D", CoordGrid(2712, 3831, 1), spot("hunting_trail_spawn_polar1", 2711, 3830, 1), spot("hunting_trail_end_polar", 2712, 3831, 1)),
        TrackingNode("U", CoordGrid(2715, 3824, 1), null, null),
    ), listOf(
        TrackingEdge("8_0", "A", "B", true, spot("hunting_trail_clue8_0", 2712, 3815, 1), spot("hunting_trail_clue8_0", 2712, 3815, 1)),
        TrackingEdge("8_1", "A", "U", true, spot("hunting_trail_clue8_1", 2711, 3819, 1), spot("hunting_trail_clue8_1", 2711, 3819, 1)),
        TrackingEdge("8_2", "U", "B", false, spot("hunting_trail_clue8_2", 2715, 3820, 1), spot("hunting_trail_clue8_2", 2715, 3820, 1)),
        TrackingEdge("8_3", "B", "U", true, spot("hunting_trail_clue8_3", 2721, 3827, 1), spot("hunting_trail_clue8_3", 2721, 3827, 1)),
        TrackingEdge("8_4", "A", "D", false, spot("hunting_trail_clue8_4", 2708, 3825, 1), spot("hunting_trail_clue8_4", 2708, 3825, 1)),
        TrackingEdge("8_5", "U", "C", false, spot("hunting_trail_clue8_5", 2714, 3821, 1), spot("hunting_trail_clue8_5", 2714, 3821, 1)),
        TrackingEdge("8_6", "C", "U", true, spot("hunting_trail_clue8_6", 2718, 3829, 1), spot("hunting_trail_clue8_6", 2718, 3829, 1)),
        TrackingEdge("8_7", "D", "C", false, spot("hunting_trail_clue8_7", 2713, 3827, 1), spot("hunting_trail_clue8_7", 2713, 3827, 1)),
        TrackingEdge("8_8", "D", "U", true, spot("hunting_trail_clue8_8", 2718, 3832, 1), spot("hunting_trail_clue8_8", 2718, 3832, 1)),
    ))

    val herbiboar = TrackingArea(TrackingPrey.HERBIBOAR, listOf(
        TrackingNode("A", CoordGrid(3670, 3889), null, null, 0),
        TrackingNode("B", CoordGrid(3728, 3893), null, null, 0),
        TrackingNode("C", CoordGrid(3699, 3875), null, spot("hunting_trail_end_fossil", 3700, 3877), 5),
        TrackingNode("D", CoordGrid(3708, 3876), null, spot("hunting_trail_end_fossil", 3710, 3881), 4),
        TrackingNode("E", CoordGrid(3668, 3865), null, null, 0),
        TrackingNode("F", CoordGrid(3681, 3860), null, spot("hunting_trail_end_fossil", 3681, 3863), 9),
        TrackingNode("G", CoordGrid(3694, 3847), null, null, 0),
        TrackingNode("H", CoordGrid(3715, 3851), null, null, 0),
        TrackingNode("I", CoordGrid(3680, 3838), null, null, 0),
        TrackingNode("J", CoordGrid(3713, 3840), null, spot("hunting_trail_end_fossil", 3715, 3840), 6),
        TrackingNode("K", CoordGrid(3706, 3811), spot("hunting_trail_spawn_fossil3", 3704, 3810, 0), spot("hunting_trail_end_fossil", 3702, 3808), 2),
        TrackingNode("S", CoordGrid(3686, 3870), spot("hunting_trail_spawn_fossil1", 3686, 3870, 0), spot("hunting_trail_end_fossil", 3685, 3869), 8),
        TrackingNode("L", CoordGrid(3695, 3800), spot("hunting_trail_spawn_fossil2", 3695, 3800, 0), spot("hunting_trail_end_fossil", 3693, 3798), 1),
        TrackingNode("M", CoordGrid(3705, 3830), spot("hunting_trail_spawn_fossil4", 3705, 3830, 0), spot("hunting_trail_end_fossil", 3703, 3826), 3),
        TrackingNode("N", CoordGrid(3751, 3850), spot("hunting_trail_spawn_fossil5", 3751, 3850, 0), spot("hunting_trail_end_fossil", 3751, 3849), 7),
    ), listOf(
        TrackingEdge("9_0", "S", "C", true, null, spot("hunting_trail_clue10_4", 3697, 3875, 0)),
        TrackingEdge("9_1", "S", "A", true, null, spot("hunting_trail_clue10_5", 3672, 3890, 0)),
        TrackingEdge("9_2", "S", "F", true, null, spot("hunting_trail_clue10_1", 3681, 3859, 0)),
        TrackingEdge("9_3", "C", "D", true, spot("hunting_trail_clue9_0", 3699, 3875, 0), spot("hunting_trail_clue11_0", 3710, 3877, 0)),
        TrackingEdge("9_4", "C", "B", true, spot("hunting_trail_clue9_0", 3699, 3875, 0), spot("hunting_trail_clue9_4", 3728, 3893, 0)),
        TrackingEdge("9_5", "A", "B", true, spot("hunting_trail_clue9_1", 3670, 3889, 0), spot("hunting_trail_clue9_4", 3728, 3893, 0)),
        TrackingEdge("9_6", "A", "E", true, spot("hunting_trail_clue9_1", 3670, 3889, 0), spot("hunting_trail_clue10_8", 3667, 3862, 0)),
        TrackingEdge("9_7", "F", "I", true, spot("hunting_trail_clue9_2", 3681, 3860, 0), spot("hunting_trail_clue10_6", 3680, 3836, 0)),
        TrackingEdge("9_8", "F", "G", true, spot("hunting_trail_clue9_2", 3681, 3860, 0), spot("hunting_trail_clue10_0", 3698, 3847, 0)),
        TrackingEdge("9_9", "D", "H", true, spot("hunting_trail_clue9_3", 3708, 3876, 0), spot("hunting_trail_clue10_9", 3713, 3850, 0)),
        TrackingEdge("10_0", "D", "G", true, spot("hunting_trail_clue9_3", 3708, 3876, 0), spot("hunting_trail_clue9_8", 3694, 3847, 0)),
        TrackingEdge("10_1", "B", "C", true, spot("hunting_trail_clue9_4", 3728, 3893, 0), null),
        TrackingEdge("10_2", "B", "D", true, spot("hunting_trail_clue9_4", 3728, 3893, 0), spot("hunting_trail_clue11_0", 3710, 3877, 0)),
        TrackingEdge("10_3", "E", "F", true, spot("hunting_trail_clue9_6", 3668, 3865, 0), spot("hunting_trail_clue9_2", 3681, 3860, 0)),
        TrackingEdge("10_4", "E", "I", true, spot("hunting_trail_clue9_6", 3668, 3865, 0), spot("hunting_trail_clue10_6", 3680, 3836, 0)),
        TrackingEdge("10_5", "I", "K", true, spot("hunting_trail_clue9_7", 3680, 3838, 0), spot("hunting_trail_clue10_2", 3706, 3811, 0)),
        TrackingEdge("10_6", "I", "L", true, spot("hunting_trail_clue9_7", 3680, 3838, 0), null),
        TrackingEdge("10_7", "G", "M", true, spot("hunting_trail_clue9_8", 3694, 3847, 0), null),
        TrackingEdge("10_8", "H", "J", true, spot("hunting_trail_clue9_9", 3715, 3851, 0), spot("hunting_trail_clue10_3", 3713, 3840, 0)),
        TrackingEdge("10_9", "H", "N", true, spot("hunting_trail_clue9_9", 3715, 3851, 0), null),
        TrackingEdge("11_0", "H", "G", true, spot("hunting_trail_clue10_9", 3713, 3850, 0), null),
        TrackingEdge("11_1", "K", "L", true, spot("hunting_trail_clue10_2", 3706, 3811, 0), null),
        TrackingEdge("11_2", "K", "J", true, spot("hunting_trail_clue10_2", 3706, 3811, 0), null),
        TrackingEdge("11_3", "J", "D", true, spot("hunting_trail_clue10_3", 3713, 3840, 0), null),
    ))
    val all = listOf(polar, common, weasel, desert, razor, herbiboar)
}
