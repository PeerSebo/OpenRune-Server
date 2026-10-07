package org.rsmod.content.skills.fletching.scripts

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.content.skills.SkillMultiConfig
import org.rsmod.content.skills.SkillingActionType
import org.rsmod.content.skills.fletching.configs.FletchingParams
import org.rsmod.content.skills.fletching.table.FletchingRecipe
import org.rsmod.content.skills.openSkillMulti
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class KnifeCarvingScript : PluginScript() {

    private val recipesByLog =
        FletchingRecipe.table("fletching_carving").groupBy { it.inputs.first().internalName }

    override fun ScriptContext.startup() {
        for ((log, recipes) in recipesByLog) {
            onOpHeldU(FletchingParams.KNIFE, log) { promptCarve(recipes) }
            onOpHeldU("obj.fletching_knife", log) { promptCarve(recipes) }
        }
        onPlayerQueueWithArgs<FletchingTask>(FletchingParams.CARVE_QUEUE) { processCarve(it.args) }
    }

    private suspend fun ProtectedAccess.promptCarve(recipes: List<FletchingRecipe>) {
        if (!hasCarvingTool()) return
        val candidates = recipes.filter { hasLevelFor(it) && hasMaterials(it) }
        if (candidates.isEmpty()) {
            val unlocked = recipes.filter { hasLevelFor(it) }
            if (unlocked.isEmpty()) {
                meetsFletchingLevel(recipes.minBy { it.levelReq })
            } else {
                mesbox("You do not have the materials to make any of these items.")
            }
            return
        }
        openSkillMulti(
            SkillMultiConfig(
                verb = "make",
                actionType = SkillingActionType.MAKE,
                entries = candidates.map { it.toSkillMultiEntry() },
            )
        ) { selection ->
            val recipe =
                candidates.firstOrNull { it.output.internalName == selection.entry.internal }
                    ?: return@openSkillMulti
            val capped = minOf(selection.amount, maxCraftable(recipe))
            if (capped > 0 && hasCarvingTool()) {
                clearWeakQueue(FletchingParams.CARVE_QUEUE)
                anim(FletchingParams.CARVE_ANIM)
                fletchQueue(
                    FletchingParams.CARVE_QUEUE,
                    carveCadence(recipe, completedSoFar = 0),
                    FletchingTask(recipe, capped, completed = 0),
                )
            }
        }
    }

    private suspend fun ProtectedAccess.processCarve(task: FletchingTask) {
        val recipe = task.recipe
        if (!hasCarvingTool() || !meetsFletchingLevel(recipe) || !hasMaterials(recipe)) {
            resetAnim()
            return
        }

        if (!tryCraft(recipe)) {
            resetAnim()
            return
        }
        statAdvance(FLETCHING_STAT, recipe.xp)

        val completed = task.completed + 1
        if (completed < task.amount && hasMaterials(recipe)) {
            anim(FletchingParams.CARVE_ANIM)
            fletchQueue(
                FletchingParams.CARVE_QUEUE,
                carveCadence(recipe, completedSoFar = completed),
                FletchingTask(recipe, task.amount, completed),
            )
        } else {
            resetAnim()
        }
    }

    private val FletchingRecipe.carveTicks: Int
        get() = checkNotNull(ticks) { "Missing `ticks` column for carving row `$rowId`." }

    private val FletchingRecipe.isArrowShaft: Boolean
        get() = output.internalName in setOf("obj.arrow_shaft", "obj.seeking_arrow_shaft")

    private fun ProtectedAccess.hasCarvingTool(): Boolean =
        FletchingParams.KNIFE in inv || "obj.fletching_knife" in inv || "obj.fletching_knife" in worn

    private fun ProtectedAccess.carveCadence(recipe: FletchingRecipe, completedSoFar: Int): Int {
        val normal = if (recipe.isArrowShaft && completedSoFar > 0) {
            FletchingParams.ARROW_SHAFT_SUBSEQUENT_TICKS
        } else {
            recipe.carveTicks
        }
        if ("obj.fletching_knife" !in inv && "obj.fletching_knife" !in worn) return normal
        val first = checkNotNull(recipe.knifeTicks) { "Missing knife cadence for ${recipe.rowId}" }
        return first + (normal - recipe.carveTicks)
    }
}
