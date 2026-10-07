package org.rsmod.content.minigames.sorceressgarden.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.Constants
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.restoreRunEnergyPercent
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.content.minigames.sorceressgarden.configs.GardenSeason
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SqirkJuiceScript @Inject constructor() : PluginScript() {
    override fun ScriptContext.startup() {
        for (season in GardenSeason.entries) {
            onOpHeldU("obj.pestle_and_mortar", season.fruitObj) { crushSqirks(season) }
            onOpHeld1(season.juiceObj) { drinkJuice(season) }
        }
        onPlayerQueueWithArgs<CrushTask>(SqirkJuiceQueue) { processCrushTick(it.args.season) }
    }

    private suspend fun ProtectedAccess.drinkJuice(season: GardenSeason) {
        if (invDel(inv, season.juiceObj, 1).failure) {
            return
        }
        anim("seq.human_eat")
        soundSynth(DrinkSound)
        statBoost("stat.thieving", constant = season.thievingBoost, percent = 0)
        player.restoreRunEnergyPercent(season.runEnergyPercent)
        mes("You drink the ${season.name.lowercase()} sq'irkjuice.")
    }

    private suspend fun ProtectedAccess.crushSqirks(season: GardenSeason) {
        if (!hasIngredients(season)) {
            // Registered item-use handlers consume unmatched events; provide fallback feedback explicitly.
            mes(Constants.dm_default)
            return
        }
        // Validate resources again at queue commit; deleting fruit/glass must not lose them on failure.
        weakQueue(SqirkJuiceQueue, CrushCadenceTicks + 1, CrushTask(season))
    }

    private suspend fun ProtectedAccess.processCrushTick(season: GardenSeason) {
        if (!hasIngredients(season)) {
            resetAnim()
            return
        }

        anim("seq.human_herbing_grind")

        if (invDel(inv, "obj.beer_glass", 1).failure) {
            resetAnim()
            return
        }
        if (invDel(inv, season.fruitObj, season.sqirkCount).failure) {
            invAdd(inv, "obj.beer_glass", 1)
            resetAnim()
            return
        }
        if (invAdd(inv, season.juiceObj, 1).failure) {
            invAdd(inv, "obj.beer_glass", 1)
            invAdd(inv, season.fruitObj, season.sqirkCount)
            mes("You don't have enough inventory space.")
            resetAnim()
            return
        }

        soundSynth("synth.GRIND")
        statAdvance("stat.cooking", CookingXp)
        mes("You crush the sq'irks and mix them with the water in the glass, making some sq'irkjuice.")

        if (hasIngredients(season)) {
            // Queue-handler rescheduling needs +1 (docs/skills.md §2); crushing cadence is inferred.
            weakQueue(SqirkJuiceQueue, CrushCadenceTicks + 1, CrushTask(season))
        } else {
            resetAnim()
        }
    }

    private fun ProtectedAccess.hasIngredients(season: GardenSeason): Boolean =
        inv.contains("obj.pestle_and_mortar") &&
            inv.contains("obj.beer_glass") &&
            invTotal(inv, season.fruitObj) >= season.sqirkCount

    private data class CrushTask(val season: GardenSeason)

    private companion object {
        const val SqirkJuiceQueue = "queue.garden_squirk_juice"
        const val CrushCadenceTicks = 3
        const val CookingXp = 5.0

        const val DrinkSound = "synth.LIQUID"
    }
}
