package org.rsmod.content.skills.hunter.configs

object BirdhouseParams {

    const val NOT_BUILT_LOC: String = "loc.birdhouse_not_built"

    const val CLOCKWORK: String = "obj.poh_clockwork_mechanism"

    const val RAW_BIRD_MEAT: String = "obj.spit_raw_bird_meat"

    const val RAW_BIRD_MEAT_COUNT: Int = 10

    const val FEATHER: String = "obj.feather"

    // Feather outcomes are established; equal weights are provisional.
    fun featherCount(random: org.rsmod.api.random.GameRandom): Int = 30 + 10 * random.of(4)

    const val STRUNG_RABBIT_FOOT: String = "obj.hunting_strung_rabbit_foot"

    const val SEED_NEST: String = "obj.bird_nest_seeds_jan2019"

    const val RING_NEST: String = "obj.bird_nest_ring"
    const val EMPTY_NEST: String = "obj.bird_nest_empty"

    val EGG_NESTS: List<String> =
        listOf("obj.bird_nest_egg_red", "obj.bird_nest_egg_green", "obj.bird_nest_egg_blue")

    const val SEED_NEST_ROLL_LOW: Int = 0

    const val SEED_NEST_ROLL_HIGH: Int = 200

    const val NEST_TABLE_ROLLS: Int = 5

    fun publishedNestChance(tier: BirdhouseTier, hunterLevel: Int): Double {
        // Double the base to preserve half-thousandths during interpolation.
        val doubledBase = when (tier) {
            BirdhouseTier.NORMAL -> 300
            BirdhouseTier.OAK -> 375
            BirdhouseTier.WILLOW -> 384
            BirdhouseTier.TEAK -> 390
            BirdhouseTier.MAPLE -> 420
            BirdhouseTier.MAHOGANY -> 450
            BirdhouseTier.YEW -> 480
            BirdhouseTier.MAGIC -> 510
            BirdhouseTier.REDWOOD -> 525
        }
        // Boosts above 99 still increase nests; do not cap this level.
        val scaledLevels = (hunterLevel - 50).coerceAtLeast(0)
        val thousandths = doubledBase * (49 + scaledLevels) / 98
        return (thousandths / 1000.0).coerceAtMost(1.0)
    }

    const val NEST_TABLE_RING_WEIGHT: Int = 32

    const val NEST_TABLE_EGG_WEIGHT_EACH: Int = 1

    const val NEST_TABLE_DENOMINATOR_BASE: Int = 100

    const val NEST_TABLE_DENOMINATOR_WITH_FOOT: Int = 95

    const val BUILD_QUEUE: String = "queue.hunter_birdhouse_build"
    const val SEED_QUEUE: String = "queue.hunter_birdhouse_seed"

    const val DISMANTLE_QUEUE: String = "queue.hunter_birdhouse_dismantle"

    const val BUILD_ANIM: String = "seq.hunting_setting_trap_small"

    const val SEED_ANIM: String = "seq.hunting_baiting_small_traps"

    const val DISMANTLE_ANIM: String = "seq.human_hunting_dismantle_net"

    const val BUILD_TICKS: Int = 3

    const val SEED_TICKS: Int = 3

    const val DISMANTLE_TICKS: Int = 3

    const val RESET_TICKS: Int = DISMANTLE_TICKS + 1

    const val FILL_MINUTES: Int = 50

    const val MILLIS_PER_MINUTE: Long = 60_000L
}
