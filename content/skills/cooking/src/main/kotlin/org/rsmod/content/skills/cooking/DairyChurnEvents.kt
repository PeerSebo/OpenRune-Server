package org.rsmod.content.skills.cooking

import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.cookingLvl
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpLocCategory1
import org.rsmod.api.script.onOpLocCategoryU
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.Material
import org.rsmod.content.skills.SkillMultiConfig
import org.rsmod.content.skills.SkillMultiEntry
import org.rsmod.content.skills.openSkillMulti
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DairyChurnEvents @Inject constructor(
    private val xpMods: XpModifiers,
    private val objRepo: ObjRepository,
    private val worldRepo: WorldRepository,
) : PluginScript() {

    private data class ChurnRecipe(
        val input: String,
        val product: String,
        val level: Int,
        val xp: Double,
        // TODO: verify churn cadence; published recipe ticks and sequence lengths disagree.
        val ticks: Int,
        val returnsBucket: Boolean,
    )

    private val MILK = "obj.bucket_milk"
    private val CREAM = "obj.pot_of_cream"
    private val BUTTER = "obj.pot_of_butter"
    private val CHEESE = "obj.cheese"

    private val recipesByProduct: Map<String, List<ChurnRecipe>> =
        mapOf(
            CREAM to listOf(ChurnRecipe(MILK, CREAM, 21, 18.0, 10, returnsBucket = true)),
            BUTTER to
                listOf(
                    ChurnRecipe(CREAM, BUTTER, 38, 22.5, 10, returnsBucket = false),
                    ChurnRecipe(MILK, BUTTER, 38, 40.5, 19, returnsBucket = true),
                ),
            CHEESE to
                listOf(
                    ChurnRecipe(BUTTER, CHEESE, 48, 23.5, 10, returnsBucket = false),
                    ChurnRecipe(CREAM, CHEESE, 48, 46.0, 19, returnsBucket = false),
                    ChurnRecipe(MILK, CHEESE, 48, 64.0, 26, returnsBucket = true),
                ),
        )

    override fun ScriptContext.startup() {
        onOpLocCategory1("category.dairy_churn") { promptChurn(it.loc, fixedInput = null) }
        listOf(MILK, CREAM, BUTTER).forEach { input ->
            onOpLocCategoryU("category.dairy_churn", input) {
                promptChurn(it.loc, fixedInput = input)
            }
        }
        onPlayerQueueWithArgs<ChurnTask>("queue.cooking_churn") { processChurnTick(it.args) }
    }

    private fun recipesFor(product: String, fixedInput: String?): List<ChurnRecipe> =
        recipesByProduct.getValue(product).filter { fixedInput == null || it.input == fixedInput }

    private fun maxProducible(inv: Inventory, product: String, fixedInput: String?): Int =
        recipesFor(product, fixedInput).sumOf { inv.count(it.input) }

    private suspend fun ProtectedAccess.promptChurn(churn: BoundLocInfo, fixedInput: String?) {
        val products = listOf(CREAM, BUTTER, CHEESE)
        val withInputs =
            products.filter { product ->
                recipesFor(product, fixedInput).any { inv.contains(it.input) }
            }
        if (withInputs.isEmpty()) {
            mes("You have nothing that can be churned.")
            return
        }

        val makeable =
            withInputs.filter { product ->
                player.cookingLvl >= recipesByProduct.getValue(product).first().level
            }
        if (makeable.isEmpty()) {
            val required = withInputs.minOf { recipesByProduct.getValue(it).first().level }
            mes("You need a Cooking level of $required to churn that.")
            return
        }

        openSkillMulti(
            SkillMultiConfig(
                verb = "make",
                entries =
                    makeable.map { product ->
                        val shown =
                            recipesFor(product, fixedInput).firstOrNull { inv.contains(it.input) }
                                ?: recipesFor(product, fixedInput).first()
                        SkillMultiEntry(product, listOf(Material(shown.input)))
                    },
                maxCountProvider = { inventory, entry ->
                    maxProducible(inventory, entry.internal, fixedInput)
                },
            )
        ) { selection ->
            startChurn(churn, selection.entry.internal, fixedInput, selection.amount)
        }
    }

    private fun ProtectedAccess.startChurn(
        churn: BoundLocInfo,
        product: String,
        fixedInput: String?,
        amount: Int,
    ) {
        val recipe = nextRecipe(product, fixedInput) ?: return
        clearWeakQueue("queue.cooking_churn")
        playChurnCycle(churn, recipe)
        // Skillmulti callbacks add queues before this tick's decrement; compensate with +1.
        weakQueue(
            "queue.cooking_churn",
            recipe.ticks + 1,
            ChurnTask(churn, product, fixedInput, amount, 0),
        )
    }

    private fun ProtectedAccess.playChurnCycle(churn: BoundLocInfo, recipe: ChurnRecipe) {
        anim(churnAnim(recipe))
        locAnim(worldRepo, churn, locChurnAnim(recipe))
    }

    private fun ProtectedAccess.processChurnTick(task: ChurnTask) {
        val recipe = nextRecipe(task.product, task.fixedInput)
        if (recipe == null || player.cookingLvl < recipe.level) {
            resetAnim()
            return
        }

        if (recipe.returnsBucket) {
            // Milk returns an empty bucket; its additional product goes to the floor when inventory is full.
            if (invReplace(inv, recipe.input, 1, "obj.bucket_empty").failure) {
                resetAnim()
                return
            }
            if (invAdd(inv, recipe.product, 1).failure) {
                val duration = player.lootDropDuration ?: constants.lootdrop_duration
                objRepo.add(recipe.product, player.coords, duration, receiver = player)
            }
        } else {
            if (invReplace(inv, recipe.input, 1, recipe.product).failure) {
                resetAnim()
                return
            }
        }

        val xpModifier = xpMods.get(player, "stat.cooking")
        statAdvance("stat.cooking", recipe.xp * xpModifier)
        mes(
            when (recipe.product) {
                CREAM -> "You make a pot of cream."
                BUTTER -> "You make a pat of butter."
                else -> "You make some cheese."
            }
        )

        val made = task.made + 1
        if (made < task.amount) {
            val next = nextRecipe(task.product, task.fixedInput)
            if (next != null) {
                playChurnCycle(task.churn, next)
                weakQueue(
                    "queue.cooking_churn",
                    next.ticks + 1,
                    ChurnTask(task.churn, task.product, task.fixedInput, task.amount, made),
                )
                return
            }
        }
        // LocAnim has no cancel packet; the churn stops through maxLoops.
        resetAnim()
    }

    private fun ProtectedAccess.nextRecipe(product: String, fixedInput: String?): ChurnRecipe? =
        recipesFor(product, fixedInput).firstOrNull { inv.contains(it.input) }

    private fun churnAnim(recipe: ChurnRecipe): String =
        when (recipe.ticks) {
            10 -> "seq.player_churns_milk_short"
            19 -> "seq.player_churns_milk_medium"
            else -> "seq.player_churns_milk_long"
        }

    private fun locChurnAnim(recipe: ChurnRecipe): String =
        when (recipe.ticks) {
            10 -> "seq.churn_milk_short"
            19 -> "seq.churn_milk_medium"
            else -> "seq.churn_milk_long"
        }

    private data class ChurnTask(
        val churn: BoundLocInfo,
        val product: String,
        val fixedInput: String?,
        val amount: Int,
        val made: Int,
    )
}
