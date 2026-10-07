package org.rsmod.content.minigames.blastfurnace.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.smithingLvl
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.minigames.blastfurnace.configs.BlastFurnaceParams
import org.rsmod.content.minigames.blastfurnace.core.BlastFurnaceBeltLoad
import org.rsmod.content.minigames.blastfurnace.core.BlastFurnaceRecipes
import org.rsmod.content.minigames.blastfurnace.core.BlastFurnaceVisualEvent
import org.rsmod.content.minigames.blastfurnace.core.addBeltInFlight
import org.rsmod.content.minigames.blastfurnace.core.beltInFlight
import org.rsmod.content.minigames.blastfurnace.core.blastFurnaceBarCount
import org.rsmod.content.minigames.blastfurnace.core.blastFurnaceCoal
import org.rsmod.content.minigames.blastfurnace.core.blastFurnaceCofferBalance
import org.rsmod.content.minigames.blastfurnace.core.blastFurnaceOreCount
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
class ConveyorScript @Inject constructor(private val eventBus: EventBus) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.blast_furnace_conveyer_belt_clickable") { loadOre(ORE_ITEMS) }
        ORE_ITEMS.forEach { ore ->
            onOpLocU("loc.blast_furnace_conveyer_belt_clickable", ore) { loadOre(listOf(ore)) }
        }
    }

    private suspend fun ProtectedAccess.loadOre(candidates: List<String>) {
        if (!player.hasBlastFurnaceAccess()) {
            mesbox("You must ask the foreman's permission before using the blast furnace.")
            return
        }
        if (player.blastFurnaceCofferBalance <= 0) {
            mesbox("Put some coins in the coffer before using the belt.")
            return
        }

        val entries = mutableListOf<BlastFurnaceBeltLoad.BeltEntry>()
        var oreCapHit = false
        var barCapHit = false
        var levelBlocked = false
        for (ore in candidates.sortedBy { if (it == "obj.coal") 0 else 1 }) {
            val held = heldAmount(player, ore)
            if (held <= 0) {
                continue
            }
            if (player.smithingLvl < requiredLevel(ore)) {
                levelBlocked = true
                continue
            }

            val recipeBar = oreOutputBar(ore)
            if (
                recipeBar != null &&
                    player.blastFurnaceBarCount(recipeBar) >= BlastFurnaceParams.BAR_DISPENSER_CAP
            ) {
                barCapHit = true
                continue
            }

            val room =
                (oreCap(ore) - player.blastFurnaceOreCount(ore) - player.beltInFlight(ore))
                    .coerceAtLeast(0)
            if (room <= 0) {
                oreCapHit = true
                continue
            }
            val amount = minOf(held, room)
            if (invDel(inv, ore, amount).failure) {
                continue
            }
            player.addBeltInFlight(ore, amount)
            entries += BlastFurnaceBeltLoad.BeltEntry(ore, amount)
        }

        when {
            entries.isNotEmpty() -> {
                entries.sortBy { if (it.ore == "obj.coal") 0 else 1 }
                softQueue(
                    BlastFurnaceParams.BELT_QUEUE,
                    BlastFurnaceParams.BELT_TRAVEL_TICKS,
                    BlastFurnaceBeltLoad(entries),
                )
                softQueue(
                    BlastFurnaceParams.SMELT_QUEUE,
                    BlastFurnaceParams.BELT_TRAVEL_TICKS + BlastFurnaceParams.POT_SMELT_TICKS,
                )
                mes("All your ore goes onto the conveyor belt.")
                eventBus.publish(BlastFurnaceVisualEvent.Belt(entries.map { it.ore }))
            }
            levelBlocked -> mesbox("You don't have the Smithing level to smelt any of that ore.")
            barCapHit -> mesbox(BlastFurnaceParams.BAR_CAP_MESSAGE)
            oreCapHit -> mesbox(BlastFurnaceParams.ORE_CAP_MESSAGE)
            else -> mes("You don't have any ore to put on the belt.")
        }
    }

    internal fun heldAmount(player: Player, ore: String): Int = player.inv.count(ore)

    private fun requiredLevel(ore: String): Int =
        when (ore) {
            "obj.iron_ore" -> minOf(
                BlastFurnaceRecipes.ironFallback.levelReq,
                BlastFurnaceRecipes.steel.levelReq,
            )
            else -> BlastFurnaceRecipes.forPrimaryOre(ore)?.levelReq ?: 0
        }

    private fun ProtectedAccess.oreOutputBar(ore: String): String? {
        if (ore == "obj.iron_ore") {
            val hasCoal = player.blastFurnaceCoal + player.beltInFlight("obj.coal") > 0
            return if (hasCoal && player.smithingLvl >= BlastFurnaceRecipes.steel.levelReq) {
                "obj.steel_bar"
            } else {
                "obj.iron_bar"
            }
        }
        return BlastFurnaceRecipes.forPrimaryOre(ore)?.output?.internalName
    }

    private fun oreCap(ore: String): Int =
        when (ore) {
            "obj.coal" -> BlastFurnaceParams.COAL_CAP
            "obj.tin_ore" -> BlastFurnaceParams.TIN_CAP
            "obj.copper_ore" -> BlastFurnaceParams.COPPER_CAP
            else -> BlastFurnaceParams.PRIMARY_ORE_CAP
        }

    private companion object {
        val ORE_ITEMS =
            listOf(
                "obj.copper_ore",
                "obj.tin_ore",
                "obj.iron_ore",
                "obj.mithril_ore",
                "obj.adamantite_ore",
                "obj.runite_ore",
                "obj.silver_ore",
                "obj.gold_ore",
                "obj.coal",
                "obj.lead_ore",
                "obj.nickel_ore",
                "obj.smithing_catalyst",
            )
    }
}
