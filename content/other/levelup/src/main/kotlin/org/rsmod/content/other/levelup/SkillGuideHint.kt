package org.rsmod.content.other.levelup

import dev.openrune.types.StatType

internal object SkillGuideHint {
    const val Timer: String = "timer.levelup_skill_guide_hint"

    // The N| prefix is the client skill-guide ID, not the stat ID.
    fun message(guideSkillId: Int, displayName: String): String =
        "$guideSkillId|Check the <col=ef1020>skill guide</col> " +
            "to see what your new $displayName level unlocks!"
}

// Use explicit symbols: custom/unreleased stats need not have corresponding guide varbits.
internal fun levelUpListVarbit(stat: StatType): String? {
    val shortName = stat.internalName.removePrefix("stat.")
    if (shortName !in LEVEL_UP_LIST_STATS) {
        return null
    }
    val suffix = if (shortName == "runecrafting") "runecraft" else shortName
    return "varbit.levelup_list_$suffix"
}

private val LEVEL_UP_LIST_STATS =
    setOf(
        "attack",
        "defence",
        "strength",
        "hitpoints",
        "ranged",
        "prayer",
        "magic",
        "cooking",
        "woodcutting",
        "fletching",
        "fishing",
        "firemaking",
        "crafting",
        "smithing",
        "mining",
        "herblore",
        "agility",
        "thieving",
        "slayer",
        "farming",
        "runecrafting",
        "hunter",
        "construction",
        "sailing",
    )
