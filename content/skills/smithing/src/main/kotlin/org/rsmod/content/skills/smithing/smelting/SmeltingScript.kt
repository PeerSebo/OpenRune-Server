package org.rsmod.content.skills.smithing.smelting

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocCategory2
import org.rsmod.api.script.onOpLocCategoryU
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.Material
import org.rsmod.content.skills.SkillMultiConfig
import org.rsmod.content.skills.SkillMultiEntry
import org.rsmod.content.skills.SkillingActionType
import org.rsmod.content.skills.crafting.interfaces.hasGoldCraftingBars
import org.rsmod.content.skills.crafting.interfaces.hasSilverCraftingBars
import org.rsmod.content.skills.crafting.interfaces.openGoldCrafting
import org.rsmod.content.skills.crafting.interfaces.openSilverCrafting
import org.rsmod.content.skills.openSkillMulti
import org.rsmod.content.skills.smithing.RingOfForging
import org.rsmod.content.skills.smithing.configs.SmithingParams
import org.rsmod.content.skills.smithing.hasCannonballFurnaceMould
import org.rsmod.content.skills.smithing.openCannonballFurnaceMenu
import org.rsmod.content.skills.smithing.smithQueue
import org.rsmod.content.skills.smithing.table.SmithingBar
import org.rsmod.content.skills.smithing.tryProduce
import org.rsmod.content.skills.smithing.util.SmithingBonuses
import org.rsmod.content.skills.smithing.util.SmithingBonuses.effectiveCoalAmount
import org.rsmod.content.skills.smithing.util.SmithingBonuses.isBarEligibleForVarrockArmour
import org.rsmod.content.skills.smithing.util.SmithingBonuses.isEdgevilleFurnace
import org.rsmod.content.skills.smithing.util.SmithingBonuses.rollVarrockDoubleBar
import org.rsmod.content.skills.smithing.util.SmithingBonuses.varrockArmourTier
import org.rsmod.content.skills.smithing.util.SmithingUtils
import org.rsmod.game.inv.Inventory
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SmeltingScript @Inject constructor(private val xpMods: XpModifiers) : PluginScript() {

    private val allBars = SmithingBar.all()

    // Elemental Workshop smelting needs its quest-specific furnace flow.
    private val standardExclusions = setOf("obj.lovakite_bar", "obj.elemental_workshop_bar")
    private val normalBars = allBars.filter { it.output.internalName !in standardExclusions }
    private val barsByOutput = allBars.associateBy { it.output.internalName }

    override fun ScriptContext.startup() {
        onOpLocCategory2("category.furnace") {
            val locInternal = it.type.internalName
            val coords = it.loc.coords
            // TODO: Verify menu priority when both bars and cannonballs are available.
            if (hasBarSmeltMaterials()) {
                openStandardSmeltMenu(locInternal, coords)
            } else if (hasCannonballFurnaceMould()) {
                openCannonballFurnaceMenu(locInternal)
            } else if (hasGoldCraftingBars()) {
                openGoldCrafting()
            } else if (hasSilverCraftingBars()) {
                openSilverCrafting()
            }
        }

        val standardInputs =
            normalBars.flatMap { bar -> bar.input.map { it.internalName } }.distinct()
        standardInputs.forEach { input ->
            onOpLocCategoryU("category.furnace", input) {
                openStandardSmeltMenu(it.type.internalName, it.loc.coords)
            }
        }

        onOpLoc1("loc.lovakengj_furnace_large_01") { openLovakiteSmeltMenu() }

        // The Lovakengj furnace lacks the generic furnace category and needs a direct binding.
        listOf("obj.lovakite_ore", "obj.coal").forEach { input ->
            onOpLocU("loc.lovakengj_furnace_large_01", input) { openLovakiteSmeltMenu() }
        }

        onPlayerQueueWithArgs<SmeltTask>(SmithingParams.SMELT_QUEUE) { processSmeltTask(it.args) }
    }

    private fun ProtectedAccess.hasBarSmeltMaterials(): Boolean =
        normalBars.any { hasItemsForBar(it) }

    private suspend fun ProtectedAccess.openStandardSmeltMenu(
        furnaceLocInternal: String,
        furnaceCoords: org.rsmod.map.CoordGrid,
    ) {
        val smeltable = normalBars.filter { hasItemsForBar(it) }
        if (smeltable.isEmpty()) {
            return
        }

        openSkillMulti(
            SkillMultiConfig(
                verb = "smelt",
                actionType = SkillingActionType.SMELT,
                entries = smeltable.map { smeltEntry(it) },
                maxCountProvider = { inventory, entry ->
                    barsByOutput[entry.internal]?.let { maxSmeltCount(inventory, it) } ?: 0
                },
            )
        ) { selection ->
            val bar = barsByOutput[selection.entry.internal] ?: return@openSkillMulti
            startSmelting(bar, selection.amount, furnaceLocInternal, furnaceCoords)
        }
    }

    private suspend fun ProtectedAccess.openLovakiteSmeltMenu() {
        val bar = barsByOutput["obj.lovakite_bar"] ?: return
        if (!hasItemsForBar(bar)) {
            return
        }

        openSkillMulti(
            SkillMultiConfig(
                verb = "smelt",
                actionType = SkillingActionType.SMELT,
                entries = listOf(smeltEntry(bar)),
                maxCountProvider = { inventory, _ -> maxSmeltCount(inventory, bar) },
            )
        ) { selection ->
            startSmelting(
                bar,
                selection.amount,
                furnaceLocInternal = "",
                furnaceCoords = player.coords,
            )
        }
    }

    private fun ProtectedAccess.smeltEntry(bar: SmithingBar): SkillMultiEntry {
        val materials = buildList {
            add(Material(bar.input.first().internalName, bar.inputAmount.first()))
            val secondary = bar.input.getOrNull(1)
            val secondaryAmt = bar.input.getOrNull(1)?.let { bar.inputAmount.getOrNull(1) }
            if (secondary != null && secondaryAmt != null && secondaryAmt > 0) {
                add(Material(secondary.internalName, effectiveCoalAmount(inv, bar)))
            }
        }
        return SkillMultiEntry(bar.output.internalName, materials)
    }

    private suspend fun ProtectedAccess.startSmelting(
        bar: SmithingBar,
        requestedAmount: Int,
        furnaceLocInternal: String,
        furnaceCoords: org.rsmod.map.CoordGrid,
    ) {
        val amount = minOf(requestedAmount, maxSmeltCount(inv, bar))
        if (amount <= 0) {
            return
        }

        if (!canSmelt(bar)) {
            return
        }

        clearWeakQueue(SmithingParams.SMELT_QUEUE)
        scheduleSmeltCycle(
            SmeltTask(
                bar,
                amount,
                completed = 0,
                isSuperHeat = false,
                furnaceLocInternal = furnaceLocInternal,
                furnaceCoords = furnaceCoords,
            )
        )
    }

    private fun ProtectedAccess.scheduleSmeltCycle(task: SmeltTask) {
        anim(SmithingParams.FURNACE_ANIM)
        soundSynth(SmithingParams.FURNACE_SOUND)
        smithQueue(SmithingParams.SMELT_QUEUE, SmithingParams.SMELT_TICKS, task)
    }

    private suspend fun ProtectedAccess.processSmeltTask(task: SmeltTask) {
        if (!canSmelt(task.bar)) {
            resetAnim()
            return
        }

        val processed = performSmelt(task)
        if (processed <= 0) {
            resetAnim()
            return
        }

        val completed = task.completed + processed
        if (completed < task.amount && hasItemsForBar(task.bar)) {
            scheduleSmeltCycle(task.copy(completed = completed))
        } else {
            resetAnim()
        }
    }

    private fun ProtectedAccess.performSmelt(task: SmeltTask): Int {
        val bar = task.bar
        val primary = bar.input.first()
        val primaryAmt = bar.inputAmount.first()
        val secondary = bar.input.getOrNull(1)
        val requiresSecondary = secondary != null && (bar.inputAmount.getOrNull(1) ?: 0) > 0
        val fullCoalAmt = if (requiresSecondary) bar.inputAmount[1] else 0
        val halfCoalAmt = SmithingBonuses.halfCoalAmount(bar)
        val catalysts = if (requiresSecondary) inv.count(SmithingBonuses.SMITHING_CATALYST) else 0

        // Catalysts apply per bar; bars beyond the carried catalyst supply still cost full coal.
        fun coalCost(sets: Int): Int {
            if (!requiresSecondary) {
                return 0
            }
            val boosted = minOf(sets, catalysts)
            return boosted * halfCoalAmt + (sets - boosted) * fullCoalAmt
        }

        // Varrock procs consume and reward two ore sets; they do not create a free bar.
        val sets = if (canVarrockDoubleSmelt(task, coalCost(2))) 2 else 1

        // Double-smelt iron rolls independently per ore; this interaction is unverified.
        val isIronBar = bar.output.internalName == "obj.iron_bar"
        val ringWorn = RingOfForging.isWearing(player)
        val successes =
            if (!isIronBar || ringWorn || task.isSuperHeat) {
                sets
            } else {
                (0 until sets).count { random.randomBoolean(2) }
            }

        if (successes == 0) {
            // A failed smelt still consumes the ore (iron only - it has no secondary input).
            invDel(inv, primary.internalName, primaryAmt * sets)
            mes("The ore is too impure and you fail to refine it.")
            return sets
        }

        val inputs = buildList {
            add(primary.internalName to primaryAmt * sets)
            if (requiresSecondary) {
                add(secondary.internalName to coalCost(sets))
            }
        }
        if (!tryProduce(inputs, bar.output.internalName, successes)) {
            return 0
        }

        val consumedCatalysts = minOf(sets, catalysts)
        if (consumedCatalysts > 0) {
            invDel(inv, SmithingBonuses.SMITHING_CATALYST, consumedCatalysts)
        }

        if (isIronBar && ringWorn) {
            RingOfForging.consumeCharges(player, sets)
        }

        val xpPerBar = SmithingSmeltXp.resolve(player, bar, task.isSuperHeat, xpMods)
        val boostedBars = minOf(successes, consumedCatalysts)
        statAdvance("stat.smithing", xpPerBar * (successes + boostedBars))

        mes("You retrieve a bar of ${metalName(bar)}.")
        if (successes < sets) {
            mes("The ore is too impure and you fail to refine it.")
        }
        if (sets > 1) {
            val barName = SmithingUtils.itemName(bar.output, "bar").lowercase()
            mes("Your Varrock armour helps you smelt an extra $barName.")
        }
        return sets
    }

    private fun ProtectedAccess.canVarrockDoubleSmelt(
        task: SmeltTask,
        coalCostForTwo: Int,
    ): Boolean {
        val bar = task.bar
        if (!isEdgevilleFurnace(task.furnaceLocInternal, task.furnaceCoords)) {
            return false
        }
        val tier = player.varrockArmourTier() ?: return false
        if (!isBarEligibleForVarrockArmour(bar, tier)) {
            return false
        }
        if (inv.count(bar.input.first().internalName) < bar.inputAmount.first() * 2) {
            return false
        }
        val secondary = bar.input.getOrNull(1)
        val requiresSecondary = secondary != null && (bar.inputAmount.getOrNull(1) ?: 0) > 0
        if (requiresSecondary && inv.count(secondary.internalName) < coalCostForTwo) {
            return false
        }
        return rollVarrockDoubleBar(random)
    }

    private fun metalName(bar: SmithingBar): String =
        SmithingUtils.itemName(bar.output, "metal").lowercase().removeSuffix(" bar")

    // Read the quest varp directly to avoid a smithing-to-quest dependency cycle.
    private fun ProtectedAccess.completedKnightsSword(): Boolean =
        player.vars[KnightsSwordVarp] >= KnightsSwordComplete

    private suspend fun ProtectedAccess.canSmelt(bar: SmithingBar): Boolean {
        if (bar.output.internalName == "obj.blurite_bar" && !completedKnightsSword()) {
            mesbox("You must complete The Knight's Sword before you can smelt blurite.")
            return false
        }

        val primaryAmt = bar.inputAmount.first()
        val secondary = bar.input.getOrNull(1)
        val secondaryAmt = effectiveCoalAmount(inv, bar)
        val primaryName = SmithingUtils.itemName(bar.input.first(), "ore")

        val hasPrimary = inv.count(bar.input.first().internalName) >= primaryAmt
        val requiresSecondary = secondary != null && (bar.inputAmount.getOrNull(1) ?: 0) > 0
        val hasSecondary = !requiresSecondary || inv.count(secondary.internalName) >= secondaryAmt

        if (!hasPrimary || !hasSecondary) {
            val message =
                if (!requiresSecondary) {
                    "You don't have ${SmithingUtils.countLiteral(primaryAmt)} $primaryName to smelt."
                } else {
                    val secondaryName = SmithingUtils.itemName(secondary, "materials")
                    val barName = SmithingUtils.itemName(bar.output, "bar")
                    "You need ${SmithingUtils.countLiteral(primaryAmt)} $primaryName and " +
                        "${SmithingUtils.countLiteral(secondaryAmt)} $secondaryName to make " +
                        "${SmithingUtils.prefixAn(barName)}."
                }
            mesbox(message)
            return false
        }

        return SmithingUtils.requireSmithingLevel(this, bar.levelReq, "smelt $primaryName")
    }

    private fun ProtectedAccess.hasItemsForBar(bar: SmithingBar): Boolean {
        val primaryAmt = bar.inputAmount.first()
        val secondaryAmt = effectiveCoalAmount(inv, bar)
        val secondary = bar.input.getOrNull(1)
        val hasPrimary = inv.count(bar.input.first().internalName) >= primaryAmt
        val requiresSecondary = secondary != null && (bar.inputAmount.getOrNull(1) ?: 0) > 0
        val hasSecondary = !requiresSecondary || inv.count(secondary.internalName) >= secondaryAmt
        return hasPrimary && hasSecondary
    }

    private fun maxSmeltCount(inventory: Inventory, bar: SmithingBar): Int {
        val primaryAmt = bar.inputAmount.first()
        val effectiveSecondaryAmt = effectiveCoalAmount(inventory, bar)
        val secondary = bar.input.getOrNull(1)
        val requiresSecondary = secondary != null && (bar.inputAmount.getOrNull(1) ?: 0) > 0

        val primaryCount = inventory.count(bar.input.first().internalName)
        val secondaryCount =
            if (requiresSecondary) {
                inventory.count(secondary.internalName)
            } else {
                Int.MAX_VALUE
            }

        return minOf(
            primaryCount / primaryAmt,
            if (requiresSecondary) secondaryCount / effectiveSecondaryAmt else Int.MAX_VALUE,
        )
    }

    private data class SmeltTask(
        val bar: SmithingBar,
        val amount: Int,
        val completed: Int,
        val isSuperHeat: Boolean,
        val furnaceLocInternal: String,
        val furnaceCoords: org.rsmod.map.CoordGrid,
    )

    private companion object {
        private const val KnightsSwordVarp = "varp.squire"

        private const val KnightsSwordComplete = 7
    }
}
