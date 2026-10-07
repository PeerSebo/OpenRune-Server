package org.rsmod.content.other.commands

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.StatType
import jakarta.inject.Inject
import org.rsmod.annotations.InternalApi
import org.rsmod.api.mechanics.toxins.impl.PlayerDisease
import org.rsmod.api.mechanics.toxins.impl.PlayerPoison
import org.rsmod.api.mechanics.toxins.impl.PlayerVenom
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.PlayerSkillXP
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statAdd
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.stat.statHeal
import org.rsmod.api.player.stat.statRestoreAll
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.player.ui.PlayerInterfaceUpdates
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.player.RunEnergy
import org.rsmod.game.stat.PlayerSkillXPTable
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class AdminStatCommands @Inject constructor() : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("heal", "Debug: refill HP and Prayer without removing boosts; ::heal", ::heal)
        onCommand(
            "restore",
            "Debug: reset current stats to base, cure toxins and refill energy; ::restore",
            ::restore,
        )
        onCommand("energy", "Debug: set run energy to 0-100 percent; ::energy [percent]", ::energy)
        onCommand(
            "setlevel",
            "Debug: set one base skill level and its XP (saved); ::setlevel skill level",
            ::setLevel,
        )
        onCommand(
            "setcurrent",
            "Debug: set current level without changing XP; ::setcurrent skill level",
            ::setCurrent,
        )
    }

    private fun heal(cheat: Cheat) = with(cheat) {
        if (!expectArgs(0, "::heal")) return
        if (!canRestore()) return
        for (stat in listOf("stat.hitpoints", "stat.prayer")) {
            if (player.stat(stat) < player.statBase(stat)) player.statHeal(stat, 0, 100)
        }
        player.mes("Hitpoints and Prayer restored; existing boosts retained.")
    }

    private fun restore(cheat: Cheat) = with(cheat) {
        if (!expectArgs(0, "::restore")) return
        if (!canRestore()) return
        player.statRestoreAll(ServerCacheManager.getStats().values.map { it.internalName })
        PlayerPoison.clear(player)
        PlayerVenom.clear(player)
        PlayerDisease.clear(player)
        player.runEnergy = RunEnergy.MAX
        UpdateRun.energy(player, player.runEnergy)
        player.mes("Stats restored to base, toxins cleared and run energy refilled.")
    }

    private fun energy(cheat: Cheat) = with(cheat) {
        if (!expectArgs(0..1, "::energy [0-100]")) return
        val percent = if (args.isEmpty()) 100 else args[0].toIntOrNull()
        if (percent == null || percent !in 0..100) {
            player.mes("Usage: ::energy [0-100]")
            return
        }
        player.runEnergy = percent * RunEnergy.UNITS_PER_PERCENT
        UpdateRun.energy(player, player.runEnergy)
        player.mes("Run energy set to $percent%.")
    }

    @OptIn(InternalApi::class)
    private fun setLevel(cheat: Cheat) = with(cheat) {
        if (!expectArgs(2, "::setlevel skill level")) return
        val stat = resolveStat() ?: return
        val level = args[1].toIntOrNull()
        if (level == null || level !in stat.minLevel..stat.maxLevel) {
            player.mes(
                "${stat.internalName}: base level must be ${stat.minLevel}-${stat.maxLevel}."
            )
            return
        }
        if (!canRestore()) return
        val key = stat.internalName
        player.statMap.setXP(key, PlayerSkillXPTable.getXPFromLevel(level))
        player.statMap.setBaseLevel(key, level.toByte())
        setCurrentLevel(key, level)
        player.markStatUpdate(key)
        player.engineQueueChangeStat(stat)
        player.appearance.combatLevel = PlayerSkillXP.calculateCombatLevel(player)
        PlayerInterfaceUpdates.updateCombatLevel(player)
        player.mes(
            "$key set to level $level with ${player.statMap.getXP(key)} XP (saved on logout)."
        )
    }

    private fun setCurrent(cheat: Cheat) = with(cheat) {
        if (!expectArgs(2, "::setcurrent skill level")) return
        val stat = resolveStat() ?: return
        val level = args[1].toIntOrNull()
        val minimum = if (stat.isType("stat.hitpoints")) 1 else 0
        if (level == null || level !in minimum..255) {
            player.mes("${stat.internalName}: current level must be $minimum-255.")
            return
        }
        if (!canRestore()) return
        setCurrentLevel(stat.internalName, level)
        player.mes("${stat.internalName}: current level $level; base level and XP unchanged.")
    }

    private fun Cheat.setCurrentLevel(stat: String, level: Int) {
        val delta = level - player.stat(stat)
        when {
            delta > 0 -> player.statAdd(stat, delta, 0)
            delta < 0 -> player.statSub(stat, -delta, 0)
        }
    }

    private fun Cheat.resolveStat(): StatType? {
        val name = "stat.${args[0].lowercase().removePrefix("stat.")}"
        val id = runCatching { name.asRSCM() }.getOrNull()
        val stat = id?.let(ServerCacheManager::getStats)
        if (stat == null) {
            player.mes("Unknown skill. Use a skill name, for example mining or hitpoints.")
        }
        return stat
    }

    private fun Cheat.canRestore(): Boolean {
        if (player.stat("stat.hitpoints") > 0 && "queue.death" !in player.queueList &&
            !player.loggingOut && !player.pendingShutdown) return true
        player.mes("Wait until your death or logout has finished before changing stats.")
        return false
    }
}
