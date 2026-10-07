package org.rsmod.api.farming

import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class FarmingCropValidationTest : FarmingCacheTest() {
    @Test
    fun `a block that runs into another crop's states fails the boot`() {
        val registry = registry()
        registry.register(potato())
        registry.register(snapeGrassAsContiguousBlock())

        val failure =
            assertFailsWith<IllegalStateException> {
                registry.validateFamily(
                    FarmingPatchType.ALLOTMENT,
                    allotmentTable(),
                    registry.all().toList(),
                    LOC_IDS::get,
                )
            }
        val message = failure.message.orEmpty()
        assertTrue(message.contains("snape_grass"), message)
        assertTrue(message.contains("potato"), message)
        assertTrue(message.contains("135"), message)
        assertTrue(message.contains("FarmingAnchor.stages"), message)
    }

    @Test
    fun `the same crop declared with per-stage anchors validates`() {
        val registry = registry()
        registry.register(potato())
        registry.register(snapeGrassAsPerStageAnchors())

        registry.validateFamily(
            FarmingPatchType.ALLOTMENT,
            allotmentTable(),
            registry.all().toList(),
            LOC_IDS::get,
        )
    }

    @Test
    fun `crops sharing one dead block by name are allowed`() {
        // All herbs intentionally share one dead-state block.
        val registry = registry()
        registry.register(
            herb(
                id = "guam",
                growing = "loc.herb_guam_leaf_seed",
                diseased = "loc.herb_guam_leaf_diseased_1",
            )
        )
        // The cache spells growth states marrentill and disease states marentill.
        registry.register(
            herb(
                id = "marrentill",
                growing = "loc.herb_marrentill_1",
                growingStage = 1,
                diseased = "loc.herb_marentill_diseased_1",
            )
        )

        registry.validateFamily(
            FarmingPatchType.HERB,
            herbTable(),
            registry.all().toList(),
            LOC_IDS::get,
        )
    }

    @Test
    fun `a check-health crop without a grown-checked anchor fails the boot`() {
        val registry = registry()
        registry.register(oak(withGrownChecked = false))

        val failure =
            assertFailsWith<IllegalStateException> {
                registry.validateFamily(
                    FarmingPatchType.TREE,
                    treeTable(),
                    registry.all().toList(),
                    LOC_IDS::get,
                )
            }
        val message = failure.message.orEmpty()
        assertTrue(message.contains("grownChecked"), message)
        assertTrue(message.contains("chopped"), message)
    }

    @Test
    fun `a tree with both grown states validates and claims no index twice`() {
        val registry = registry()
        registry.register(oak(withGrownChecked = true))

        registry.validateFamily(
            FarmingPatchType.TREE,
            treeTable(),
            registry.all().toList(),
            LOC_IDS::get,
        )
    }

    @Test
    fun `a crop that can be diseased but cannot die fails the boot`() {
        val registry = registry()
        registry.register(
            potato()
                .copy(
                    id = "undying_potato",
                    visuals =
                        FarmingCropVisuals(
                            growing = FarmingAnchor("loc.potato_seed"),
                            diseased = FarmingAnchor("loc.potato_1_diseased", stage = 1),
                        ),
                )
        )

        val failure =
            assertFailsWith<IllegalStateException> {
                registry.validateFamily(
                    FarmingPatchType.ALLOTMENT,
                    allotmentTable(),
                    registry.all().toList(),
                    LOC_IDS::get,
                )
            }
        assertTrue(failure.message.orEmpty().contains("freeze permanently"), failure.message)
    }

    @Test
    fun `a block that overruns into filler fails the boot`() {
        val registry = registry()
        registry.register(potato().copy(id = "long_potato", growthStages = 7))

        val failure =
            assertFailsWith<IllegalStateException> {
                registry.validateFamily(
                    FarmingPatchType.ALLOTMENT,
                    allotmentTable(),
                    registry.all().toList(),
                    LOC_IDS::get,
                )
            }
        assertTrue(failure.message.orEmpty().contains("filler"), failure.message)
    }

    @Test
    fun `an anchor that resolves backwards into the weeds head fails the boot`() {
        val registry = registry()
        registry.register(
            potato()
                .copy(
                    id = "backwards_potato",
                    visuals =
                        FarmingCropVisuals(growing = FarmingAnchor("loc.potato_seed", stage = 5)),
                )
        )

        val failure =
            assertFailsWith<IllegalStateException> {
                registry.validateFamily(
                    FarmingPatchType.ALLOTMENT,
                    allotmentTable(),
                    registry.all().toList(),
                    LOC_IDS::get,
                )
            }
        assertTrue(failure.message.orEmpty().contains("weeds/empty head"), failure.message)
    }

    private fun registry() = FarmingCropRegistry(FarmingPatchRegistry())

    private fun potato() =
        FarmingCrop(
            id = "potato",
            patchType = FarmingPatchType.ALLOTMENT,
            growthStages = 5,
            osrsCycleMinutes = 10,
            visuals =
                FarmingCropVisuals(
                    growing = FarmingAnchor("loc.potato_seed"),
                    watered = FarmingAnchor("loc.potato_seed_watered"),
                    diseased = FarmingAnchor("loc.potato_1_diseased", stage = 1),
                    dead = FarmingAnchor("loc.potato_1_dead", stage = 1),
                ),
            diseaseChance = 27,
            baseLives = 3,
        )

    private fun snapeGrassAsContiguousBlock() =
        FarmingCrop(
            id = "snape_grass",
            patchType = FarmingPatchType.ALLOTMENT,
            growthStages = 8,
            osrsCycleMinutes = 10,
            visuals = FarmingCropVisuals(growing = FarmingAnchor("loc.snapegrass_seedling")),
            baseLives = 3,
        )

    private fun snapeGrassAsPerStageAnchors() =
        FarmingCrop(
            id = "snape_grass",
            patchType = FarmingPatchType.ALLOTMENT,
            growthStages = 8,
            osrsCycleMinutes = 10,
            visuals =
                FarmingCropVisuals(
                    growing =
                        FarmingAnchor.stages(
                            listOf(
                                "loc.snapegrass_seedling",
                                "loc.snapegrass_1",
                                "loc.snapegrass_2",
                                "loc.snapegrass_3",
                                "loc.snapegrass_4",
                                "loc.snapegrass_5",
                                "loc.snapegrass_6",
                                "loc.snapegrass_fullygrown",
                            )
                        )
                ),
            baseLives = 3,
        )

    private fun herb(id: String, growing: String, diseased: String, growingStage: Int = 0) =
        FarmingCrop(
            id = id,
            patchType = FarmingPatchType.HERB,
            growthStages = 5,
            osrsCycleMinutes = 20,
            visuals =
                FarmingCropVisuals(
                    growing = FarmingAnchor(growing, stage = growingStage),
                    diseased = FarmingAnchor(diseased, stage = 1),
                    dead = FarmingAnchor("loc.herb_1_dead", stage = 1),
                ),
            diseaseChance = 27,
            baseLives = 3,
        )

    private fun oak(withGrownChecked: Boolean) =
        FarmingCrop(
            id = "oak",
            patchType = FarmingPatchType.TREE,
            growthStages = 5,
            osrsCycleMinutes = 40,
            visuals =
                FarmingCropVisuals(
                    growing = FarmingAnchor("loc.oak_tree_seedling"),
                    diseased = FarmingAnchor("loc.oak_tree_diseased_1", stage = 1),
                    dead = FarmingAnchor("loc.oak_tree_dead_1", stage = 1),
                    unchecked = FarmingAnchor("loc.oak_tree_fullygrown_1"),
                    grownChecked =
                        if (withGrownChecked) FarmingAnchor("loc.oak_tree_fullygrown_2") else null,
                    stump = FarmingAnchor("loc.oak_tree_stump"),
                ),
            diseaseChance = 13,
        )

    private fun allotmentTable(): FarmingTransformTable =
        table(
            ALLOTMENT_FILLER,
            3 to 8573,
            6 to 8558,
            7 to 8559,
            8 to 8560,
            9 to 8561,
            10 to 8562,
            70 to 8563,
            71 to 8564,
            72 to 8565,
            73 to 8566,
            128 to 33666,
            129 to 33667,
            130 to 33668,
            131 to 33669,
            132 to 33670,
            133 to 33671,
            134 to 33672,
            135 to 8567,
            136 to 8568,
            137 to 8569,
            138 to 33673,
            199 to 8570,
            200 to 8571,
            201 to 8572,
        )

    private fun herbTable(): FarmingTransformTable =
        table(
            HERB_FILLER,
            3 to 8132,
            4 to 14209,
            5 to 26825,
            6 to 26826,
            7 to 26827,
            8 to 26828,
            11 to 26829,
            12 to 39748,
            13 to 39749,
            14 to 39750,
            15 to 39751,
            128 to 29568,
            129 to 29569,
            130 to 29570,
            131 to 29571,
            132 to 29572,
            133 to 29573,
            170 to 8147,
            171 to 8148,
            172 to 8149,
        )

    private fun treeTable(): FarmingTransformTable =
        table(
            TREE_FILLER,
            3 to 8392,
            8 to 8462,
            9 to 8463,
            10 to 8464,
            11 to 8465,
            12 to 8466,
            13 to 8467,
            14 to 8468,
            73 to 8473,
            74 to 8474,
            75 to 8475,
            137 to 8477,
            138 to 8478,
            139 to 8479,
        )

    private fun table(filler: Int, vararg entries: Pair<Int, Int>): FarmingTransformTable {
        val transforms = IntArray(TABLE_SIZE) { filler }
        transforms[TABLE_SIZE - 1] = -1
        for ((index, loc) in entries) {
            transforms[index] = loc
        }
        return FarmingTransformTable(locId = 0, transforms = transforms)
    }

    private companion object {
        private const val TABLE_SIZE = 257
        private const val ALLOTMENT_FILLER = 8576
        private const val HERB_FILLER = 8135
        private const val TREE_FILLER = 8395

        private val LOC_IDS: Map<String, Int> =
            mapOf(
                "loc.potato_seed" to 8558,
                "loc.potato_seed_watered" to 8563,
                "loc.potato_1_diseased" to 8567,
                "loc.potato_1_dead" to 8570,
                "loc.snapegrass_seedling" to 33666,
                "loc.snapegrass_1" to 33667,
                "loc.snapegrass_2" to 33668,
                "loc.snapegrass_3" to 33669,
                "loc.snapegrass_4" to 33670,
                "loc.snapegrass_5" to 33671,
                "loc.snapegrass_6" to 33672,
                "loc.snapegrass_fullygrown" to 33673,
                "loc.herb_guam_leaf_seed" to 14209,
                "loc.herb_guam_leaf_diseased_1" to 29568,
                "loc.herb_marrentill_1" to 39748,
                "loc.herb_marentill_diseased_1" to 29571,
                "loc.herb_1_dead" to 8147,
                "loc.oak_tree_seedling" to 8462,
                "loc.oak_tree_diseased_1" to 8473,
                "loc.oak_tree_dead_1" to 8477,
                "loc.oak_tree_fullygrown_1" to 8466,
                "loc.oak_tree_fullygrown_2" to 8467,
                "loc.oak_tree_stump" to 8468,
            )
    }
}
