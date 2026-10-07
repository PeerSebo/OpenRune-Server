package org.rsmod.content.interfaces.xpdrops.configs

internal data class XpTrackerSkill(val stat: String?, val startVarp: String, val endVarp: String)

internal object XpTrackerSkills {
    private val entries: List<XpTrackerSkill> =
        listOf(
            XpTrackerSkill("stat.attack", "varp.xpdrops_attack_start", "varp.xpdrops_attack_end"),
            XpTrackerSkill(
                "stat.strength",
                "varp.xpdrops_strength_start",
                "varp.xpdrops_strength_end",
            ),
            XpTrackerSkill("stat.ranged", "varp.xpdrops_ranged_start", "varp.xpdrops_ranged_end"),
            XpTrackerSkill("stat.magic", "varp.xpdrops_magic_start", "varp.xpdrops_magic_end"),
            XpTrackerSkill(
                "stat.defence",
                "varp.xpdrops_defence_start",
                "varp.xpdrops_defence_end",
            ),
            XpTrackerSkill(
                "stat.hitpoints",
                "varp.xpdrops_hitpoints_start",
                "varp.xpdrops_hitpoints_end",
            ),
            XpTrackerSkill("stat.prayer", "varp.xpdrops_prayer_start", "varp.xpdrops_prayer_end"),
            XpTrackerSkill(
                "stat.agility",
                "varp.xpdrops_agility_start",
                "varp.xpdrops_agility_end",
            ),
            XpTrackerSkill(
                "stat.herblore",
                "varp.xpdrops_herblore_start",
                "varp.xpdrops_herblore_end",
            ),
            XpTrackerSkill(
                "stat.thieving",
                "varp.xpdrops_thieving_start",
                "varp.xpdrops_thieving_end",
            ),
            XpTrackerSkill(
                "stat.crafting",
                "varp.xpdrops_crafting_start",
                "varp.xpdrops_crafting_end",
            ),
            XpTrackerSkill(
                "stat.runecrafting",
                "varp.xpdrops_runecraft_start",
                "varp.xpdrops_runecraft_end",
            ),
            XpTrackerSkill("stat.mining", "varp.xpdrops_mining_start", "varp.xpdrops_mining_end"),
            XpTrackerSkill(
                "stat.smithing",
                "varp.xpdrops_smithing_start",
                "varp.xpdrops_smithing_end",
            ),
            XpTrackerSkill(
                "stat.fishing",
                "varp.xpdrops_fishing_start",
                "varp.xpdrops_fishing_end",
            ),
            XpTrackerSkill(
                "stat.cooking",
                "varp.xpdrops_cooking_start",
                "varp.xpdrops_cooking_end",
            ),
            XpTrackerSkill(
                "stat.firemaking",
                "varp.xpdrops_firemaking_start",
                "varp.xpdrops_firemaking_end",
            ),
            XpTrackerSkill(
                "stat.woodcutting",
                "varp.xpdrops_woodcutting_start",
                "varp.xpdrops_woodcutting_end",
            ),
            XpTrackerSkill(
                "stat.fletching",
                "varp.xpdrops_fletching_start",
                "varp.xpdrops_fletching_end",
            ),
            XpTrackerSkill("stat.slayer", "varp.xpdrops_slayer_start", "varp.xpdrops_slayer_end"),
            XpTrackerSkill(
                "stat.farming",
                "varp.xpdrops_farming_start",
                "varp.xpdrops_farming_end",
            ),
            XpTrackerSkill(
                "stat.construction",
                "varp.xpdrops_construction_start",
                "varp.xpdrops_construction_end",
            ),
            XpTrackerSkill("stat.hunter", "varp.xpdrops_hunter_start", "varp.xpdrops_hunter_end"),
            XpTrackerSkill(
                "stat.sailing",
                "varp.xpdrops_sailing_start",
                "varp.xpdrops_sailing_end",
            ),
            XpTrackerSkill(null, "varp.xpdrops_total_start", "varp.xpdrops_total_end"),
        )

    val statNames: List<String> = entries.mapNotNull { it.stat }

    fun bySetupIndex(setupSkill: Int): XpTrackerSkill? = entries.getOrNull(setupSkill - 1)
}
