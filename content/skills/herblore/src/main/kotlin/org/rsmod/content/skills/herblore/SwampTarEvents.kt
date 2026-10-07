package org.rsmod.content.skills.herblore

import org.rsmod.api.config.Constants
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.table.herblore.HerbloreSwampTarRow
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SwampTarEvents : PluginScript() {

    override fun ScriptContext.startup() {
        HerbloreDefinitions.swampTars.forEach { recipe ->
            onOpHeldU(SWAMP_TAR, recipe.herb.internalName) { createSwampTar(recipe) }
        }
        onPlayerQueueWithArgs<SwampTarTask>("queue.herblore_swamp_tar") {
            processSwampTarTick(it.args)
        }
    }

    private suspend fun ProtectedAccess.createSwampTar(recipe: HerbloreSwampTarRow) {
        if (!meetsStatReqs(recipe.statReq)) {
            return
        }

        if (
            !inv.contains("obj.pestle_and_mortar") ||
            !inv.contains(recipe.herb.internalName) ||
            inv.count(SWAMP_TAR) < SWAMP_TAR_PER_BATCH
        ) {
            mes(Constants.dm_default)
            return
        }

        // Deleting the nonstackable herb frees the stackable tar's slot.
        weakQueue("queue.herblore_swamp_tar", 1, SwampTarTask(recipe))
    }

    private suspend fun ProtectedAccess.processSwampTarTick(task: SwampTarTask) {
        val recipe = task.recipe

        if (!meetsStatReqs(recipe.statReq)) {
            resetAnim()
            return
        }

        if (
            !inv.contains("obj.pestle_and_mortar") ||
            !inv.contains(recipe.herb.internalName) ||
            inv.count(SWAMP_TAR) < SWAMP_TAR_PER_BATCH
        ) {
            resetAnim()
            return
        }

        anim("seq.human_herbing_vial")

        val herbRemoved = invDel(inv, recipe.herb.internalName, 1).success
        val swampTarRemoved = invDel(inv, SWAMP_TAR, SWAMP_TAR_PER_BATCH).success

        if (!herbRemoved || !swampTarRemoved) {
            if (herbRemoved) invAdd(inv, recipe.herb.internalName, 1)
            if (swampTarRemoved) invAdd(inv, SWAMP_TAR, SWAMP_TAR_PER_BATCH)
            resetAnim()
            return
        }

        if (invAdd(inv, recipe.finishedTar.internalName, SWAMP_TAR_PER_BATCH).failure) {
            invAdd(inv, recipe.herb.internalName, 1)
            invAdd(inv, SWAMP_TAR, SWAMP_TAR_PER_BATCH)
            mes("You don't have enough inventory space to make this tar.")
            resetAnim()
            return
        }

        statAdvance("stat.herblore", recipe.xp.toDouble())
        val herbName = recipe.herb.name.lowercase()
        mes("You add the $herbName to the swamp tar.")

        if (
            inv.contains("obj.pestle_and_mortar") &&
            inv.contains(recipe.herb.internalName) &&
            inv.count(SWAMP_TAR) >= SWAMP_TAR_PER_BATCH
        ) {
            // Queue-handler rescheduling requires +1 to preserve the three-tick cadence.
            weakQueue("queue.herblore_swamp_tar", 4, SwampTarTask(recipe))
        } else {
            resetAnim()
        }
    }

    private data class SwampTarTask(val recipe: HerbloreSwampTarRow)

    companion object {
        private const val SWAMP_TAR = "obj.swamp_tar"
        private const val SWAMP_TAR_PER_BATCH = 15
    }
}
