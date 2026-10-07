package org.rsmod.content.skills.woodcutting.configs

import org.rsmod.api.random.GameRandom

// Ring and seed contents roll when searched; only egg color is fixed when the nest drops.
object BirdNests {
    const val CHANCE: Int = 256

    const val TYPE_TABLE_SIZE: Int = 100
    const val RABBIT_FOOT_TABLE_SIZE: Int = 95

    private const val EGG_SLOTS = 3
    private const val RING_SLOTS = 32

    const val RING: String = "obj.bird_nest_ring"

    const val SEEDS: String = "obj.bird_nest_seeds_jan2019"

    const val EMPTY: String = "obj.bird_nest_empty"

    val EGGS: List<String> =
        listOf("obj.bird_nest_egg_red", "obj.bird_nest_egg_blue", "obj.bird_nest_egg_green")

    val EGG_CONTENTS: Map<String, String> =
        mapOf(
            "obj.bird_nest_egg_red" to "obj.bird_egg_red",
            "obj.bird_nest_egg_green" to "obj.bird_egg_green",
            "obj.bird_nest_egg_blue" to "obj.bird_egg_blue",
        )

    val RING_TABLE: List<Pair<String, Int>> =
        listOf(
            "obj.gold_ring" to 35,
            "obj.sapphire_ring" to 40,
            "obj.emerald_ring" to 15,
            "obj.ruby_ring" to 9,
            "obj.diamond_ring" to 1,
        )

    val SEED_TABLE: List<Pair<String, Int>> =
        listOf(
            "obj.acorn" to 214,
            "obj.apple_tree_seed" to 170,
            "obj.willow_seed" to 135,
            "obj.banana_tree_seed" to 108,
            "obj.orange_tree_seed" to 85,
            "obj.curry_tree_seed" to 68,
            "obj.maple_seed" to 54,
            "obj.pineapple_tree_seed" to 42,
            "obj.papaya_tree_seed" to 34,
            "obj.yew_seed" to 27,
            "obj.palm_tree_seed" to 22,
            "obj.calquat_tree_seed" to 17,
            "obj.spirit_tree_seed" to 11,
            "obj.dragonfruit_tree_seed" to 6,
            "obj.magic_tree_seed" to 5,
            "obj.teak_seed" to 4,
            "obj.mahogany_seed" to 4,
            "obj.celastrus_tree_seed" to 3,
            "obj.redwood_tree_seed" to 2,
        )

    val SEARCHABLE: List<String> = EGGS + RING + SEEDS

    const val MESSAGE: String = "A bird's nest falls out of the tree."

    /** Redwood and blisterwood only award clue nests, which use a separate roll. */
    fun canDropFrom(log: String): Boolean =
        log != "obj.redwood_logs" && log != "obj.blisterwood_logs"

    fun roll(random: GameRandom, capeBoost: Boolean, rabbitFoot: Boolean): String? {
        val successfulSlots = if (capeBoost) 11 else 10
        if (random.of(1, CHANCE * 10) > successfulSlots) return null
        val tableSize = if (rabbitFoot) RABBIT_FOOT_TABLE_SIZE else TYPE_TABLE_SIZE
        return nestType(random.of(1, tableSize))
    }

    fun nestType(roll: Int): String =
        when {
            roll <= EGG_SLOTS -> EGGS[roll - 1]
            roll <= EGG_SLOTS + RING_SLOTS -> RING
            else -> SEEDS
        }

    fun rollContents(nest: String, random: GameRandom): String? =
        when (nest) {
            in EGG_CONTENTS -> EGG_CONTENTS.getValue(nest)
            RING -> rollWeighted(RING_TABLE, random)
            SEEDS -> rollWeighted(SEED_TABLE, random)
            else -> null
        }

    private fun rollWeighted(table: List<Pair<String, Int>>, random: GameRandom): String {
        var roll = random.of(1, table.sumOf { it.second })
        for ((item, weight) in table) {
            roll -= weight
            if (roll <= 0) {
                return item
            }
        }
        return table.last().first
    }
}
