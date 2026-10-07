package org.rsmod.content.skills.smithing.smelting

import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.smithing.table.SmithingBar
import org.rsmod.game.entity.Player

object SmithingSmeltXp {
    private const val GOLD_BAR = "obj.gold_bar"
    private const val BLURITE_BAR = "obj.blurite_bar"

    // Apply catalyst XP per bar; a double-smelt can mix boosted and unboosted bars.
    fun resolve(
        player: Player,
        bar: SmithingBar,
        isSuperHeat: Boolean,
        xpMods: XpModifiers,
    ): Double {
        val alternate = bar.smeltXpAlternate
        val baseXp =
            when {
                alternate == null -> bar.smeltXp
                bar.output.internalName == BLURITE_BAR && isSuperHeat -> alternate
                bar.output.internalName == GOLD_BAR && player.hasGoldsmithSmeltBonus() -> alternate
                else -> bar.smeltXp
            }

        return baseXp * xpMods.get(player, "stat.smithing")
    }

    private fun Player.hasGoldsmithSmeltBonus(): Boolean =
        "obj.gauntlets_of_goldsmithing" in worn ||
            "obj.skillcape_smithing" in worn ||
            "obj.skillcape_smithing_trimmed" in worn ||
            "obj.skillcape_max" in worn
}
