package org.rsmod.api.farming

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Fixtures: ObjectDefinitions.json, configChangeDest of locs 8550, 8388, 7962 and 7577.
 * Their incompatible layouts require named anchors instead of a global band offset.
 * FarmingCropRegistry.validate checks the live cache separately.
 */
class FarmingTransformTableTest {
    @Test
    fun `allotments band watered, diseased and dead 64 apart`() {
        // loc 8550: potato normal 6, watered 70, diseased stage 1 at 135, dead stage 1 at 199.
        val table =
            table(
                6 to POTATO_SEED,
                70 to POTATO_SEED_WATERED,
                135 to POTATO_DISEASED_1,
                199 to POTATO_DEAD_1,
            )
        assertEquals(6, FarmingRenderer.anchorBase(table, POTATO_SEED, stage = 0))
        assertEquals(70, FarmingRenderer.anchorBase(table, POTATO_SEED_WATERED, stage = 0))
        assertEquals(6 + 128, FarmingRenderer.anchorBase(table, POTATO_DISEASED_1, stage = 1))
        assertEquals(6 + 192, FarmingRenderer.anchorBase(table, POTATO_DEAD_1, stage = 1))
    }

    @Test
    fun `trees have no watered band - diseased is band 1 and dead is band 2`() {
        // loc 8388: oak normal 8, diseased stage 1 at 73, dead stage 1 at 137.
        val table = table(8 to OAK_SEEDLING, 73 to OAK_DISEASED_1, 137 to OAK_DEAD_1)
        assertEquals(8, FarmingRenderer.anchorBase(table, OAK_SEEDLING, stage = 0))
        assertEquals(8 + 64, FarmingRenderer.anchorBase(table, OAK_DISEASED_1, stage = 1))
        assertEquals(8 + 128, FarmingRenderer.anchorBase(table, OAK_DEAD_1, stage = 1))
        assertNull(FarmingRenderer.anchorBase(table, ABSENT_LOC, stage = 0))
    }

    @Test
    fun `fruit trees are not band-flagged at all`() {
        // Apple growth/fruit, disease, death, stump and health-check occupy separate cache bands.
        val table =
            table(
                8 to APPLE_SEEDLING,
                21 to APPLE_DISEASED_1,
                27 to APPLE_DEAD_1,
                33 to APPLE_STUMP,
                34 to APPLE_CLAIM_XP,
            )
        assertEquals(8, FarmingRenderer.anchorBase(table, APPLE_SEEDLING, stage = 0))
        assertEquals(8 + 12, FarmingRenderer.anchorBase(table, APPLE_DISEASED_1, stage = 1))
        assertEquals(8 + 18, FarmingRenderer.anchorBase(table, APPLE_DEAD_1, stage = 1))
        assertEquals(33, FarmingRenderer.anchorBase(table, APPLE_STUMP, stage = 0))
        assertEquals(34, FarmingRenderer.anchorBase(table, APPLE_CLAIM_XP, stage = 0))
    }

    @Test
    fun `poison ivy sits in its own late block but still has diseased and dead states`() {
        val table =
            table(
                197 to POISON_IVY_SEEDLING,
                210 to POISON_IVY_DISEASED_1,
                217 to POISON_IVY_DEAD_1,
            )
        assertEquals(197, FarmingRenderer.anchorBase(table, POISON_IVY_SEEDLING, stage = 0))
        assertEquals(209, FarmingRenderer.anchorBase(table, POISON_IVY_DISEASED_1, stage = 1))
        assertEquals(216, FarmingRenderer.anchorBase(table, POISON_IVY_DEAD_1, stage = 1))
        assertNull(FarmingRenderer.anchorBase(table, ABSENT_LOC, stage = 0))
    }

    @Test
    fun `filler detection flags unused slots but not the overgrown patch`() {
        val table = table(6 to POTATO_SEED)
        assertFalse(table.isFiller(FarmingConstants.WEEDS_TRANSFORM_INDEX))
        assertFalse(table.isFiller(6))
        assertTrue(table.isFiller(7), "an unused slot points at the family's weeds loc")
        assertTrue(table.isFiller(FarmingConstants.MAX_TRANSFORM_INDEX + 1))
    }

    @Test
    fun `indexOf returns the first copy of a repeated grown state`() {
        val table = table(10 to POTATO_GROWN, 11 to POTATO_GROWN, 12 to POTATO_GROWN)
        assertEquals(10, table.indexOf(POTATO_GROWN))
    }

    @Test
    fun `a contiguous anchor derives every offset from one named loc`() {
        val table = table(6 to POTATO_SEED, 7 to POTATO_1)
        val anchor = FarmingAnchor("loc.potato_seed")
        assertEquals(6, index(table, anchor, offset = 0))
        assertEquals(7, index(table, anchor, offset = 1))
        assertEquals(10, index(table, anchor, offset = 4))
    }

    @Test
    fun `a per-stage anchor names every offset and derives nothing`() {
        // Snape grass skips intervening potato disease states; its growth band is not contiguous.
        val table =
            table(
                128 to SNAPE_SEED,
                129 to SNAPE_1,
                135 to POTATO_DISEASED_1,
                138 to SNAPE_FULLYGROWN,
            )
        val contiguous = FarmingAnchor("loc.snape_grass_seed")
        assertEquals(135, index(table, contiguous, offset = 7), "the trap this API had to remove")

        val perStage =
            FarmingAnchor.stages(
                listOf("loc.snape_grass_seed", "loc.snape_grass_1", "loc.snape_grass_fullygrown")
            )
        assertEquals(128, index(table, perStage, offset = 0))
        assertEquals(129, index(table, perStage, offset = 1))
        assertEquals(138, index(table, perStage, offset = 2))
        assertNull(index(table, perStage, offset = 3))
    }

    @Test
    fun `a per-stage anchor can start at a non-zero offset`() {
        val table = table(200 to SNAPE_1, 204 to SNAPE_FULLYGROWN)
        val anchor =
            FarmingAnchor.stages(
                listOf("loc.snape_grass_1", "loc.snape_grass_fullygrown"),
                firstStage = 1,
            )
        assertNull(index(table, anchor, offset = 0))
        assertEquals(200, index(table, anchor, offset = 1))
        assertEquals(204, index(table, anchor, offset = 2))
    }

    @Test
    fun `a tree separates its unchecked and grown-checked states`() {
        // Oak uses separate mature health-check and chop-down anchors at indices 12 and 13.
        val table =
            table(
                8 to OAK_SEEDLING,
                12 to OAK_FULLYGROWN_1,
                13 to OAK_FULLYGROWN_2,
                14 to OAK_STUMP,
            )
        assertEquals(12, index(table, FarmingAnchor("loc.oak_tree_fullygrown_1"), offset = 0))
        assertEquals(13, index(table, FarmingAnchor("loc.oak_tree_fullygrown_2"), offset = 0))
        assertEquals(14, index(table, FarmingAnchor("loc.oak_tree_stump"), offset = 0))
    }

    private fun index(table: FarmingTransformTable, anchor: FarmingAnchor, offset: Int): Int? =
        FarmingRenderer.indexFor(table, anchor, offset) { LOC_IDS[it] }

    private fun table(vararg entries: Pair<Int, Int>): FarmingTransformTable {
        val transforms = IntArray(TABLE_SIZE) { FILLER }
        transforms[TABLE_SIZE - 1] = -1
        for ((index, loc) in entries) {
            transforms[index] = loc
        }
        return FarmingTransformTable(locId = 0, transforms = transforms)
    }

    private companion object {
        private const val TABLE_SIZE = 257

        /** Whatever sits at index 0 doubles as the filler; the allotment table uses 8576. */
        private const val FILLER = 8576

        private const val POTATO_SEED = 8558
        private const val POTATO_GROWN = 8562
        private const val POTATO_SEED_WATERED = 8563
        private const val POTATO_DISEASED_1 = 8567
        private const val POTATO_DEAD_1 = 8570

        private const val OAK_SEEDLING = 8462
        private const val OAK_DISEASED_1 = 8473
        private const val OAK_DEAD_1 = 8477

        private const val APPLE_SEEDLING = 7935
        private const val APPLE_CLAIM_XP = 7948
        private const val APPLE_DISEASED_1 = 7949
        private const val APPLE_DEAD_1 = 7955
        private const val APPLE_STUMP = 7961

        private const val POISON_IVY_SEEDLING = 7662
        private const val POISON_IVY_DISEASED_1 = 7676
        private const val POISON_IVY_DEAD_1 = 7684

        private const val POTATO_1 = 8559
        private const val SNAPE_SEED = 33666
        private const val SNAPE_1 = 33667
        private const val SNAPE_FULLYGROWN = 33673

        private const val OAK_FULLYGROWN_1 = 8466
        private const val OAK_FULLYGROWN_2 = 8467
        private const val OAK_STUMP = 8468

        private const val ABSENT_LOC = 999_999

        private val LOC_IDS: Map<String, Int> =
            mapOf(
                "loc.potato_seed" to POTATO_SEED,
                "loc.potato_1" to POTATO_1,
                "loc.snape_grass_seed" to SNAPE_SEED,
                "loc.snape_grass_1" to SNAPE_1,
                "loc.snape_grass_fullygrown" to SNAPE_FULLYGROWN,
                "loc.oak_tree_seedling" to OAK_SEEDLING,
                "loc.oak_tree_fullygrown_1" to OAK_FULLYGROWN_1,
                "loc.oak_tree_fullygrown_2" to OAK_FULLYGROWN_2,
                "loc.oak_tree_stump" to OAK_STUMP,
            )
    }
}
