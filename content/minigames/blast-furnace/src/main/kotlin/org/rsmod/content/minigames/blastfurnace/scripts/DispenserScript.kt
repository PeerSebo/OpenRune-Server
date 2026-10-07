package org.rsmod.content.minigames.blastfurnace.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.Constants
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.minigames.blastfurnace.configs.BlastFurnaceParams
import org.rsmod.content.minigames.blastfurnace.core.BlastFurnaceSmeltRequest
import org.rsmod.content.minigames.blastfurnace.core.blastFurnaceBarCount
import org.rsmod.content.minigames.blastfurnace.core.blastFurnaceBarsHot
import org.rsmod.content.minigames.blastfurnace.core.setBlastFurnaceBarCount
import org.rsmod.content.skills.SkillMultiConfig
import org.rsmod.content.skills.SkillMultiEntry
import org.rsmod.content.skills.openSkillMulti
import org.rsmod.events.EventBus
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
class DispenserScript @Inject constructor(private val eventBus: EventBus) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.blast_furnace_ore_dispenser_empty") { checkDispenser() }

        onOpLoc1("loc.blast_furnace_ore_dispenser_full") { takeBars(hot = true) }
        onOpLoc2("loc.blast_furnace_ore_dispenser_full") { checkDispenser() }
        onOpLocU("loc.blast_furnace_ore_dispenser_full", "obj.bucket_water") { throwWater() }

        onOpLoc1("loc.blast_furnace_ore_dispenser_cooled") { takeBars(hot = false) }
        onOpLoc2("loc.blast_furnace_ore_dispenser_cooled") { checkDispenser() }
    }

    private suspend fun ProtectedAccess.takeBars(hot: Boolean) {
        if (hot) {
            if (!hasHotBarGloves()) {
                mesbox("The bars are still molten! You need to cool them down.")
                return
            }
            if (player.blastFurnaceBarsHot == SmeltingProcess.DISPENSER_POURING) {
                mesbox("Wait for the bars to become molten before collecting them.")
                return
            }
        }

        val available = BAR_TYPES.filter { player.blastFurnaceBarCount(it) > 0 }
        if (available.isEmpty()) {
            mesbox("The dispenser doesn't contain any bars.")
            return
        }

        if (inv.freeSpace() <= 0) {
            mes(Constants.dm_invspace)
            return
        }

        if (available.size > 1) {
            openSkillMulti(
                SkillMultiConfig(
                    verb = "take",
                    entries = available.map { SkillMultiEntry(it) },
                    maxCountProvider = { inventory, entry ->
                        minOf(
                            player.blastFurnaceBarCount(entry.internal),
                            inventory.freeSpace(),
                        )
                    },
                )
            ) { selection ->
                ifClose()
                withdrawBars(
                    selection.entry.internal,
                    selection.amount,
                )
            }
            return
        }

        withdrawBars(
            available.single(),
            Int.MAX_VALUE,
        )
    }

    private suspend fun ProtectedAccess.withdrawBars(
        bar: String,
        requested: Int,
    ) {
        if (bar !in BAR_TYPES || requested <= 0) {
            return
        }

        if (player.blastFurnaceBarsHot == SmeltingProcess.DISPENSER_POURING) {
            mesbox("Wait for the bars to become molten before collecting them.")
            return
        }
        if (player.blastFurnaceBarsHot == SmeltingProcess.DISPENSER_HOT) {
            if (!hasHotBarGloves()) {
                mesbox("The bars are still molten! You need to cool them down.")
                return
            }
            player.blastFurnaceBarsHot = SmeltingProcess.DISPENSER_COOLED
            clearSoftTimer(BlastFurnaceParams.HOT_TIMER)
        }

        val currentCount = player.blastFurnaceBarCount(bar)
        val space = inv.freeSpace()
        val amount = minOf(requested, currentCount, space)
        if (amount <= 0) {
            if (space <= 0) {
                mes(Constants.dm_invspace)
            } else {
                mesbox("The dispenser doesn't contain any ${barLabel(bar)}.")
            }
            return
        }
        if (invAdd(inv, bar, amount).failure) {
            mes(Constants.dm_invspace)
            return
        }
        player.setBlastFurnaceBarCount(bar, currentCount - amount)

        if (BAR_TYPES.all { player.blastFurnaceBarCount(it) == 0 }) {
            player.blastFurnaceBarsHot = SmeltingProcess.DISPENSER_EMPTY
            clearSoftTimer(BlastFurnaceParams.HOT_TIMER)
        }

        eventBus.publish(BlastFurnaceSmeltRequest(player))

        val plural = if (amount == 1) "" else "s"
        mesbox("You take $amount ${barLabel(bar)}$plural from the dispenser.")
    }

    private suspend fun ProtectedAccess.checkDispenser() {
        val held = BAR_TYPES.filter { player.blastFurnaceBarCount(it) > 0 }
        if (held.isEmpty()) {
            mesbox("The dispenser doesn't contain any bars.")
            return
        }
        val readout =
            held.joinToString(", ") { bar ->
                "${player.blastFurnaceBarCount(bar)} x ${barLabel(bar)}"
            }
        mesbox("The dispenser contains: $readout.")
    }

    private suspend fun ProtectedAccess.throwWater() {
        if (invReplace(inv, "obj.bucket_water", 1, "obj.bucket_empty").failure) {
            return
        }
        mes("You throw the water over the bars.")
        soundSynth(BlastFurnaceParams.SOUND_COOLDOWN)
        player.blastFurnaceBarsHot = SmeltingProcess.DISPENSER_COOLED
        clearSoftTimer(BlastFurnaceParams.HOT_TIMER)
    }

    private fun ProtectedAccess.hasHotBarGloves(): Boolean =
        "obj.ice_gloves" in worn || "obj.smithing_uniform_gloves_ice" in worn

    private fun barLabel(internal: String): String = internal.removePrefix("obj.").replace('_', ' ')

    private companion object {
        val BAR_TYPES =
            listOf(
                "obj.bronze_bar",
                "obj.iron_bar",
                "obj.steel_bar",
                "obj.silver_bar",
                "obj.lead_bar",
                "obj.gold_bar",
                "obj.cupronickel_bar",
                "obj.mithril_bar",
                "obj.adamantite_bar",
                "obj.runite_bar",
            )
    }
}
