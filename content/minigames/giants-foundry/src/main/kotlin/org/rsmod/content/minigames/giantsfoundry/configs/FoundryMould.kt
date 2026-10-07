package org.rsmod.content.minigames.giantsfoundry.configs

enum class FoundryMouldPart(val key: String, val selectedVarbit: String) {
    FORTE("ricasso", "varbit.giants_foundry_mould_selected_ricasso"),
    BLADE("blade", "varbit.giants_foundry_mould_selected_blade"),
    TIP("tip", "varbit.giants_foundry_mould_selected_tip"),
}

data class FoundryMould(
    val part: FoundryMouldPart,
    val index: Int,
    val structId: Int,
    val name: String,
    val level: Int,
    val width: Int,
    val weight: Int,
    val profile: Int,
    val unlockVarbit: String?,
) {
    fun score(word: Int): Int = when (word) {
        1 -> (-width).coerceAtLeast(0)
        2 -> (-weight).coerceAtLeast(0)
        3 -> (-profile).coerceAtLeast(0)
        4 -> width.coerceAtLeast(0)
        5 -> weight.coerceAtLeast(0)
        6 -> profile.coerceAtLeast(0)
        else -> 0
    }
}

data class FoundryReward(
    val index: Int,
    val item: String?,
    val mould: FoundryMould?,
    val cost: Int,
)
