package org.rsmod.content.skills.farming.configs

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import dev.openrune.ServerCacheManager
import dev.openrune.definition.util.VarType
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.dbcol.DbColumnCodec.StringCodec
import dev.openrune.types.dbcol.DbException
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.column
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.farming.FarmingAnchor
import org.rsmod.api.farming.FarmingCropRegistry
import org.rsmod.api.farming.FarmingGrowth
import org.rsmod.api.farming.FarmingPatchRegistry
import org.rsmod.api.farming.FarmingPatchState
import org.rsmod.api.farming.FarmingRenderer
import org.rsmod.api.farming.FarmingTransmitVars
import org.rsmod.api.random.GameRandom

@ResourceLock("ServerCacheManager")
class FarmingCropDataTest {
    @Test
    fun `all 60 original crops retain every value optional field and native anchor`() {
        val expected = original()
        assertEquals(60, expected.size)
        assertEquals(expected.map { it.id }, FarmingCrops.ALL.map { it.id })
        assertEquals(expected.map(::canonical), FarmingCrops.ALL)
        assertEquals(253, DbHelper.table("dbtable.farming_crop_visuals").size)
        for (crop in expected) {
            val actual = checkNotNull(FarmingCrops.byId(crop.id))
            assertEquals(crop.deferredPlantXp, actual.deferredPlantXp, crop.id)
            assertEquals(crop.harvestXp, actual.harvestXp, crop.id)
            assertEquals(crop.checkHealthXp, actual.checkHealthXp, crop.id)
            assertEquals(crop.growthMinutes, actual.growthMinutes, crop.id)
            assertEquals(crop.toApiCrop().copy(visuals = actual.visuals), actual.toApiCrop(), crop.id)
        }
    }

    @Test
    fun `persisted crop identities and deferred XP identities resolve without remapping`() {
        for (crop in original()) {
            val state = FarmingPatchState().apply {
                cropId = crop.id
                plantXpOwedCrop = crop.id
                stage = crop.growthCycles
                paidProtected = true
                lastGrowthMinute = 5_000_000_000L
                lastProduceMinute = 4_000_000_000L
            }
            assertEquals(canonical(crop), FarmingCrops.byId(state.cropId))
            assertEquals(crop.deferredPlantXp, FarmingCrops.byId(state.plantXpOwedCrop)?.deferredPlantXp)
            assertSame(FarmingCrops.byId(crop.id), FarmingCrops.byPlantedObj(canonical(crop).plantedObj))
            assertEquals(crop.id, state.cropId)
            assertEquals(crop.growthCycles, state.stage)
            assertTrue(state.paidProtected)
            assertEquals(5_000_000_000L, state.lastGrowthMinute)
            assertEquals(4_000_000_000L, state.lastProduceMinute)
        }
        assertNull(FarmingCrops.byId(null))
        assertNull(FarmingCrops.byId("unknown_future_crop"))
        assertNull(FarmingCrops.byPlantedObj("obj.spade"))
    }

    @Test
    fun `crop and visual schemas use typed items native objects and relational crop rows`() {
        val types = mapOf(
            "farming_crops" to mapOf(
                "seed" to VarType.OBJ, "produce" to VarType.OBJ,
                "protection_item" to VarType.OBJ, "sapling" to VarType.OBJ,
                "pot_seed" to VarType.OBJ, "pot_seed_watered" to VarType.OBJ,
                "placed" to VarType.BOOLEAN, "waterable" to VarType.BOOLEAN,
                "plant_xp_tenths" to VarType.INT, "harvest_xp_tenths" to VarType.INT,
                "check_xp_tenths" to VarType.INT,
            ),
            "farming_crop_visuals" to mapOf(
                "crop" to VarType.DBROW, "band" to VarType.STRING,
                "anchor" to VarType.LOC, "first_stage" to VarType.INT, "stages" to VarType.LOC,
            ),
        )
        for ((table, columns) in types) {
            for ((name, type) in columns) {
                val present = DbHelper.table("dbtable.$table").mapNotNull { row ->
                    try { row.getColumn("dbcol.$table:$name") } catch (_: DbException.MissingColumn) { null }
                }
                assertTrue(present.isNotEmpty(), "$table:$name")
                for (column in present) assertEquals(listOf(type), column.types.toList(), "$table:$name")
            }
        }
    }

    @Test
    fun `all original patch render states remain identical after joining visual rows`() {
        val patches = FarmingPatchRegistry()
        val old = FarmingCropRegistry(patches).apply { registerAll(original().map { it.toApiCrop() }) }
        val current = FarmingCropRegistry(patches).apply { registerAll(FarmingCrops.ALL.map { it.toApiCrop() }) }
        old.validate()
        current.validate()
        val expected = FarmingRenderer(old, FarmingTransmitVars())
        val actual = FarmingRenderer(current, FarmingTransmitVars())
        for (crop in original()) {
            val patch = patches.all().first { it.type == crop.type }
            for (stage in 0..crop.growthCycles) {
                val state = FarmingPatchState().apply { cropId = crop.id; this.stage = stage }
                assertEquals(expected.stateValue(patch, state), actual.stateValue(patch, state), "${crop.id}/$stage")
                if (crop.visuals.watered != null && stage < crop.growthCycles) {
                    state.watered = true
                    assertEquals(expected.stateValue(patch, state), actual.stateValue(patch, state), "${crop.id}/watered/$stage")
                    state.watered = false
                }
                if (stage in 1 until crop.growthCycles) {
                    state.diseased = crop.visuals.diseased != null
                    assertEquals(expected.stateValue(patch, state), actual.stateValue(patch, state), "${crop.id}/diseased/$stage")
                    state.diseased = false
                    state.dead = crop.visuals.dead != null
                    assertEquals(expected.stateValue(patch, state), actual.stateValue(patch, state), "${crop.id}/dead/$stage")
                }
            }
            val grown = FarmingPatchState().apply {
                cropId = crop.id; stage = crop.growthCycles; checkHealthClaimed = true
            }
            for (count in 0..crop.produceCapacity) {
                grown.produceCount = count
                assertEquals(expected.stateValue(patch, grown), actual.stateValue(patch, grown), "${crop.id}/checked/$count")
            }
            if (crop.visuals.stump != null) {
                grown.stump = true
                assertEquals(expected.stateValue(patch, grown), actual.stateValue(patch, grown), "${crop.id}/stump")
            }
        }
    }

    @Test
    fun `growth and produce regrowth preserve every original clock and random draw`() {
        val patches = FarmingPatchRegistry()
        for (crop in original()) {
            val oldRandom = GrowthRandom()
            val newRandom = GrowthRandom()
            val oldGrowth = FarmingGrowth(oldRandom, FarmingCropRegistry(patches).apply { register(crop.toApiCrop()) })
            val newGrowth = FarmingGrowth(newRandom, FarmingCropRegistry(patches).apply {
                register(checkNotNull(FarmingCrops.byId(crop.id)).toApiCrop())
            })
            val oldState = planted(crop.id)
            val newState = planted(crop.id)
            for (now in listOf(10001L, 10010L, 10080L, 10160L, 12000L, 12000L)) {
                assertEquals(oldGrowth.resolve(oldState, crop.type, now), newGrowth.resolve(newState, crop.type, now), crop.id)
                assertEquals(snapshot(oldState), snapshot(newState), "${crop.id}/$now")
                assertEquals(oldRandom.rolls, newRandom.rolls, crop.id)
            }
            oldState.checkHealthClaimed = true
            newState.checkHealthClaimed = true
            oldState.produceCount = 0
            newState.produceCount = 0
            for (now in listOf(12040L, 12080L, 12480L)) {
                assertEquals(oldGrowth.resolve(oldState, crop.type, now), newGrowth.resolve(newState, crop.type, now), crop.id)
                assertEquals(snapshot(oldState), snapshot(newState), "${crop.id}/produce/$now")
            }
        }
    }

    @Test
    fun `every harvest save curve and Hespori denominator remains unchanged`() {
        for (crop in original()) {
            val actual = checkNotNull(FarmingCrops.byId(crop.id))
            assertEquals(crop.hesporiSeedDenominator, HesporiSeedDrops.denominator(actual), crop.id)
            for (level in 0..100) {
                assertEquals(crop.chanceToSave?.atLevel(level), actual.chanceToSave?.atLevel(level), "${crop.id}/$level")
            }
        }
        assertEquals(setOf("scarecrow", "flax", "hemp", "cotton"), FarmingCrops.ALL.filter { it.hesporiSeedDenominator == null }.map { it.id }.toSet())
    }

    @Test
    fun `reader rejects missing duplicate and orphaned crop visual records`() {
        val crops = DbHelper.table("dbtable.farming_crops")
        val visuals = DbHelper.table("dbtable.farming_crop_visuals")
        val invalid = listOf(
            emptyList<DbHelper>() to visuals,
            crops.drop(1) to visuals,
            (crops + crops.first()) to visuals,
            crops to emptyList(),
            crops to visuals.drop(1),
            crops to (visuals + visuals.first()),
            crops.dropLast(1) to visuals,
            crops to visuals.filter { it.column("dbcol.farming_crop_visuals:band", StringCodec) != "growing" },
        )
        for ((cropRows, visualRows) in invalid) {
            assertThrows(IllegalArgumentException::class.java) { FarmingCropData.read(cropRows, visualRows) }
        }
    }

    @Test
    fun `validation rejects ambiguous identities and invalid catalogue quantities`() {
        val crops = FarmingCrops.ALL
        val first = crops.first()
        val invalid = listOf(
            emptyList(), crops + first,
            listOf(first.copy(id = "")) + crops.drop(1),
            listOf(first.copy(seed = crops[1].plantedObj)) + crops.drop(1),
            listOf(first.copy(levelReq = 0)) + crops.drop(1),
            listOf(first.copy(seedsPerPatch = 0)) + crops.drop(1),
            listOf(first.copy(deferredPlantXp = -1.0)) + crops.drop(1),
            listOf(first.copy(harvestXp = Double.NaN)) + crops.drop(1),
            listOf(first.copy(cycleMinutes = 0)) + crops.drop(1),
            listOf(first.copy(baseLives = -1)) + crops.drop(1),
            listOf(first.copy(protection = ProtectionPayment("obj.potato", 0))) + crops.drop(1),
            listOf(first.copy(chanceToSave = ChanceToSave(-1, 80))) + crops.drop(1),
            listOf(first.copy(hesporiSeedDenominator = 0)) + crops.drop(1),
            listOf(first.copy(sapling = "obj.plantpot_oak_sapling")) + crops.drop(1),
        )
        for (case in invalid) assertThrows(IllegalArgumentException::class.java) { FarmingCropData.validate(case) }
    }

    private fun original(): List<FarmingCropDef> = ObjectMapper().registerKotlinModule().readValue(
        checkNotNull(javaClass.getResourceAsStream("/farming/original-crops.json")),
        Array<FarmingCropDef>::class.java,
    ).toList()

    private fun canonical(crop: FarmingCropDef): FarmingCropDef {
        fun obj(name: String?): String? = name?.let { RSCM.getReverseMapping(RSCMType.OBJ, it.asRSCM()) }
        fun anchor(value: FarmingAnchor?): FarmingAnchor? = value?.copy(
            loc = RSCM.getReverseMapping(RSCMType.LOC, value.loc.asRSCM()),
            stageLocs = value.stageLocs.map { RSCM.getReverseMapping(RSCMType.LOC, it.asRSCM()) },
        )
        return crop.copy(
            seed = checkNotNull(obj(crop.seed)), produce = obj(crop.produce), sapling = obj(crop.sapling),
            potSeed = obj(crop.potSeed), potSeedWatered = obj(crop.potSeedWatered),
            protection = crop.protection?.copy(obj = checkNotNull(obj(crop.protection.obj))),
            visuals = crop.visuals.copy(
                growing = checkNotNull(anchor(crop.visuals.growing)), watered = anchor(crop.visuals.watered),
                diseased = anchor(crop.visuals.diseased), dead = anchor(crop.visuals.dead),
                unchecked = anchor(crop.visuals.unchecked), grownChecked = anchor(crop.visuals.grownChecked),
                stump = anchor(crop.visuals.stump),
            ),
        )
    }

    private fun planted(id: String) = FarmingPatchState().apply {
        cropId = id; lastGrowthMinute = 10000L; lastProduceMinute = 10000L; watered = true
    }

    private fun snapshot(state: FarmingPatchState): List<Any?> = listOf(
        state.cropId, state.stage, state.watered, state.diseased, state.dead, state.stump,
        state.lastGrowthMinute, state.lastProduceMinute, state.produceCount, state.checkHealthClaimed,
    )

    private class GrowthRandom : GameRandom {
        val rolls = mutableListOf<Int>()
        override fun of(maxExclusive: Int): Int { rolls += maxExclusive; return maxExclusive - 1 }
        override fun of(minInclusive: Int, maxInclusive: Int): Int = error("unused")
        override fun randomDouble(): Double = error("unused")
    }

    companion object {
        @BeforeAll @JvmStatic fun loadCache() { ServerCacheManager.init(241).close() }
    }
}
