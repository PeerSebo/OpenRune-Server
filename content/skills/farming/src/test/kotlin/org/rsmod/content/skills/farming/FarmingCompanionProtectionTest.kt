package org.rsmod.content.skills.farming

import dev.openrune.ServerCacheManager
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.farming.FarmingClock
import org.rsmod.api.farming.FarmingCropRegistry
import org.rsmod.api.farming.FarmingGrowth
import org.rsmod.api.farming.FarmingManager
import org.rsmod.api.farming.FarmingPatchKey
import org.rsmod.api.farming.FarmingPatchRegistry
import org.rsmod.api.farming.FarmingPatchState
import org.rsmod.api.farming.FarmingRenderer
import org.rsmod.api.farming.FarmingTransmitVars
import org.rsmod.api.farming.farmingPatchesOrCreate
import org.rsmod.api.random.GameRandom
import org.rsmod.content.skills.farming.configs.FarmingCrops
import org.rsmod.game.entity.Player

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class FarmingCompanionProtectionTest {
    @Test
    fun `a mature Hosidius flower protects its two allotments without protecting Harmony`() {
        val fixture = Fixture()
        fixture.plant("farming_flower_patch_5", "marigold", grown = true)
        val northEast = fixture.plant("farming_veg_patch_10", "potato")
        val southWest = fixture.plant("farming_veg_patch_11", "tomato")
        val harmony = fixture.plant("farming_veg_patch_9", "potato")

        fixture.resolveProtection("farming_veg_patch_11")

        assertTrue(northEast.protected)
        assertTrue(southWest.protected)
        assertFalse(harmony.protected)
    }

    @Test
    fun `guild white lily protects snape grass and does not create untouched allotments`() {
        val fixture = Fixture()
        fixture.plant("farming_flower_patch_6", "white_lily", grown = true)
        val crop = fixture.plant("farming_veg_patch_13", "snape_grass")

        fixture.resolveProtection("farming_flower_patch_6")

        assertTrue(crop.protected)
        assertNull(fixture.player.farmingPatchesOrCreate().getOrNull(FarmingPatchKey("farming_veg_patch_12")))
    }

    @Test
    fun `lost companion protection leaves the gardeners payment intact`() {
        val fixture = Fixture()
        val flower = fixture.plant("farming_flower_patch_7", "marigold", grown = true)
        val crop = fixture.plant("farming_veg_patch_14", "potato")
        crop.paidProtected = true
        fixture.resolveProtection("farming_veg_patch_14")
        assertTrue(crop.protected)

        flower.dead = true
        fixture.resolveProtection("farming_veg_patch_14")

        assertFalse(crop.protected)
        assertTrue(crop.paidProtected)
    }

    @Test
    fun `Civitas scarecrow protects sweetcorn but not unrelated crops`() {
        val fixture = Fixture()
        fixture.plant("farming_flower_patch_8", FarmingCrops.SCARECROW_ID, grown = true)
        val sweetcorn = fixture.plant("farming_veg_patch_16", "sweetcorn")
        val watermelon = fixture.plant("farming_veg_patch_17", "watermelon")

        fixture.resolveProtection("farming_veg_patch_16")

        assertTrue(sweetcorn.protected)
        assertFalse(watermelon.protected)
    }

    @Test
    fun `immature flowers and isolated patches give no companion protection`() {
        val fixture = Fixture()
        fixture.plant("farming_flower_patch_5", "marigold")
        val crop = fixture.plant("farming_veg_patch_10", "potato")
        fixture.resolveProtection("farming_veg_patch_10")

        assertFalse(crop.protected)
        assertNull(FarmingPatchGroups.forPatch("farming_flower_patch_9"))
        assertNull(FarmingPatchGroups.forPatch("farming_veg_patch_9"))
        assertNull(FarmingPatchGroups.forPatch("farming_herb_patch_5"))
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }

    private class Fixture {
        val player = Player()
        private val patches = FarmingPatchRegistry()
        private val crops = FarmingCropRegistry(patches).apply {
            registerAll(FarmingCrops.ALL.map { it.toApiCrop() })
        }
        private val random = object : GameRandom {
            override fun of(maxExclusive: Int): Int = error("Protection must not roll growth here")
            override fun of(minInclusive: Int, maxInclusive: Int): Int = error("Unexpected roll")
            override fun randomDouble(): Double = error("Unexpected roll")
        }
        private val manager = FarmingManager(
            patches,
            crops,
            FarmingGrowth(random, crops),
            FarmingRenderer(crops, FarmingTransmitVars()),
            random,
            { emptySet() },
        )
        private val hook = FarmingCompanionProtectionHook(manager, crops)

        fun plant(key: String, crop: String, grown: Boolean = false): FarmingPatchState {
            val definition = checkNotNull(crops.find(crop))
            return player.farmingPatchesOrCreate().getOrCreate(FarmingPatchKey(key)).apply {
                cropId = crop
                stage = if (grown) definition.grownStage else 0
                lastGrowthMinute = FarmingClock.currentMinute()
            }
        }

        fun resolveProtection(key: String) {
            hook.beforeResolve(player, checkNotNull(patches.byKey(FarmingPatchKey(key))))
        }
    }
}
