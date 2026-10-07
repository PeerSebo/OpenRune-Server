package org.rsmod.content.minigames.blastfurnace.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import jakarta.inject.Inject
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.stat.smithingLvl
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerSoftQueue
import org.rsmod.api.script.onPlayerSoftQueueWithArgs
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.minigames.blastfurnace.configs.BlastFurnaceParams
import org.rsmod.content.minigames.blastfurnace.core.BlastFurnaceBar
import org.rsmod.content.minigames.blastfurnace.core.BlastFurnaceBeltLoad
import org.rsmod.content.minigames.blastfurnace.core.BlastFurnaceRecipes
import org.rsmod.content.minigames.blastfurnace.core.BlastFurnaceSmeltRequest
import org.rsmod.content.minigames.blastfurnace.core.BlastFurnaceVisualEvent
import org.rsmod.content.minigames.blastfurnace.core.addBeltInFlight
import org.rsmod.content.minigames.blastfurnace.core.blastFurnaceBarCount
import org.rsmod.content.minigames.blastfurnace.core.blastFurnaceBarsHot
import org.rsmod.content.minigames.blastfurnace.core.blastFurnaceCatalyst
import org.rsmod.content.minigames.blastfurnace.core.blastFurnaceCoal
import org.rsmod.content.minigames.blastfurnace.core.blastFurnaceIronArmedForSteel
import org.rsmod.content.minigames.blastfurnace.core.blastFurnaceIronOre
import org.rsmod.content.minigames.blastfurnace.core.blastFurnaceOreCount
import org.rsmod.content.minigames.blastfurnace.core.setBlastFurnaceBarCount
import org.rsmod.content.minigames.blastfurnace.core.setBlastFurnaceOreCount
import org.rsmod.content.minigames.blastfurnace.core.takeBeltInFlight
import org.rsmod.content.skills.smithing.smelting.SmithingSmeltXp
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
class SmeltingProcess @Inject constructor(private val xpMods: XpModifiers) : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerSoftQueueWithArgs<BlastFurnaceBeltLoad>(BlastFurnaceParams.BELT_QUEUE) {
            deliverLoad(player, args)
        }
        onPlayerLogout {
            flushBeltInFlight(player)
            player.clearQueue(BlastFurnaceParams.BELT_QUEUE)
            player.clearQueue(BlastFurnaceParams.SMELT_QUEUE)
        }
        onPlayerSoftQueue(BlastFurnaceParams.SMELT_QUEUE) {
            if (processPot(player)) eventBus.publish(BlastFurnaceVisualEvent.Pour)
        }
        onEvent<BlastFurnaceSmeltRequest> {
            if (processPot(player)) eventBus.publish(BlastFurnaceVisualEvent.Pour)
        }
        onPlayerSoftTimer(BlastFurnaceParams.HOT_TIMER) {
            player.clearSoftTimer(BlastFurnaceParams.HOT_TIMER)
            when (player.blastFurnaceBarsHot) {
                DISPENSER_POURING -> {
                    player.blastFurnaceBarsHot = DISPENSER_HOT
                    player.softTimer(BlastFurnaceParams.HOT_TIMER, BlastFurnaceParams.AUTO_COOL_TICKS)
                }
                DISPENSER_HOT -> player.blastFurnaceBarsHot = DISPENSER_COOLED
                else -> {}
            }
        }
    }

    internal fun deliverLoad(player: Player, load: BlastFurnaceBeltLoad) {
        for (entry in load.entries) {
            player.addBeltInFlight(entry.ore, -entry.amount)
            val current = player.blastFurnaceOreCount(entry.ore)
            val room = (VARBIT_CEILING - current).coerceAtLeast(0)
            val amount = minOf(entry.amount, room)
            if (amount <= 0) {
                continue
            }
            player.setBlastFurnaceOreCount(entry.ore, current + amount)
            player.soundSynth(BlastFurnaceParams.SOUND_ORE_IN_FURNACE)
        }
    }

    internal fun processPot(player: Player): Boolean {
        var produced = false
        if (trySmeltIron(player)) {
            produced = true
        }
        for (recipe in OTHER_PRIMARIES) {
            if (trySmelt(player, recipe)) {
                produced = true
            }
        }
        if (produced) {
            player.blastFurnaceBarsHot = DISPENSER_POURING
            val pourTicks = checkNotNull(ServerCacheManager.getAnim("seq.blast_furnace_ore_dispense".asRSCM())).tickDuration
            player.softTimer(BlastFurnaceParams.HOT_TIMER, maxOf(BlastFurnaceParams.POURING_TO_HOT_TICKS, pourTicks))
            player.soundSynth(BlastFurnaceParams.SOUND_SMELT)
        }
        return produced
    }

    private fun ScriptContext.flushBeltInFlight(player: Player) {
        for (entry in player.takeBeltInFlight()) {
            val next = player.blastFurnaceOreCount(entry.ore) + entry.amount
            check(next <= VARBIT_CEILING) { "belt flush of ${entry.amount} ${entry.ore} overflows stock varbit" }
            player.setBlastFurnaceOreCount(entry.ore, next)
        }
    }
    private val OTHER_PRIMARIES: List<BlastFurnaceBar> by lazy {
        val ironAndSteel = setOf("obj.iron_bar", "obj.steel_bar")
        BlastFurnaceRecipes.all.filterNot { it.output.internalName in ironAndSteel }
    }

    private fun trySmelt(player: Player, recipe: BlastFurnaceBar): Boolean {
        if (player.smithingLvl < recipe.levelReq) {
            return false
        }
        val barOutput = recipe.output.internalName
        val capRoom = BlastFurnaceParams.BAR_DISPENSER_CAP - player.blastFurnaceBarCount(barOutput)
        if (capRoom <= 0) {
            return false
        }

        val primaryInternal = recipe.primaryOre.internalName
        val primaryHave = player.blastFurnaceOreCount(primaryInternal)
        var count = primaryHave / recipe.primaryAmount

        val secondary = recipe.secondaryOre
        val secondaryHave = secondary?.let { player.blastFurnaceOreCount(it.internalName) } ?: 0
        if (secondary != null) {
            count = minOf(count, secondaryHave / recipe.secondaryAmount)
        }
        count = minOf(count, capRoom)
        if (count <= 0) {
            return false
        }

        player.setBlastFurnaceOreCount(primaryInternal, primaryHave - count * recipe.primaryAmount)
        if (secondary != null) {
            player.setBlastFurnaceOreCount(
                secondary.internalName,
                secondaryHave - count * recipe.secondaryAmount,
            )
        }
        val catalystEligible = recipe.usesCoal && barOutput != "obj.gold_bar"
        val boosted =
            if (catalystEligible) {
                val boostCount = minOf(count, player.blastFurnaceCatalyst)
                player.blastFurnaceCatalyst -= boostCount
                boostCount
            } else {
                0
            }

        awardXp(player, recipe, count, boosted)
        player.setBlastFurnaceBarCount(barOutput, player.blastFurnaceBarCount(barOutput) + count)
        return true
    }

    private fun trySmeltIron(player: Player): Boolean {
        val ironFallback = BlastFurnaceRecipes.ironFallback
        if (player.smithingLvl < ironFallback.levelReq) {
            return false
        }
        val ironHave = player.blastFurnaceIronOre
        if (ironHave < 1) {
            return false
        }

        val steelRecipe = BlastFurnaceRecipes.steel
        if (player.blastFurnaceIronArmedForSteel && player.smithingLvl >= steelRecipe.levelReq) {
            val capRoom =
                BlastFurnaceParams.BAR_DISPENSER_CAP - player.blastFurnaceBarCount("obj.steel_bar")
            val coalHave = player.blastFurnaceCoal
            val count = minOf(ironHave, coalHave / steelRecipe.secondaryAmount, capRoom)
            if (count <= 0) {
                return false
            }
            player.blastFurnaceIronOre = ironHave - count
            player.blastFurnaceCoal = coalHave - count * steelRecipe.secondaryAmount
            val boosted = minOf(count, player.blastFurnaceCatalyst)
            player.blastFurnaceCatalyst -= boosted
            awardXp(player, steelRecipe, count, boosted)
            player.setBlastFurnaceBarCount(
                "obj.steel_bar",
                player.blastFurnaceBarCount("obj.steel_bar") + count,
            )
            return true
        }

        val capRoom = BlastFurnaceParams.BAR_DISPENSER_CAP - player.blastFurnaceBarCount("obj.iron_bar")
        val count = minOf(ironHave, capRoom)
        if (count <= 0) {
            return false
        }
        player.blastFurnaceIronOre = ironHave - count
        awardXp(player, ironFallback, count, boostedCount = 0)
        player.setBlastFurnaceBarCount(
            "obj.iron_bar",
            player.blastFurnaceBarCount("obj.iron_bar") + count,
        )
        return true
    }

    private fun awardXp(player: Player, recipe: BlastFurnaceBar, count: Int, boostedCount: Int) {
        val effectiveBars = (count - boostedCount) + boostedCount * 2.0
        val xp = SmithingSmeltXp.resolve(player, recipe.smithingBar, isSuperHeat = false, xpMods = xpMods) *
            effectiveBars
        player.statAdvance("stat.smithing", xp)
    }

    internal companion object {
        const val DISPENSER_EMPTY = 0
        const val DISPENSER_POURING = 1
        const val DISPENSER_HOT = 2
        const val DISPENSER_COOLED = 3
        private const val VARBIT_CEILING = 255
    }
}
