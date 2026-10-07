package org.rsmod.api.farming

import dev.openrune.ServerCacheManager
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

internal class FarmingWorldDataTest : FarmingCacheTest() {
    @Test
    fun `all patch families retain their original fields and enum order`() {
        assertEquals(
            fixture("patch-types"),
            FarmingWorldData.patchTypes.values.map {
                listOf(it.order, it.key, it.seedFamily, it.waterable, it.cure, it.checkHealth,
                    it.bearsProduce, it.emptyLocName, it.weedCycleMinutes).map(Any::toString)
            },
        )
        for (type in FarmingPatchType.entries) {
            val row = FarmingWorldData.patchTypes.getValue(type.name)
            assertEquals(row.order, type.ordinal)
            assertSame(type, FarmingPatchType.ofSeedFamily(row.seedFamily))
            assertEquals(row.waterable, type.waterable)
            assertEquals(row.checkHealth, type.checkHealth)
            assertEquals(row.bearsProduce, type.bearsProduce)
            assertEquals(row.emptyLocName, type.emptyLocName)
            assertEquals(FarmingCureMethod.valueOf(row.cure), type.cure)
            assertEquals(maxOf(1, row.weedCycleMinutes / FarmingConstants.GROWTH_SPEED_MULTIPLIER), type.weedCycleMinutes)
        }
        assertNull(FarmingPatchType.ofSeedFamily(8))
    }

    @Test
    fun `all bin families retain their original names capacities and enum order`() {
        assertEquals(
            fixture("bin-types"),
            FarmingWorldData.binTypes.values.map { listOf(it.order, it.key, it.locPrefix, it.capacity).map(Any::toString) },
        )
        for (type in FarmingBinType.entries) {
            val row = FarmingWorldData.binTypes.getValue(type.name)
            assertEquals(row.order, type.ordinal)
            assertEquals(row.locPrefix, type.locPrefix)
            assertEquals(row.capacity, type.capacity)
        }
    }

    @Test
    fun `compost preserves existing harvest lives and disease reduction`() {
        assertEquals(
            fixture("compost-types"),
            FarmingWorldData.compostTypes.values.map { listOf(it.order, it.key, it.extraLives, it.diseaseDivisor).map(Any::toString) },
        )
        for (type in FarmingCompost.entries) {
            val row = FarmingWorldData.compostTypes.getValue(type.name)
            assertEquals(row.order, type.ordinal)
            assertEquals(row.extraLives, type.extraLives)
            assertEquals(row.diseaseDivisor, type.diseaseDivisor)
            assertSame(type, FarmingCompost.find(row.key.lowercase()))
        }
    }

    @Test
    fun `patch locations retain all original saved keys and family assignments`() {
        assertEquals(fixture("patches"), FarmingWorldData.patches.map { listOf(it.order.toString(), it.locName, it.family) })
        val registry = FarmingPatchRegistry()
        assertEquals(59, registry.all().size)
        for (row in FarmingWorldData.patches) {
            val def = assertNotNull(registry.byKey(FarmingPatchKey(row.locName.removePrefix("loc."))))
            assertSame(def, registry.byLoc(row.locId))
            assertTrue(registry.isPatchLoc(row.locId))
            assertEquals(FarmingPatchType.valueOf(row.family), def.type)
            assertEquals(row.locName, def.locName)
            assertEquals(row.locId, def.locId)
            val native = assertNotNull(ServerCacheManager.getObject(row.locId))
            assertEquals(FarmingTransmitSlot.ofVarbitId(native.multiVarBit), def.transmitSlot)
            val transforms = assertNotNull(def.transforms)
            assertEquals(native.multiLoc.toList(), (0 until transforms.size).map(transforms::at))
        }
        assertFalse(registry.isPatchLoc(-1))
        assertNull(registry.byKey(FarmingPatchKey("missing")))
    }

    @Test
    fun `bin locations retain all original saved keys and native transforms`() {
        assertEquals(fixture("bins"), FarmingWorldData.bins.map { listOf(it.order.toString(), it.locName, it.family) })
        val registry = FarmingBinRegistry()
        assertEquals(8, registry.all().size)
        for (row in FarmingWorldData.bins) {
            val def = assertNotNull(registry.byKey(FarmingBinKey(row.locName.removePrefix("loc."))))
            assertSame(def, registry.byLoc(row.locId))
            assertTrue(registry.isBinLoc(row.locId))
            assertEquals(FarmingBinType.valueOf(row.family), def.type)
            val native = assertNotNull(ServerCacheManager.getObject(row.locId))
            assertEquals(FarmingTransmitSlot.ofVarbitId(native.multiVarBit), def.transmitSlot)
            val transforms = assertNotNull(def.transforms)
            assertEquals(native.multiLoc.toList(), (0 until transforms.size).map(transforms::at))
        }
        assertFalse(registry.isBinLoc(-1))
    }

    @Test
    fun `registering extensions invalidates patch and bin location indices`() {
        val patches = FarmingPatchRegistry()
        val patchLoc = assertNotNull(FarmingTransformTable.locIdOrNull("loc.veg_patch_weeded"))
        assertNull(patches.byLoc(patchLoc))
        val patch = patches.register("loc.veg_patch_weeded", FarmingPatchType.ALLOTMENT)
        assertSame(patch, patches.byLoc(patchLoc))
        assertSame(patch, patches.byKey(patch.key))
        assertEquals(60, patches.all().size)

        val bins = FarmingBinRegistry()
        val binLoc = assertNotNull(FarmingTransformTable.locIdOrNull("loc.compost_bin_empty"))
        assertNull(bins.byLoc(binLoc))
        val bin = bins.register("loc.compost_bin_empty", FarmingBinType.STANDARD)
        assertSame(bin, bins.byLoc(binLoc))
        assertSame(bin, bins.byKey(bin.key))
        assertEquals(9, bins.all().size)
    }

    @Test
    fun `bin states render their actual family loc across every physical bin`() {
        val renderer = FarmingBinRenderer(FarmingTransmitVars())
        for (def in FarmingBinRegistry().all()) {
            val table = assertNotNull(def.transforms)
            val states = buildList {
                add(FarmingBinState())
                for (fill in FarmingBinFill.entries.filter { it != FarmingBinFill.NONE }) {
                    for (count in 1..def.capacity) add(FarmingBinState().apply { this.fill = fill; this.count = count })
                }
                for (product in FarmingBinProduct.entries) {
                    for (count in 1..def.capacity) add(FarmingBinState().apply { phase = FarmingBinPhase.OPEN; this.product = product; this.count = count })
                    if (product.hasRotStates) {
                        for (phase in listOf(FarmingBinPhase.ROTTING, FarmingBinPhase.READY)) {
                            add(FarmingBinState().apply { this.phase = phase; this.product = product; count = def.capacity })
                        }
                    }
                }
            }
            for (state in states) {
                val name = assertNotNull(FarmingBinRenderer.stateLocName(def.type, state))
                val id = assertNotNull(FarmingTransformTable.locIdOrNull(name))
                val rendered = renderer.stateValue(def, state)
                assertEquals(id, table.at(rendered), "${def.key}: $state")
                assertFalse(table.isFiller(rendered), "${def.key}: $name")
            }
        }
    }

    @Test
    fun `empty patches preserve native rake states across all families`() {
        val patches = FarmingPatchRegistry()
        val renderer = FarmingRenderer(FarmingCropRegistry(patches), FarmingTransmitVars())
        for (def in patches.all()) {
            for (rake in 0..FarmingConstants.RAKE_STAGES) {
                assertEquals(rake, renderer.stateValue(def, FarmingPatchState().apply { rakeStage = rake }))
            }
        }
    }

    @Test
    fun `location validation rejects collisions unknown families and key mismatches`() {
        val rows = FarmingWorldData.patches
        val families = FarmingWorldData.patchTypes.keys
        assertFailsWith<IllegalArgumentException> { FarmingWorldData.validateLocations(emptyList(), families) }
        assertFailsWith<IllegalArgumentException> { FarmingWorldData.validateLocations(rows + rows.first(), families) }
        assertFailsWith<IllegalArgumentException> { FarmingWorldData.validateLocations(rows.toMutableList().apply { this[1] = this[1].copy(locId = first().locId) }, families) }
        assertFailsWith<IllegalArgumentException> { FarmingWorldData.validateLocations(rows.toMutableList().apply { this[0] = first().copy(family = "MISSING") }, families) }
        assertFailsWith<IllegalArgumentException> { FarmingWorldData.validateLocations(rows, families) { -1 } }
    }

    @Test
    fun `family validation rejects missing identities and invalid growth or capacity values`() {
        val patches = FarmingWorldData.patchTypes.values.toList()
        assertFailsWith<IllegalArgumentException> { FarmingWorldData.validatePatchTypes(patches.dropLast(1)) }
        assertFailsWith<IllegalArgumentException> { FarmingWorldData.validatePatchTypes(patches.toMutableList().apply { this[0] = first().copy(weedCycleMinutes = 0) }) }
        assertFailsWith<IllegalArgumentException> { FarmingWorldData.validatePatchTypes(patches.toMutableList().apply { this[0] = first().copy(cure = "MISSING") }) }
        val bins = FarmingWorldData.binTypes.values.toList()
        assertFailsWith<IllegalArgumentException> { FarmingWorldData.validateBinTypes(bins.toMutableList().apply { this[0] = first().copy(capacity = 0) }) }
        val compost = FarmingWorldData.compostTypes.values.toList()
        assertFailsWith<IllegalArgumentException> { FarmingWorldData.validateCompostTypes(compost.toMutableList().apply { this[0] = first().copy(diseaseDivisor = 0) }) }
        assertFailsWith<IllegalArgumentException> { FarmingWorldData.validateCompostTypes(compost.reversed()) }
    }

    private fun fixture(name: String): List<List<String>> =
        checkNotNull(javaClass.getResourceAsStream("/farming-world-$name.tsv"))
            .bufferedReader().use { it.readLines().map { row -> row.split('\t') } }
}
