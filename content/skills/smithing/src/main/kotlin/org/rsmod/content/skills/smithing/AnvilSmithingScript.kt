package org.rsmod.content.skills.smithing

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.smithingLvl
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpLocCategory1
import org.rsmod.api.script.onOpLocCategoryU
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.smithing.configs.SmithingParams
import org.rsmod.content.skills.smithing.table.SmithingBar
import org.rsmod.content.skills.smithing.util.SmithingData
import org.rsmod.content.skills.smithing.util.SmithingProductMeta
import org.rsmod.content.skills.smithing.util.SmithingUtils
import org.rsmod.content.skills.smithing.util.SmithingUtils.hasHammer
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class AnvilSmithingScript @Inject constructor(private val xpMods: XpModifiers) : PluginScript() {

    private val hammers = setOf("obj.hammer", "obj.imcando_hammer", "obj.imcando_hammer_offhand")

    // Doric's anvil type is reused elsewhere; gate only his hut coordinates.
    private fun ProtectedAccess.isDoricsAnvil(coords: CoordGrid): Boolean = coords in DoricsAnvils

    private fun ProtectedAccess.completedDoricsQuest(): Boolean =
        player.vars[DoricsQuestVarp] >= DoricsQuestComplete

    private fun ProtectedAccess.blockedByDorics(coords: CoordGrid): Boolean {
        if (isDoricsAnvil(coords) && !completedDoricsQuest()) {
            mes(DoricsRefusal)
            return true
        }
        return false
    }

    private var ProtectedAccess.smithingBarType by intVarBit(SmithingData.SMITHING_BAR_TYPE_VARBIT)

    override fun ScriptContext.startup() {
        hammers.forEach { hammer ->
            onOpLocCategoryU(SmithingData.ANVIL_CATEGORY, hammer) {
                mes(
                    "To smith a metal bar, click on the anvil while you have the bar in your inventory."
                )
            }
        }

        SmithingData.barOutputInternals.forEach { barInternal ->
            onOpLocCategoryU(SmithingData.ANVIL_CATEGORY, barInternal) {
                if (blockedByDorics(it.loc.coords)) {
                    return@onOpLocCategoryU
                }
                SmithingData.barsByOutput[barInternal]?.let { queueOpenSmithing(it) }
            }
        }

        onOpLocCategory1(SmithingData.ANVIL_CATEGORY) {
            if (blockedByDorics(it.loc.coords)) {
                return@onOpLocCategory1
            }
            val bar = selectedBar()
            if (bar == null) {
                mesbox("You should select an item from your inventory and use it on the anvil.")
                return@onOpLocCategory1
            }
            queueOpenSmithing(bar)
        }

        SmithingData.smithingButtonComponents.forEach { component ->
            onIfModalButton(component) { handleSmithingInterfaceClick(it.component.packed) }
        }

        // TODO: Identify the quantity-selection varbit so the client can highlight the selected button.
        onIfModalButton("component.smithing:make_1") { selectQuantity(1) }
        onIfModalButton("component.smithing:make_5") { selectQuantity(5) }
        onIfModalButton("component.smithing:make_10") { selectQuantity(10) }
        onIfModalButton("component.smithing:make_all") { selectQuantity(QUANTITY_ALL) }
        onIfModalButton("component.smithing:make_x") { selectCustomQuantity() }

        onPlayerQueueWithArgs<OpenAnvilTask>("queue.smithing_anvil_open") {
            openAnvilSmithing(it.args.barInternal)
        }

        onPlayerQueueWithArgs<AnvilSmithTask>(SmithingParams.ANVIL_QUEUE) {
            processSmithTask(it.args)
        }
    }

    private fun ProtectedAccess.queueOpenSmithing(bar: SmithingBar) {
        weakQueue("queue.smithing_anvil_open", 1, OpenAnvilTask(bar.output.internalName))
    }

    private suspend fun ProtectedAccess.openAnvilSmithing(barInternal: String) {
        val bar = SmithingData.barsByOutput[barInternal] ?: return
        if (!canSmithBar(bar)) {
            return
        }
        openSmithingInterface(bar)
    }

    private suspend fun ProtectedAccess.handleSmithingInterfaceClick(componentPacked: Int) {
        val bar = SmithingData.barAtIndex(smithingBarType) ?: return
        val componentString = RSCM.getReverseMapping(RSCMType.COMPONENT, componentPacked)
        val itemName = SmithingData.typeForChild(componentString, bar) ?: return
        val meta = SmithingData.metaForName(bar, itemName) ?: return

        ifCloseSub(SmithingData.SMITHING_INTERFACE)
        val quantity = SmithingState.quantity(player)
        startSmithing(meta, amount = if (quantity == QUANTITY_ALL) Int.MAX_VALUE else quantity)
    }

    private fun ProtectedAccess.selectQuantity(quantity: Int) {
        SmithingState.setQuantity(player, quantity)
    }

    // TODO: Verify whether the quantity prompt should leave the smithing interface open.
    private suspend fun ProtectedAccess.selectCustomQuantity() {
        val count = countDialog()
        if (count > 0) {
            SmithingState.setQuantity(player, count)
        }
    }

    private suspend fun ProtectedAccess.canSmithBar(bar: SmithingBar): Boolean {
        if (!hasHammer()) {
            mes("You need a hammer to work metal with an anvil.")
            return false
        }
        val barName = SmithingUtils.itemName(bar.output, "bar").lowercase()
        return SmithingUtils.requireSmithingLevel(this, bar.levelReq, "work $barName")
    }

    private suspend fun ProtectedAccess.openSmithingInterface(bar: SmithingBar) {
        SmithingData.barIndexFor(bar)?.let { smithingBarType = it }
        ifOpenMainModal(SmithingData.SMITHING_INTERFACE)
    }

    private fun ProtectedAccess.selectedBar(): SmithingBar? {
        val smithingLevel = player.smithingLvl

        SmithingData.barAtIndex(smithingBarType)
            ?.takeIf { inv.contains(it.output.internalName) && smithingLevel >= it.levelReq }
            ?.let {
                return it
            }

        val bestBar =
            SmithingData.allBars
                .filter { inv.contains(it.output.internalName) && smithingLevel >= it.levelReq }
                .maxByOrNull { it.levelReq }

        if (bestBar != null) {
            SmithingData.barIndexFor(bestBar)?.let { smithingBarType = it }
        }

        return bestBar
    }

    private suspend fun ProtectedAccess.startSmithing(meta: SmithingProductMeta, amount: Int) {
        if (!canSmithProduct(meta)) {
            return
        }
        val maxAmount = inv.count(meta.bar.output.internalName) / meta.barCount
        val craftAmount = amount.coerceAtMost(maxAmount)
        if (craftAmount <= 0) {
            return
        }
        // Re-confirming replaces a still-running chain instead of stacking a second one.
        clearWeakQueue(SmithingParams.ANVIL_QUEUE)
        scheduleSmithCycle(AnvilSmithTask(meta, craftAmount, completed = 0))
    }

    private fun ProtectedAccess.scheduleSmithCycle(task: AnvilSmithTask) {
        // Anvil strikes are baked into the animation; an extra synth would double them.
        anim(SmithingParams.ANVIL_ANIM)
        smithQueue(SmithingParams.ANVIL_QUEUE, anvilCadenceTicks(), task)
    }

    private suspend fun ProtectedAccess.processSmithTask(task: AnvilSmithTask) {
        val meta = task.meta
        if (!canSmithProduct(meta)) {
            resetAnim()
            return
        }

        if (!trySmith(meta)) {
            resetAnim()
            return
        }

        val xp = meta.barCount * meta.bar.smithXp * xpMods.get(player, "stat.smithing")
        statAdvance("stat.smithing", xp)

        val completed = task.completed + 1
        if (completed < task.amount && hasBarsFor(meta)) {
            scheduleSmithCycle(task.copy(completed = completed))
        } else {
            // Stop the looping animation after the final production cadence.
            resetAnim()
        }
    }

    // Inputs free enough slots for every recipe; a pre-delete free-space check would reject valid batches.
    private fun ProtectedAccess.trySmith(meta: SmithingProductMeta): Boolean =
        tryProduce(
            inputs = listOf(meta.bar.output.internalName to meta.barCount),
            output = meta.product.internalName,
            outputCount = meta.numProduced,
        )

    private fun ProtectedAccess.hasBarsFor(meta: SmithingProductMeta): Boolean =
        inv.count(meta.bar.output.internalName) >= meta.barCount

    private suspend fun ProtectedAccess.canSmithProduct(meta: SmithingProductMeta): Boolean {
        if (!canSmithBar(meta.bar)) {
            return false
        }

        if (
            !SmithingUtils.requireSmithingLevel(
                this,
                meta.level,
                "make ${SmithingUtils.prefixAn(meta.name)}",
            )
        ) {
            return false
        }

        if (meta.barCount > inv.count(meta.bar.output.internalName)) {
            val barName = SmithingUtils.itemName(meta.bar.output, "bar").lowercase()
            mesbox(
                "You don't have enough ${SmithingUtils.pluralize(barName, meta.barCount)} to make " +
                    SmithingUtils.prefixAn(meta.name) +
                    "."
            )
            return false
        }

        return true
    }

    private data class OpenAnvilTask(val barInternal: String)

    private data class AnvilSmithTask(
        val meta: SmithingProductMeta,
        val amount: Int,
        val completed: Int,
    )

    private companion object {
        /** Sentinel for the All button; resolved against the carried bars per product click. */
        private const val QUANTITY_ALL = -1

        private val DoricsAnvils = setOf(CoordGrid(2950, 3451, 0), CoordGrid(2950, 3452, 0))

        private const val DoricsQuestVarp = "varp.doricquest"

        private const val DoricsQuestComplete = 100

        private const val DoricsRefusal = "You should probably ask before using that."
    }
}
