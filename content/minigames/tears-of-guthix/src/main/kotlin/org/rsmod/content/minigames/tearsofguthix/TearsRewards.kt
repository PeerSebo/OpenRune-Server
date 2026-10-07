package org.rsmod.content.minigames.tearsofguthix

import org.rsmod.api.player.stat.PlayerSkillXP
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.game.entity.Player

internal object TearsRewards {
    fun lowest(player: Player): TearsSkill? = TearsData.skills.filter {
        (it.quest.isEmpty() || QuestRequirements.hasCompleted(player, it.quest)) &&
            (!it.needsHouse || player.vars["varbit.poh_house_location"] > 0)
    }.minByOrNull { player.statMap.getFineXP(it.stat) }

    fun experience(tears: Int, previousXp: Int, hardBonus: Boolean): Int {
        val tenths = (100 + previousXp.coerceAtLeast(0) / 27).coerceAtMost(600)
        return (tears.coerceAtLeast(0).toLong() * tenths * if (hardBonus) 11 else 10).div(100).toInt()
    }

    fun award(player: Player, tears: Int): String? {
        val target = lowest(player) ?: return null
        val bonus = player.vars["varbit.lumbridge_diary_hard_complete"] != 0
        val xp = experience(tears, player.statMap.getXP(target.stat), bonus)
        PlayerSkillXP.internalAddXP(player, target.stat, xp.toDouble(), 1.0)
        return target.stat.removePrefix("stat.")
    }
}
