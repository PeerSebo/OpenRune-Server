package org.rsmod.content.skills.smithing

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.craftingLvl
import org.rsmod.api.player.stat.smithingLvl
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.table.smithing.SmithingCrystalSingingRow
import org.rsmod.content.skills.Material
import org.rsmod.content.skills.SkillMultiConfig
import org.rsmod.content.skills.SkillMultiEntry
import org.rsmod.content.skills.openSkillMulti
import org.rsmod.content.skills.smithing.configs.SmithingParams
import org.rsmod.content.skills.smithing.util.SmithingUtils
import org.rsmod.game.inv.Inventory
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class CrystalSingingScript : PluginScript() {

    private val crystalShardInternal = "obj.prif_crystal_shard"

    private val crystalItems = SmithingCrystalSingingRow.all()

    private val itemsByOutput = crystalItems.associateBy { it.output.internalName }

    override fun ScriptContext.startup() {
        onOpLoc1("loc.prif_singing_bowl") { openCrystalSinging() }

        onPlayerQueueWithArgs<CrystalSingTask>(SmithingParams.CRYSTAL_SING_QUEUE) {
            processSingTask(it.args)
        }
    }

    private suspend fun ProtectedAccess.openCrystalSinging() {
        val available = crystalItems.filter { canUseItem(it) }
        if (available.isEmpty()) {
            return
        }

        openSkillMulti(
            SkillMultiConfig(
                verb = "make",
                entries = available.map(::toEntry),
                maxCountProvider = { inventory, entry ->
                    itemsByOutput[entry.internal]?.let { maxCraftCount(inventory, it) } ?: 0
                },
            )
        ) { selection ->
            val item = itemsByOutput[selection.entry.internal] ?: return@openSkillMulti
            startSinging(item, selection.amount)
        }
    }

    private fun toEntry(item: SmithingCrystalSingingRow): SkillMultiEntry =
        SkillMultiEntry(
            item.output.internalName,
            item.input.zip(item.inputAmount).map { (material, amount) ->
                Material(material.internalName, amount)
            },
        )

    private fun ProtectedAccess.canUseItem(item: SmithingCrystalSingingRow): Boolean {
        if (
            player.smithingLvl < item.statReq.first().t1 ||
                player.craftingLvl < item.statReq.first().t1
        ) {
            return false
        }

        return item.input.zip(item.inputAmount).any { (material, _) ->
            material.internalName != crystalShardInternal && inv.contains(material.internalName)
        }
    }

    private suspend fun ProtectedAccess.startSinging(
        item: SmithingCrystalSingingRow,
        requestedAmount: Int,
    ) {
        val amount = minOf(requestedAmount, maxCraftCount(inv, item))
        if (amount <= 0) {
            return
        }

        if (!confirmSinging(item)) {
            return
        }

        // Re-confirming replaces a still-running chain instead of stacking a second one.
        clearWeakQueue(SmithingParams.CRYSTAL_SING_QUEUE)
        scheduleSingCycle(CrystalSingTask(item, amount, completed = 0))
    }

    private suspend fun ProtectedAccess.confirmSinging(item: SmithingCrystalSingingRow): Boolean {
        if (SmithingState.skipCrystalConfirmation(player)) {
            return true
        }

        val materialsText =
            item.input
                .zip(item.inputAmount)
                .filterNot { (material, _) -> material.internalName == crystalShardInternal }
                .joinToString(" and ") { (material, amount) ->
                    val name = SmithingUtils.itemName(material)
                    "${SmithingUtils.countLiteral(amount)} ${SmithingUtils.pluralize(name, amount)}"
                }

        val shortName = item.shortname ?: SmithingUtils.itemName(item.output)

        objbox(
            item.output.internalName,
            "This will consume $materialsText. " +
                "Reverting the $shortName will not give you any materials back.",
        )

        return when (
            choice3(
                "Yes",
                1,
                "Yes, and don't ask again.",
                2,
                "No",
                3,
                title = "Are you sure you wish to proceed?",
            )
        ) {
            1 -> true
            2 -> {
                SmithingState.setSkipCrystalConfirmation(player, true)
                true
            }
            else -> false
        }
    }

    private suspend fun ProtectedAccess.processSingTask(task: CrystalSingTask) {
        if (!canProduce(task.item)) {
            resetAnim()
            return
        }

        if (!performSing(task.item)) {
            resetAnim()
            return
        }

        val completed = task.completed + 1
        if (completed < task.amount && hasMaterials(task.item)) {
            scheduleSingCycle(task.copy(completed = completed))
        } else {
            resetAnim()
        }
    }

    private fun ProtectedAccess.scheduleSingCycle(task: CrystalSingTask) {
        anim(SmithingParams.CRYSTAL_SING_ANIM)
        soundSynth(SmithingParams.CRYSTAL_SING_SOUND)
        smithQueue(SmithingParams.CRYSTAL_SING_QUEUE, SmithingParams.CRYSTAL_SING_TICKS, task)
    }

    private fun ProtectedAccess.performSing(item: SmithingCrystalSingingRow): Boolean {
        val crafted =
            tryProduce(
                inputs =
                    item.input.zip(item.inputAmount).map { (material, amount) ->
                        material.internalName to amount
                    },
                output = item.output.internalName,
                outputCount = 1,
            )
        if (!crafted) {
            return false
        }

        statAdvance("stat.smithing", item.xp.toDouble())
        statAdvance("stat.crafting", item.xp.toDouble())
        return true
    }

    private fun ProtectedAccess.hasMaterials(item: SmithingCrystalSingingRow): Boolean =
        item.input.zip(item.inputAmount).all { (material, required) ->
            inv.count(material.internalName) >= required
        }

    private suspend fun ProtectedAccess.canProduce(item: SmithingCrystalSingingRow): Boolean {
        val missingMaterials =
            item.input.zip(item.inputAmount).mapNotNull { (material, required) ->
                val missing = required - inv.count(material.internalName)

                if (missing > 0) {
                    SmithingUtils.itemName(material) to missing
                } else {
                    null
                }
            }

        if (missingMaterials.isNotEmpty()) {
            val missingText =
                missingMaterials.joinToString(", ") { (name, amount) ->
                    "${SmithingUtils.countLiteral(amount)} ${name.lowercase()}"
                }

            val outputName = SmithingUtils.itemName(item.output)

            mesbox(
                "You need $missingText to make " +
                    "${SmithingUtils.prefixAn(outputName).lowercase()}."
            )

            return false
        }

        return SmithingUtils.requireSmithingAndCraftingLevel(
            this,
            item.statReq.first().t1,
            "create ${SmithingUtils.itemName(item.output).lowercase()}",
        )
    }

    private fun maxCraftCount(inventory: Inventory, item: SmithingCrystalSingingRow): Int =
        item.input.zip(item.inputAmount).minOfOrNull { (material, required) ->
            inventory.count(material.internalName) / required
        } ?: 0

    private data class CrystalSingTask(
        val item: SmithingCrystalSingingRow,
        val amount: Int,
        val completed: Int,
    )
}
