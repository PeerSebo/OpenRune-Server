package org.rsmod.content.other.xprates

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.StatType
import jakarta.inject.Inject
import org.rsmod.api.player.output.UpdateStat
import org.rsmod.api.player.stat.PlayerSkillXP
import org.rsmod.api.player.ui.PlayerInterfaceUpdates
import org.rsmod.api.stats.levelmod.InvisibleLevels
import org.rsmod.game.entity.Player
import org.rsmod.game.stat.PlayerSkillXPTable

class XpLevelReset @Inject constructor(private val invisibleLevels: InvisibleLevels) {
    fun reset(player: Player): Int {
        var changed = 0
        for (type in releasedStats()) {
            if (player.resetStat(type)) {
                changed++
            }
        }
        player.appearance.combatLevel = PlayerSkillXP.calculateCombatLevel(player)
        PlayerInterfaceUpdates.updateCombatLevel(player)
        return changed
    }

    private fun Player.resetStat(type: StatType): Boolean {
        val internal = RSCM.getReverseMapping(RSCMType.STAT, type.id)

        // `minLevel` is 10 for Hitpoints and 1 for everything else; the table has no entry for 0.
        val level = type.minLevel.coerceAtLeast(1)
        val fineXp = PlayerSkillXPTable.getFineXPFromLevel(level)
        val changed = statMap.getFineXP(internal) != fineXp

        statMap.setFineXP(internal, fineXp)
        statMap.setBaseLevel(internal, level.toByte())
        statMap.setCurrentLevel(internal, level.toByte())

        val hiddenLevel = level + invisibleLevels.get(this, internal)
        UpdateStat.update(this, type, statMap.getXP(internal), level, hiddenLevel)
        return changed
    }

    private companion object {
        fun releasedStats(): List<StatType> =
            ServerCacheManager.getStats().values.filterNot(StatType::unreleased)
    }
}
