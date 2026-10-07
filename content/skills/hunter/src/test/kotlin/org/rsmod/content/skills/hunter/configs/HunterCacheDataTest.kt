package org.rsmod.content.skills.hunter.configs

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock

@ResourceLock("ServerCacheManager")
class HunterCacheDataTest {
    @Test
    fun falconryCachePreservesPreyFalconXpLootAndEveryLevelChance() {
        verifyFalcon(FalconryPrey.SPOTTED, 1, "npc.huntingbeast_speedy", "npc.hunting_falcon_onspeedy", 43, 104.0,
            listOf("obj.bones", "obj.huntingbeast_speedy_fur"),
            listOf(149, 152, 155, 157, 160, 163, 166, 169, 172, 175, 178, 181, 183, 186, 189, 192, 195, 198, 201, 204, 207, 210, 212, 215, 218, 221, 224, 227, 230, 233, 236, 239, 241, 244, 247, 250, 253, 256))
        verifyFalcon(FalconryPrey.DARK, 2, "npc.huntingbeast_silent", "npc.hunting_falcon_onsilent", 57, 132.0,
            listOf("obj.bones", "obj.huntingbeast_silent_fur"),
            listOf(146, 148, 151, 153, 156, 158, 161, 164, 166, 169, 171, 174, 177, 179, 182, 184, 187, 189, 192, 195, 197, 200, 202, 205, 208, 210, 213, 215, 218, 220, 223, 226, 228, 231, 233, 236, 239, 241, 244, 246, 249, 251, 254))
        verifyFalcon(FalconryPrey.DASHING, 3, "npc.huntingbeast_speedy2", "npc.hunting_falcon_onspeedy2", 69, 156.0,
            listOf("obj.bones", "obj.huntingbeast_speedy2_fur", "obj.huntingbeast_speedy2_meat"),
            listOf(143, 145, 147, 150, 152, 154, 156, 158, 160, 162, 164, 166, 168, 170, 173, 175, 177, 179, 181, 183, 185, 187, 189, 191, 193, 196, 198, 200, 202, 204, 206))
    }

    @Test
    fun birdhouseCachePreservesNativeStatesRequirementsCraftingAndHarvestRewards() {
        verifyBirdhouse(BirdhouseTier.NORMAL, 1, "obj.birdhouse_normal", "loc.birdhouse_normal_built", "loc.birdhouse_normal_full", "loc.birdhouse_normal_bird",
            5, 112.0, "obj.logs", 5, 15.0, 1)
        verifyBirdhouse(BirdhouseTier.OAK, 2, "obj.birdhouse_oak", "loc.birdhouse_oak_built", "loc.birdhouse_oak_full", "loc.birdhouse_oak_bird",
            14, 168.0, "obj.oak_logs", 15, 20.0, 4)
        verifyBirdhouse(BirdhouseTier.WILLOW, 3, "obj.birdhouse_willow", "loc.birdhouse_willow_built", "loc.birdhouse_willow_full", "loc.birdhouse_willow_bird",
            24, 224.0, "obj.willow_logs", 25, 25.0, 7)
        verifyBirdhouse(BirdhouseTier.TEAK, 4, "obj.birdhouse_teak", "loc.birdhouse_teak_built", "loc.birdhouse_teak_full", "loc.birdhouse_teak_bird",
            34, 280.0, "obj.teak_logs", 35, 30.0, 10)
        verifyBirdhouse(BirdhouseTier.MAPLE, 5, "obj.birdhouse_maple", "loc.birdhouse_maple_built", "loc.birdhouse_maple_full", "loc.birdhouse_maple_bird",
            44, 369.0, "obj.maple_logs", 45, 35.0, 13)
        verifyBirdhouse(BirdhouseTier.MAHOGANY, 6, "obj.birdhouse_mahogany", "loc.birdhouse_mahogany_built", "loc.birdhouse_mahogany_full", "loc.birdhouse_mahogany_bird",
            49, 480.0, "obj.mahogany_logs", 50, 40.0, 16)
        verifyBirdhouse(BirdhouseTier.YEW, 7, "obj.birdhouse_yew", "loc.birdhouse_yew_built", "loc.birdhouse_yew_full", "loc.birdhouse_yew_bird",
            59, 612.0, "obj.yew_logs", 60, 45.0, 19)
        verifyBirdhouse(BirdhouseTier.MAGIC, 8, "obj.birdhouse_magic", "loc.birdhouse_magic_built", "loc.birdhouse_magic_full", "loc.birdhouse_magic_bird",
            74, 969.0, "obj.magic_logs", 75, 50.0, 22)
        verifyBirdhouse(BirdhouseTier.REDWOOD, 9, "obj.birdhouse_redwood", "loc.birdhouse_redwood_built", "loc.birdhouse_redwood_full", "loc.birdhouse_redwood_bird",
            89, 1200.0, "obj.redwood_logs", 90, 55.0, 25)
    }

    @Test
    fun birdhouseStateLookupRejectsUnusedNativeStates() {
        for (state in listOf(Int.MIN_VALUE, -1, 0, 28, 29, Int.MAX_VALUE)) {
            assertNull(BirdhouseTier.ofVarValue(state))
            assertFalse(BirdhouseTier.isBuiltEmpty(state))
            assertFalse(BirdhouseTier.isSeeded(state))
            assertFalse(BirdhouseTier.isFinished(state))
        }
    }

    private fun verifyFalcon(prey: FalconryPrey, code: Int, npc: String, falcon: String, level: Int,
        xp: Double, loot: List<String>, chances: List<Int>) {
        assertEquals(code, prey.code)
        assertEquals(npc.asRSCM(), prey.npc.asRSCM())
        assertEquals(falcon.asRSCM(), prey.falcon.asRSCM())
        assertEquals(level, prey.level)
        assertEquals(xp, prey.xp)
        assertEquals(loot.map { it.asRSCM() }, prey.loot.map { it.asRSCM() })
        for (hunterLevel in 1..126) {
            val expected = if (hunterLevel < level) 0 else chances[(hunterLevel - level).coerceAtMost(chances.lastIndex)]
            assertEquals(expected, prey.chance(hunterLevel), "$prey at level $hunterLevel")
        }
    }

    private fun verifyBirdhouse(tier: BirdhouseTier, code: Int, house: String, built: String,
        seeded: String, finished: String, level: Int, xp: Double, logs: String,
        craftingLevel: Int, craftingXp: Double, builtState: Int) {
        assertEquals(code, tier.code)
        assertEquals(house.asRSCM(), tier.houseObj.asRSCM())
        assertEquals(built.asRSCM(), tier.builtLoc.asRSCM())
        assertEquals(seeded.asRSCM(), tier.seededLoc.asRSCM())
        assertEquals(finished.asRSCM(), tier.finishedLoc.asRSCM())
        assertEquals(level, tier.levelReq)
        assertEquals(xp, tier.xp)
        assertEquals(logs.asRSCM(), tier.logObj.asRSCM())
        assertEquals(craftingLevel, tier.craftingLevel)
        assertEquals(craftingXp, tier.craftingXp)
        assertEquals(builtState, tier.builtVarValue)
        assertEquals(builtState + 1, tier.seededVarValue)
        assertEquals(builtState + 2, tier.finishedVarValue)
        for (offset in 0..2) {
            assertEquals(tier, BirdhouseTier.ofVarValue(builtState + offset))
            assertEquals(offset == 0, BirdhouseTier.isBuiltEmpty(builtState + offset))
            assertEquals(offset == 1, BirdhouseTier.isSeeded(builtState + offset))
            assertEquals(offset == 2, BirdhouseTier.isFinished(builtState + offset))
        }
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
