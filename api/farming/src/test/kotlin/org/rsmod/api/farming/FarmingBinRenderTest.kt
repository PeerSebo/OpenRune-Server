package org.rsmod.api.farming

import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

/**
 * Fixtures: rev-240 ObjectDefinitions.json, configChangeDest of locs 7836, 7838 and 34631.
 * Small bins have 224 entries; the big bin has 239 with a split fill band that breaks `base + count`.
 * FarmingBinRegistry.validate checks the live cache separately.
 */
class FarmingBinRenderTest : FarmingCacheTest() {
    @Test
    fun `an untouched bin renders the family's empty loc at index zero`() {
        for ((type, fixture) in FIXTURES) {
            assertEquals(0, value(type, fixture, FarmingBinState()), "empty bin in $type")
        }
    }

    @Test
    fun `a filling bin renders its running classification, not the last item added`() {
        val state = filling(FarmingBinFill.SUPERCOMPOSTABLE, count = 1)
        assertEquals(33, value(FarmingBinType.STANDARD, STANDARD, state))

        // A mixed load must render ordinary compost regardless of the first ingredient.
        state.fill = state.fill.merge(FarmingBinFill.COMPOSTABLE)
        state.count = 1
        assertEquals(1, value(FarmingBinType.STANDARD, STANDARD, state))
    }

    @Test
    fun `the big bin's fill band is split and only a named lookup survives it`() {
        // Big-bin fill counts 16-30 jump to indices 63-77, beyond the compost band.
        val fifteen = filling(FarmingBinFill.COMPOSTABLE, count = 15)
        val sixteen = filling(FarmingBinFill.COMPOSTABLE, count = 16)
        val thirty = filling(FarmingBinFill.COMPOSTABLE, count = 30)

        assertEquals(15, value(FarmingBinType.FARMING_GUILD, BIG, fifteen))
        assertEquals(63, value(FarmingBinType.FARMING_GUILD, BIG, sixteen))
        assertEquals(77, value(FarmingBinType.FARMING_GUILD, BIG, thirty))
        assertNotEquals(16, value(FarmingBinType.FARMING_GUILD, BIG, sixteen))
    }

    @Test
    fun `the big bin's product band is split the same way`() {
        assertEquals(16, value(FarmingBinType.FARMING_GUILD, BIG, open(COMPOST, count = 1)))
        assertEquals(78, value(FarmingBinType.FARMING_GUILD, BIG, open(COMPOST, count = 16)))
        assertEquals(92, value(FarmingBinType.FARMING_GUILD, BIG, open(COMPOST, count = 30)))
    }

    @Test
    fun `rotting and ready are distinct states in every family`() {
        assertEquals(31, value(FarmingBinType.STANDARD, STANDARD, rotting(COMPOST)))
        assertEquals(80, value(FarmingBinType.STANDARD, STANDARD, ready(COMPOST)))
        assertEquals(95, value(FarmingBinType.STANDARD, STANDARD, rotting(SUPERCOMPOST)))
        assertEquals(112, value(FarmingBinType.STANDARD, STANDARD, ready(SUPERCOMPOST)))
        assertEquals(159, value(FarmingBinType.STANDARD, STANDARD, rotting(TOMATOES)))
        assertEquals(208, value(FarmingBinType.STANDARD, STANDARD, ready(TOMATOES)))

        // The big bin's ready states are nowhere near the small bin's: 93 and 206, not 80 and 208.
        assertEquals(93, value(FarmingBinType.FARMING_GUILD, BIG, ready(COMPOST)))
        assertEquals(206, value(FarmingBinType.FARMING_GUILD, BIG, ready(TOMATOES)))
    }

    @Test
    fun `the Morytania re-skin shares the layout but not one loc id`() {
        assertEquals(0, value(FarmingBinType.MORYTANIA, MORYTANIA, FarmingBinState()))
        assertEquals(
            1,
            value(FarmingBinType.MORYTANIA, MORYTANIA, filling(FarmingBinFill.COMPOSTABLE, 1)),
        )
        assertEquals(31, value(FarmingBinType.MORYTANIA, MORYTANIA, rotting(COMPOST)))
        assertEquals(176, value(FarmingBinType.MORYTANIA, MORYTANIA, open(ULTRACOMPOST, 1)))

        // Declaring the wrong family resolves real locs that are simply not in this bin's table,
        assertNull(value(FarmingBinType.STANDARD, MORYTANIA, rotting(COMPOST)))
    }

    @Test
    fun `ultracompost has no rot states, so a bin claiming to rot it cannot be drawn`() {
        // Ultracompost exists only as a counted open-bin band.
        assertNull(value(FarmingBinType.STANDARD, STANDARD, rotting(ULTRACOMPOST)))
        assertNull(value(FarmingBinType.STANDARD, STANDARD, ready(ULTRACOMPOST)))
        assertEquals(176, value(FarmingBinType.STANDARD, STANDARD, open(ULTRACOMPOST, count = 1)))
    }

    @Test
    fun `a count past the family's capacity is not drawn as some other band`() {
        // An invalid count must not overflow the fill band into ready compost states.
        assertNull(value(FarmingBinType.STANDARD, STANDARD, filling(FarmingBinFill.COMPOSTABLE, 16)))
        assertNull(value(FarmingBinType.STANDARD, STANDARD, open(COMPOST, count = 16)))
    }

    private fun value(type: FarmingBinType, fixture: Fixture, state: FarmingBinState): Int? =
        FarmingBinRenderer.stateValue(fixture.table, type, state, fixture.ids::get)

    private fun filling(fill: FarmingBinFill, count: Int): FarmingBinState {
        val state = FarmingBinState()
        state.fill = fill
        state.count = count
        return state
    }

    private fun rotting(product: FarmingBinProduct): FarmingBinState = closed(
        FarmingBinPhase.ROTTING,
        product,
    )

    private fun ready(product: FarmingBinProduct): FarmingBinState = closed(
        FarmingBinPhase.READY,
        product,
    )

    private fun closed(phase: FarmingBinPhase, product: FarmingBinProduct): FarmingBinState {
        val state = FarmingBinState()
        state.phase = phase
        state.product = product
        state.count = 15
        state.closedMinute = 1
        return state
    }

    private fun open(product: FarmingBinProduct, count: Int): FarmingBinState {
        val state = FarmingBinState()
        state.phase = FarmingBinPhase.OPEN
        state.product = product
        state.count = count
        return state
    }

    /**
     * A transform table plus the gameval lookup that matches it, both built from the same list so a
     * fixture cannot describe a name and an index that disagree.
     */
    private class Fixture(size: Int, entries: List<Triple<Int, String, Int>>) {
        val ids: Map<String, Int> = entries.associate { (_, name, id) -> name to id }
        val table: FarmingTransformTable

        init {
            // Unaddressed slots hold the family's own filler, exactly as the real tables do.
            val emptyId = entries.first { it.first == 0 }.third
            val transforms = IntArray(size) { emptyId }
            for ((index, _, id) in entries) {
                transforms[index] = id
            }
            table = FarmingTransformTable(locId = 0, transforms = transforms)
        }
    }

    private companion object {
        private val COMPOST = FarmingBinProduct.COMPOST
        private val SUPERCOMPOST = FarmingBinProduct.SUPERCOMPOST
        private val ULTRACOMPOST = FarmingBinProduct.ULTRACOMPOST
        private val TOMATOES = FarmingBinProduct.ROTTEN_TOMATOES

        /** loc 7836: the standard 224-entry small-bin table. */
        private val STANDARD =
            Fixture(
                size = 224,
                entries =
                    listOf(
                        Triple(0, "loc.compost_bin_empty", 7808),
                        Triple(1, "loc.compost_bin_compostable_01", 3830),
                        Triple(15, "loc.compost_bin_compostable_15", 3848),
                        Triple(16, "loc.compost_bin_compost_01", 3851),
                        Triple(30, "loc.compost_bin_compost_15", 19064),
                        Triple(31, "loc.compost_bin_compost_rotting", 3849),
                        Triple(33, "loc.compost_bin_supercompostable_01", 19066),
                        Triple(48, "loc.compost_bin_supercompost_01", 19083),
                        Triple(80, "loc.compost_bin_compost_ready", 3850),
                        Triple(95, "loc.compost_bin_supercompost_rotting", 19081),
                        Triple(112, "loc.compost_bin_supercompost_ready", 19082),
                        Triple(129, "loc.compost_bin_tomatoes_01", 19098),
                        Triple(143, "loc.compost_bin_tomatoes_15", 20099),
                        Triple(144, "loc.compost_bin_rottentomatoes_01", 20102),
                        Triple(159, "loc.compost_bin_rottentomatoes_rotting", 20100),
                        Triple(176, "loc.compost_bin_ultracompost_01", 30502),
                        Triple(208, "loc.compost_bin_rottentomatoes_ready", 20101),
                    ),
            )

        /** loc 7838: the Port Phasmatys re-skin - same indices, entirely different loc ids. */
        private val MORYTANIA =
            Fixture(
                size = 224,
                entries =
                    listOf(
                        Triple(0, "loc.compost_bin_dark_empty", 20117),
                        Triple(1, "loc.compost_bin_dark_compostable_01", 20120),
                        Triple(16, "loc.compost_bin_dark_compost_01", 20892),
                        Triple(31, "loc.compost_bin_dark_compost_rotting", 20890),
                        Triple(80, "loc.compost_bin_dark_compost_ready", 20891),
                        Triple(129, "loc.compost_bin_dark_tomatoes_01", 21295),
                        Triple(176, "loc.compost_bin_dark_ultracompost_01", 31863),
                        Triple(208, "loc.compost_bin_dark_rottentomatoes_ready", 21680),
                    ),
            )

        /** loc 34631: the Farming Guild's 239-entry table, with its split 30-wide bands. */
        private val BIG =
            Fixture(
                size = 239,
                entries =
                    listOf(
                        Triple(0, "loc.compost_bigbin_empty", 33762),
                        Triple(1, "loc.compost_bigbin_compostable_01", 33763),
                        Triple(15, "loc.compost_bigbin_compostable_15", 33777),
                        Triple(63, "loc.compost_bigbin_compostable_16", 33778),
                        Triple(77, "loc.compost_bigbin_compostable_30", 33792),
                        Triple(16, "loc.compost_bigbin_compost_01", 33795),
                        Triple(78, "loc.compost_bigbin_compost_16", 33810),
                        Triple(92, "loc.compost_bigbin_compost_30", 33824),
                        Triple(31, "loc.compost_bigbin_compost_rotting", 33793),
                        Triple(93, "loc.compost_bigbin_compost_ready", 33794),
                        Triple(95, "loc.compost_bigbin_supercompost_rotting", 33855),
                        Triple(176, "loc.compost_bigbin_ultracompost_01", 33949),
                        Triple(205, "loc.compost_bigbin_ultracompost_30", 33978),
                        Triple(206, "loc.compost_bigbin_rottentomatoes_ready", 33918),
                    ),
            )

        private val FIXTURES: List<Pair<FarmingBinType, Fixture>> =
            listOf(
                FarmingBinType.STANDARD to STANDARD,
                FarmingBinType.MORYTANIA to MORYTANIA,
                FarmingBinType.FARMING_GUILD to BIG,
            )
    }
}
