package org.rsmod.content.other.levelup

internal data class LevelUp(val level: Int, val maxLevel: Int, val unlocksContent: Boolean)

internal class SkillJingle(
    val base: String,
    val alternate: String? = null,
    private val preferAlternate: (LevelUp) -> Boolean = { false },
) {
    fun pick(levelUp: LevelUp): String =
        if (alternate != null && preferAlternate(levelUp)) alternate else base
}

internal object LevelUpJingles {
    operator fun get(internalStatName: String): SkillJingle? = byStat[internalStatName]

    private val onUnlock: (LevelUp) -> Boolean = LevelUp::unlocksContent
    private val always: (LevelUp) -> Boolean = { true }
    private val fromLevelFifty: (LevelUp) -> Boolean = { it.level >= 50 }
    private val onEvenLevel: (LevelUp) -> Boolean = { it.level % 2 == 0 }
    private val everyTenthLevel: (LevelUp) -> Boolean = { it.level % 10 == 0 }
    private val atMaxLevel: (LevelUp) -> Boolean = { it.level >= it.maxLevel }

    private val byStat: Map<String, SkillJingle> =
        mapOf(
            "stat.attack" to SkillJingle("jingle.advance_attack", "jingle.advance_attack2", onUnlock),
            "stat.cooking" to
                SkillJingle("jingle.advance_cooking", "jingle.advance_cooking2", onUnlock),
            "stat.crafting" to
                SkillJingle("jingle.advance_crafting", "jingle.advance_crafting2", onUnlock),
            "stat.defence" to
                SkillJingle("jingle.advance_defense", "jingle.advance_defense2", onUnlock),
            "stat.farming" to SkillJingle("jingle.farming_levelup", "jingle.farming_levelup_2", onUnlock),
            "stat.firemaking" to
                SkillJingle("jingle.advance_firemarking", "jingle.advance_firemarking2", onUnlock),
            "stat.fishing" to
                SkillJingle("jingle.advance_fishing", "jingle.advance_fishing2", onUnlock),
            "stat.fletching" to
                SkillJingle("jingle.advance_fletching", "jingle.advance_fletching2", onUnlock),
            "stat.herblore" to
                SkillJingle("jingle.advance_herblaw", "jingle.advance_herblaw2", onUnlock),
            "stat.magic" to SkillJingle("jingle.advance_magic", "jingle.advance_magic2", onUnlock),
            "stat.mining" to SkillJingle("jingle.advance_mining", "jingle.advance_mining2", onUnlock),
            "stat.prayer" to SkillJingle("jingle.advance_prayer", "jingle.advance_prayer2", onUnlock),
            "stat.ranged" to SkillJingle("jingle.advance_ranged", "jingle.advance_ranged2", onUnlock),
            "stat.runecrafting" to
                SkillJingle("jingle.advance_runecraft", "jingle.advance_runecraft2", onUnlock),
            "stat.slayer" to SkillJingle("jingle.advance_slayer", "jingle.advance_slayer2", onUnlock),
            "stat.thieving" to
                SkillJingle("jingle.advance_thieving", "jingle.advance_thieving2", onUnlock),
            "stat.woodcutting" to
                SkillJingle("jingle.advance_woodcutting", "jingle.advance_woodcutting2", onUnlock),

            "stat.agility" to SkillJingle("jingle.advance_agility"),
            "stat.construction" to
                SkillJingle("jingle.advance_carpentry", "jingle.advance_carpentry2", everyTenthLevel),
            "stat.hitpoints" to
                SkillJingle("jingle.advance_hitpoints", "jingle.advance_hitpoints2", fromLevelFifty),
            "stat.hunter" to
                SkillJingle("jingle.advance_hunting", "jingle.advance_hunting2", onEvenLevel),
            "stat.sailing" to
                SkillJingle("jingle.advance_sailing", "jingle.advance_sailing2", atMaxLevel),
            "stat.smithing" to
                SkillJingle("jingle.advance_smithing", "jingle.advance_smithing2", always),
            "stat.strength" to
                SkillJingle("jingle.advance_strength", "jingle.advance_strength2", fromLevelFifty),
        )
}
