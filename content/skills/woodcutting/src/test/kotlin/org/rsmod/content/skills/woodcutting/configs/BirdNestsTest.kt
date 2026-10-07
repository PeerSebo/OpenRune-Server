package org.rsmod.content.skills.woodcutting.configs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.random.GameRandom

class BirdNestsTest {
    @Test
    fun `cape increases the nest chance by exactly ten percent`() {
        for ((cape, expected) in listOf(false to 10, true to 11)) {
            val successes = (1..2560).count { roll ->
                BirdNests.roll(FixedRandom(roll, 100), capeBoost = cape, rabbitFoot = false) != null
            }
            assertEquals(expected, successes)
        }
    }

    @Test
    fun `ordinary table contains one of each egg 32 rings and 65 seeds`() {
        val contents = (1..100).map { roll ->
            BirdNests.roll(FixedRandom(1, roll), capeBoost = false, rabbitFoot = false)
        }.groupingBy { it }.eachCount()
        assertEquals(1, contents["obj.bird_nest_egg_red"])
        assertEquals(1, contents["obj.bird_nest_egg_blue"])
        assertEquals(1, contents["obj.bird_nest_egg_green"])
        assertEquals(32, contents[BirdNests.RING])
        assertEquals(65, contents[BirdNests.SEEDS])
    }

    @Test
    fun `rabbit foot removes only five seed slots and stacks with cape`() {
        val contents = (1..95).map { roll ->
            BirdNests.roll(FixedRandom(11, roll), capeBoost = true, rabbitFoot = true)
        }.groupingBy { it }.eachCount()
        for (egg in BirdNests.EGGS) assertEquals(1, contents[egg])
        assertEquals(32, contents[BirdNests.RING])
        assertEquals(60, contents[BirdNests.SEEDS])
    }

    @Test
    fun `failed nest roll does not consume a type roll`() {
        val random = FixedRandom(12)
        assertEquals(null, BirdNests.roll(random, capeBoost = true, rabbitFoot = true))
        assertEquals(1, random.calls)
    }

    @Test
    fun `redwood and blisterwood cannot drop ordinary nests`() {
        assertFalse(BirdNests.canDropFrom("obj.redwood_logs"))
        assertFalse(BirdNests.canDropFrom("obj.blisterwood_logs"))
        for (log in listOf("logs", "oak_logs", "willow_logs", "maple_logs", "yew_logs", "magic_logs")) {
            assertTrue(BirdNests.canDropFrom("obj.$log"))
        }
    }

    @Test
    fun `weighted seed and ring searches preserve every reward weight`() {
        for ((nest, table) in listOf(BirdNests.SEEDS to BirdNests.SEED_TABLE, BirdNests.RING to BirdNests.RING_TABLE)) {
            val contents = (1..table.sumOf { it.second }).map { roll ->
                BirdNests.rollContents(nest, FixedRandom(roll))
            }.groupingBy { it }.eachCount()
            assertEquals(table.toMap(), contents)
        }
        assertEquals(1011, BirdNests.SEED_TABLE.sumOf { it.second })
    }

    @Test
    fun `egg searches keep their visible colour without rerolling`() {
        for ((nest, egg) in BirdNests.EGG_CONTENTS) {
            val random = FixedRandom()
            assertEquals(egg, BirdNests.rollContents(nest, random))
            assertEquals(0, random.calls)
        }
    }

    private class FixedRandom(vararg values: Int) : GameRandom {
        private val values = values.toList()
        var calls = 0
            private set

        override fun of(minInclusive: Int, maxInclusive: Int): Int {
            val next = values[calls++]
            require(next in minInclusive..maxInclusive)
            return next
        }

        override fun of(maxExclusive: Int): Int = of(0, maxExclusive - 1)

        override fun randomDouble(): Double = error("Unexpected randomDouble")
    }
}
