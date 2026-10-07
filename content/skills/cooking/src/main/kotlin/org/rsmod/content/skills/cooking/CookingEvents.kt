package org.rsmod.content.skills.cooking

import jakarta.inject.Inject
import org.rsmod.api.player.events.PlayerAction
import org.rsmod.api.player.events.PlayerActionCompleteEvent
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.cookingLvl
import org.rsmod.api.script.onOpContentLoc1
import org.rsmod.api.script.onOpContentMixedLocU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocCategory1
import org.rsmod.api.script.onOpLocCategoryU
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.table.FiremakingColoredLogsRow
import org.rsmod.content.skills.Material
import org.rsmod.content.skills.SkillMultiConfig
import org.rsmod.content.skills.SkillMultiEntry
import org.rsmod.content.skills.cooking.table.CookingBurnContext
import org.rsmod.content.skills.cooking.table.CookingFood
import org.rsmod.content.skills.openSkillMulti
import org.rsmod.content.skills.skillSuccess
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class CookingEvents @Inject constructor(private val xpMods: XpModifiers) : PluginScript() {

    private val COOKING_FIRE_CATEGORY = "category.cooking_fire"

    private val BURN_SYNTH = "synth.FRY"

    private val BURN_PHRASES =
        mapOf(
            "obj.cooked_meat" to "nicely cooked meat",
            "obj.cooked_chicken" to "nicely cooked chicken",
        )

    private val COOK_SUCCESS_MESSAGES =
        mapOf("obj.raw_chicken" to "You cook some chicken.", "obj.raw_beef" to "You cook a piece of meat.")

    private val BURN_FAIL_MESSAGES =
        mapOf(
            "obj.tbwt_raw_karambwanji" to "You accidentally burn the karambwanji to ashes.",
            "obj.raw_chicken" to "You accidentally burn the chicken.",
            "obj.raw_shrimp" to "You accidentally burn the shrimps.",
        )

    private val RANGE_ONLY_RAWS =
        setOf(
            "obj.potato",
            "obj.bread_dough",
            "obj.uncooked_redberry_pie",
            "obj.uncooked_meat_pie",
            "obj.uncooked_apple_pie",
            "obj.uncooked_pizza",
            "obj.uncooked_mud_pie",
            "obj.uncooked_garden_pie",
            "obj.uncooked_fish_pie",
            "obj.uncooked_botanical_pie",
            "obj.uncooked_mushroom_pie",
            "obj.uncooked_admiral_pie",
            "obj.uncooked_dragonfruit_pie",
            "obj.uncooked_wild_pie",
            "obj.uncooked_summer_pie",
            "obj.uncooked_cake",
            "obj.bowl_egg_raw",
        )

    private val SINEW_RAWS =
        setOf("obj.raw_beef", "obj.raw_bear_meat", "obj.raw_boar_meat", "obj.raw_impaler_meat")

    private val SINEW_OUTPUT = "obj.xbows_sinew"

    private val foods = CookingFood.all()

    private val burnableFoods =
        foods
            .filter { it.cooked.internalName != it.burnt.internalName }
            .distinctBy { it.cooked.internalName }
    private val coloredLogRows = FiremakingColoredLogsRow.all()

    private val fires = listOf("loc.fire") + coloredLogRows.map { it.fireObject.internalName }
    private val campfires =
        listOf("loc.forestry_fire") + coloredLogRows.map { it.campfireObject.internalName }

    enum class RangeType {
        STANDARD,
        LUMBRIDGE,
        HOSIDIUS,
    }

    private fun burnContext(surface: CookingSurface): CookingBurnContext =
        when (surface) {
            is CookingSurface.Fire -> CookingBurnContext.FIRE
            is CookingSurface.Range ->
                when (surface.rangeType) {
                    RangeType.STANDARD -> CookingBurnContext.RANGE
                    RangeType.LUMBRIDGE -> CookingBurnContext.RANGE_LUMBRIDGE
                    RangeType.HOSIDIUS -> CookingBurnContext.RANGE_HOSIDIUS
                }
        }

    private val rangeTypeByContent =
        mapOf(
            "content.cooking_range_standard" to RangeType.STANDARD,
            "content.cooking_range_lumbridge" to RangeType.LUMBRIDGE,
            "content.cooking_range_hosidius" to RangeType.HOSIDIUS,
        )

    private sealed class CookingSurface {
        data class Fire(val locInternal: String) : CookingSurface()

        data class Range(val rangeType: RangeType, val locInternal: String) : CookingSurface()
    }

    override fun ScriptContext.startup() {
        val fireAndCamp = fires + campfires
        foods.forEach { food ->
            if (food.raw.internalName in RANGE_ONLY_RAWS) {
                return@forEach
            }
            fireAndCamp.forEach { loc ->
                onOpLocU(loc, food.raw.internalName) {
                    promptCookFood(food, CookingSurface.Fire(loc))
                }
            }
        }
        rangeTypeByContent.forEach { (content, rangeType) ->
            foods.forEach { food ->
                onOpContentMixedLocU(content, food.raw.internalName) {
                    promptCookFood(food, CookingSurface.Range(rangeType, it.type.internalName))
                }
            }
        }

        foods.forEach { food ->
            if (food.raw.internalName in RANGE_ONLY_RAWS) {
                return@forEach
            }
            onOpLocCategoryU(COOKING_FIRE_CATEGORY, food.raw.internalName) {
                promptCookFood(food, CookingSurface.Fire(it.type.internalName))
            }
        }

        burnableFoods.forEach { food ->
            if (food.raw.internalName !in RANGE_ONLY_RAWS) {
                fireAndCamp.forEach { loc ->
                    onOpLocU(loc, food.cooked.internalName) {
                        burnFood(food, CookingSurface.Fire(loc))
                    }
                }
                onOpLocCategoryU(COOKING_FIRE_CATEGORY, food.cooked.internalName) {
                    burnFood(food, CookingSurface.Fire(it.type.internalName))
                }
            }
            rangeTypeByContent.forEach { (content, rangeType) ->
                onOpContentMixedLocU(content, food.cooked.internalName) {
                    burnFood(food, CookingSurface.Range(rangeType, it.type.internalName))
                }
            }
        }
        onOpLocCategory1(COOKING_FIRE_CATEGORY) {
            openCookingMenu(CookingSurface.Fire(it.type.internalName))
        }

        fires.forEach { loc ->
            onOpLoc1(loc) { openCookingMenu(CookingSurface.Fire(it.type.internalName)) }
        }
        rangeTypeByContent.forEach { (content, rangeType) ->
            onOpContentLoc1(content) {
                openCookingMenu(CookingSurface.Range(rangeType, it.type.internalName))
            }
        }

        onPlayerQueueWithArgs<CookTask>("queue.cooking_cook") { processCookTick(it.args) }
        onPlayerQueueWithArgs<SinewTask>("queue.cooking_sinew") { processSinewTick(it.args) }
    }

    private fun ProtectedAccess.isBurned(food: CookingFood, surface: CookingSurface): Boolean {
        if (hasCookingCape()) return false

        val gauntlets = food.gauntletAffected && "obj.gauntlets_of_cooking" in player.worn
        val curve = food.burnCurve(burnContext(surface), gauntlets)
        return !statRandom("stat.cooking", curve.low, curve.high, 0, curve.successBonus)
    }

    private fun ProtectedAccess.hasCookingCape(): Boolean {
        return "obj.skillcape_cooking" in player.worn ||
            "obj.skillcape_cooking_trimmed" in player.worn
    }

    private fun cookAnim(surface: CookingSurface) =
        when (surface) {
            is CookingSurface.Range -> "seq.human_cooking"
            is CookingSurface.Fire -> "seq.human_firecooking"
        }

    private suspend fun ProtectedAccess.openCookingMenu(surface: CookingSurface) {
        val cookable =
            foods.filter {
                inv.contains(it.raw.internalName) &&
                    player.cookingLvl >= it.level &&
                    (surface is CookingSurface.Range || it.raw.internalName !in RANGE_ONLY_RAWS)
            }

        if (cookable.isEmpty()) {
            mes(
                "You have nothing to cook on this ${if (surface is CookingSurface.Range) "range" else "fire"}."
            )
            return
        }

        if (cookable.size == 1 && inv.count(cookable.first().raw.internalName) == 1) {
            cookInstant(cookable.first(), surface)
            return
        }

        if (cookable.size == 1) {
            val food = cookable.first()
            openSkillMulti(
                SkillMultiConfig(
                    verb = "cook",
                    entries =
                        listOf(
                            SkillMultiEntry(
                                food.cooked.internalName,
                                listOf(Material(food.raw.internalName)),
                            )
                        ),
                )
            ) { selection ->
                cookFood(food, surface, selection.amount)
            }
            return
        }

        openSkillMulti(
            SkillMultiConfig(
                verb = "cook",
                entries =
                    cookable.map { food ->
                        SkillMultiEntry(
                            food.cooked.internalName,
                            listOf(Material(food.raw.internalName)),
                        )
                    },
            )
        ) { selection ->
            val food =
                cookable.firstOrNull { it.cooked.internalName == selection.entry.internal }
                    ?: return@openSkillMulti
            cookFood(food, surface, selection.amount)
        }
    }

    private suspend fun ProtectedAccess.promptCookFood(
        food: CookingFood,
        surface: CookingSurface,
    ) {
        // Always offer cook/dry for eligible meat on a range, even with only one item.
        if (surface is CookingSurface.Range && food.raw.internalName in SINEW_RAWS) {
            promptCookOrSinew(food, surface)
            return
        }

        if (player.cookingLvl < food.level) {
            mes("You need a Cooking level of ${food.level} to cook ${food.raw.name}.")
            return
        }

        if (inv.count(food.raw.internalName) <= 1) {
            cookInstant(food, surface)
            return
        }

        openSkillMulti(
            SkillMultiConfig(
                verb = "cook",
                entries =
                    listOf(
                        SkillMultiEntry(
                            food.cooked.internalName,
                            listOf(Material(food.raw.internalName)),
                        )
                    ),
            )
        ) { selection ->
            cookFood(food, surface, selection.amount)
        }
    }

    private suspend fun ProtectedAccess.promptCookOrSinew(
        food: CookingFood,
        surface: CookingSurface,
    ) {
        openSkillMulti(
            SkillMultiConfig(
                verb = "make",
                entries =
                    listOf(
                        SkillMultiEntry(
                            food.cooked.internalName,
                            listOf(Material(food.raw.internalName)),
                        ),
                        SkillMultiEntry(
                            SINEW_OUTPUT,
                            listOf(Material(food.raw.internalName)),
                        ),
                    ),
            )
        ) { selection ->
            if (selection.entry.internal == SINEW_OUTPUT) {
                drySinew(food, surface, selection.amount)
            } else {
                cookFood(food, surface, selection.amount)
            }
        }
    }

    private fun ProtectedAccess.burnFood(food: CookingFood, surface: CookingSurface) {
        anim(cookAnim(surface))
        if (!invDel(inv, food.cooked.internalName, 1).success) {
            return
        }
        if (!invAdd(inv, food.burnt.internalName, 1).success) {
            // Roll back consumption if the output cannot fit.
            invAdd(inv, food.cooked.internalName, 1)
            return
        }
        spam("You deliberately burn the ${burnPhrase(food)}.")
        soundSynth(BURN_SYNTH)
    }

    private fun burnPhrase(food: CookingFood): String =
        BURN_PHRASES[food.cooked.internalName] ?: food.cooked.name.lowercase()

    private fun cookSuccessMessage(food: CookingFood): String =
        COOK_SUCCESS_MESSAGES[food.raw.internalName] ?: "You successfully cook the ${food.cookingNoun()}."

    private fun cookBurnMessage(food: CookingFood): String =
        BURN_FAIL_MESSAGES[food.raw.internalName] ?: "You accidentally burn the ${food.cookingNoun()}."

    private fun CookingFood.cookingNoun(): String = raw.name.lowercase().removePrefix("raw ")

    private fun ProtectedAccess.cookInstant(food: CookingFood, surface: CookingSurface): Boolean {
        anim(cookAnim(surface))
        return applyCook(food, surface)
    }

    private fun ProtectedAccess.cookFood(
        food: CookingFood,
        surface: CookingSurface,
        amount: Int = 1,
    ) {
        if (player.cookingLvl < food.level) {
            mes("You need a Cooking level of ${food.level} to cook ${food.raw.name}.")
            return
        }
        // Replace the existing production chain when a new batch is confirmed.
        clearWeakQueue("queue.cooking_cook")
        // Make-X waits one tick for item 1, three for item 2, then four per item.
        weakQueue("queue.cooking_cook", 2, CookTask(food, surface, amount, 0))
    }

    private fun ProtectedAccess.processCookTick(task: CookTask) {
        val food = task.food

        if (!inv.contains(food.raw.internalName)) {
            resetAnim()
            return
        }

        if (player.cookingLvl < food.level) {
            resetAnim()
            mes("You need a Cooking level of ${food.level} to cook ${food.raw.name}.")
            return
        }

        if (!applyCook(food, task.surface)) {
            resetAnim()
            return
        }

        anim(cookAnim(task.surface))

        val cooked = task.cooked + 1
        if (cooked < task.amount && inv.contains(food.raw.internalName)) {
            // Queue rescheduling needs +1 because newly added queues decrement on this tick.
            val cadenceTicks = if (cooked == 1) 3 else 4
            weakQueue(
                "queue.cooking_cook",
                cadenceTicks + 1,
                CookTask(food, task.surface, task.amount, cooked),
            )
        }
    }

    private fun ProtectedAccess.applyCook(food: CookingFood, surface: CookingSurface): Boolean {
        val burned = isBurned(food, surface)
        if (invDel(inv, food.raw.internalName, 1).failure) {
            return false
        }

        if (burned) {
            if (invAdd(inv, food.burnt.internalName, 1).failure) {
                invAdd(inv, food.raw.internalName, 1)
                mes("You don't have enough inventory space.")
                return false
            }
            spam(cookBurnMessage(food))
            soundSynth(BURN_SYNTH)
        } else {
            if (invAdd(inv, food.cooked.internalName, food.outputAmount).failure) {
                invAdd(inv, food.raw.internalName, 1)
                mes("You don't have enough inventory space.")
                return false
            }
            spam(cookSuccessMessage(food))
            soundSynth(BURN_SYNTH)
            val xpModifier = xpMods.get(player, "stat.cooking")
            // Use the exact tenths-derived XP, preserving fractional awards.
            statAdvance("stat.cooking", food.xp * xpModifier)
            if (surface is CookingSurface.Range && surface.rangeType == RangeType.LUMBRIDGE) {
                publish(PlayerActionCompleteEvent(player, PlayerAction.CookFood, food.cooked.internalName))
            }
        }
        return true
    }

    private fun ProtectedAccess.drySinew(
        food: CookingFood,
        surface: CookingSurface,
        amount: Int = 1,
    ) {
        anim(cookAnim(surface))
        if (!applySinew(food)) {
            return
        }
        if (amount <= 1 || !inv.contains(food.raw.internalName)) {
            return
        }
        // The first repeat is three ticks, then four; callback scheduling requires +1.
        weakQueue("queue.cooking_sinew", 5, SinewTask(food, surface, amount, 1))
    }

    private fun ProtectedAccess.processSinewTick(task: SinewTask) {
        if (!inv.contains(task.food.raw.internalName)) return

        anim(cookAnim(task.surface))
        if (!applySinew(task.food)) {
            return
        }

        val made = task.made + 1
        if (made < task.amount && inv.contains(task.food.raw.internalName)) {
            weakQueue("queue.cooking_sinew", 5, SinewTask(task.food, task.surface, task.amount, made))
        }
    }

    private fun ProtectedAccess.applySinew(food: CookingFood): Boolean {
        val dried = player.cookingLvl >= 34 || skillSuccess(128, 512, player.cookingLvl)
        if (invDel(inv, food.raw.internalName, 1).failure) {
            return false
        }
        if (dried) {
            if (invAdd(inv, SINEW_OUTPUT, 1).failure) {
                invAdd(inv, food.raw.internalName, 1)
                mes("You don't have enough inventory space.")
                return false
            }
            val xpModifier = xpMods.get(player, "stat.cooking")
            statAdvance("stat.cooking", 3.0 * xpModifier)
            mes("You dry the meat into sinew.")
        } else {
            if (invAdd(inv, food.burnt.internalName, 1).failure) {
                invAdd(inv, food.raw.internalName, 1)
                mes("You don't have enough inventory space.")
                return false
            }
            mes(cookBurnMessage(food))
        }
        return true
    }

    private data class CookTask(
        val food: CookingFood,
        val surface: CookingSurface,
        val amount: Int,
        val cooked: Int,
    )

    private data class SinewTask(
        val food: CookingFood,
        val surface: CookingSurface,
        val amount: Int,
        val made: Int,
    )
}
