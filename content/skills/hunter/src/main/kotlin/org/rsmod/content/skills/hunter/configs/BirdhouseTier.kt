package org.rsmod.content.skills.hunter.configs

enum class BirdhouseTier(val code: Int) {
    NORMAL(1), OAK(2), WILLOW(3), TEAK(4), MAPLE(5), MAHOGANY(6), YEW(7), MAGIC(8), REDWOOD(9);

    private val data: HunterCacheData.Birdhouse get() = HunterCacheData.birdhouses.getValue(code)
    val houseObj: String get() = data.house
    val builtLoc: String get() = data.built
    val seededLoc: String get() = data.seeded
    val finishedLoc: String get() = data.finished
    val levelReq: Int get() = data.level
    val xp: Double get() = data.xp
    val logObj: String get() = data.logs
    val craftingLevel: Int get() = data.craftingLevel
    val craftingXp: Double get() = data.craftingXp
    val builtVarValue: Int get() = data.builtState
    val seededVarValue: Int get() = builtVarValue + 1
    val finishedVarValue: Int get() = builtVarValue + 2

    companion object {
        const val STATES_PER_TIER: Int = 3
        const val EMPTY_VAR_VALUE: Int = 0

        fun ofVarValue(varValue: Int): BirdhouseTier? =
            if (varValue <= EMPTY_VAR_VALUE) null
            else entries.firstOrNull { varValue in it.builtVarValue..it.finishedVarValue }

        fun isBuiltEmpty(varValue: Int): Boolean = ofVarValue(varValue)?.builtVarValue == varValue
        fun isSeeded(varValue: Int): Boolean = ofVarValue(varValue)?.seededVarValue == varValue
        fun isFinished(varValue: Int): Boolean = ofVarValue(varValue)?.finishedVarValue == varValue
    }
}
