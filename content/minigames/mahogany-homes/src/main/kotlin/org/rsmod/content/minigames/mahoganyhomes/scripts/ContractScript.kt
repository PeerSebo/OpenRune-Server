package org.rsmod.content.minigames.mahoganyhomes.scripts

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLoc4
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.content.minigames.mahoganyhomes.configs.MahoganyHomesData
import org.rsmod.content.minigames.mahoganyhomes.configs.MahoganyHomesFurniture
import org.rsmod.content.minigames.mahoganyhomes.configs.MahoganyHomesHouse
import org.rsmod.content.minigames.mahoganyhomes.configs.MahoganyHomesTier
import org.rsmod.content.minigames.mahoganyhomes.core.MahoganyHomesBuildState
import org.rsmod.content.minigames.mahoganyhomes.core.MahoganyHomesHotspotKind
import org.rsmod.content.minigames.mahoganyhomes.core.MahoganyHomesHotspots
import org.rsmod.content.minigames.mahoganyhomes.core.MahoganyHomesManager
import org.rsmod.content.minigames.mahoganyhomes.core.MahoganyHomesRepairState
import org.rsmod.content.minigames.mahoganyhomes.planksack.PlankSack
import org.rsmod.content.minigames.mahoganyhomes.planksack.PlankType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// Hotspot operations must be validated against the current contract before shared varbits change.
class ContractScript : PluginScript() {

    private val hotspotHouse: Map<String, MahoganyHomesHouse> =
        MahoganyHomesHouse.entries.flatMap { house -> house.hotspots.map { loc -> loc to house } }.toMap()

    override fun ScriptContext.startup() {
        MahoganyHomesData.tiers
        MahoganyHomesHotspots.validateAtBoot()

        for (locName in hotspotHouse.keys) {
            onOpLoc2(locName) { removeFurniture(locName) }
            onOpLoc3(locName) { repair(locName) }
            onOpLoc4(locName) { build(locName) }
        }

        onPlayerQueueWithArgs<BuildTask>(BUILD_QUEUE) { finishBuild(it.args) }
        onPlayerQueueWithArgs<RepairTask>(REPAIR_QUEUE) { finishRepair(it.args) }
    }

    private suspend fun ProtectedAccess.removeFurniture(locName: String) {
        val house = hotspotHouse[locName] ?: return
        if (!inActiveHouse(house)) return
        val def = MahoganyHomesHotspots.get(locName) ?: return

        if (def.kind != MahoganyHomesHotspotKind.BUILD) {
            mes("I'd better leave this alone right now.")
            return
        }

        val current = MahoganyHomesManager.readDisplaySlot(player, def.slot)
        val removable =
            current == MahoganyHomesBuildState.EDITABLE ||
                current in MahoganyHomesBuildState.REGULAR..MahoganyHomesBuildState.MAHOGANY
        if (!removable) {
            mes("There's nothing there to remove.")
            return
        }

        MahoganyHomesManager.writeDisplaySlot(player, def.slot, MahoganyHomesBuildState.EMPTY)
        mes("You remove the old furniture.")
    }

    private suspend fun ProtectedAccess.build(locName: String) {
        val house = hotspotHouse[locName] ?: return
        if (!inActiveHouse(house)) return
        val def = MahoganyHomesHotspots.get(locName) ?: return

        if (def.kind != MahoganyHomesHotspotKind.BUILD) {
            mes("I'd better leave this alone right now.")
            return
        }
        val current = MahoganyHomesManager.readDisplaySlot(player, def.slot)
        if (current != MahoganyHomesBuildState.EMPTY) {
            mes("You need to remove the old piece before you can build a new one.")
            return
        }

        val kind = def.furnitureKind
        val plankCost = kind?.let { MahoganyHomesFurniture.buildPlankCost(it) }
        if (plankCost == null) {
            mes("You can't build anything there right now.")
            return
        }

        // TODO: restore the native furniture-selection interface.
        if (!hasHammer()) {
            mes("You need a hammer to build this.")
            return
        }
        if (!hasSaw()) {
            mes("You need a saw to build this.")
            return
        }

        val tier = MahoganyHomesManager.currentTier(player)
        val plankType = PlankType.ofObj(tier.plankObj) ?: return
        if (!hasPlanks(tier.plankObj, plankType, plankCost)) {
            mes("You don't have enough planks to build this.")
            return
        }
        if (!tryConsumePlanks(tier.plankObj, plankType, plankCost)) {
            mes("You don't have enough planks to build this.")
            return
        }

        resetAnim()
        anim(BUILD_ANIM)
        soundSynth(BUILD_WOOD_SYNTH)

        val doneState = MahoganyHomesBuildState.forTierOrdinal(tier.ordinal)
        weakQueue(BUILD_QUEUE, BUILD_REPAIR_CADENCE_TICKS, BuildTask(def.slot, doneState, plankCost, tier.buildXp))
    }

    private fun ProtectedAccess.finishBuild(task: BuildTask) {
        MahoganyHomesManager.writeDisplaySlot(player, task.slot, task.doneState)
        val xp = task.plankCost * task.xpPerPlank * MahoganyHomesManager.carpenterOutfitMultiplier(player)
        player.statAdvance("stat.construction", xp)
        resetAnim()
        mes("You build the furniture.")
    }

    private suspend fun ProtectedAccess.repair(locName: String) {
        val house = hotspotHouse[locName] ?: return
        if (!inActiveHouse(house)) return
        val def = MahoganyHomesHotspots.get(locName) ?: return

        if (def.kind != MahoganyHomesHotspotKind.REPAIR) {
            mes("There's nothing to repair there.")
            return
        }
        val current = MahoganyHomesManager.readDisplaySlot(player, def.slot)
        if (current != MahoganyHomesRepairState.BROKEN) {
            mes("It doesn't need repairing.")
            return
        }

        val kind = def.furnitureKind ?: return
        val tier = MahoganyHomesManager.currentTier(player)

        if (!hasHammer()) {
            mes("You need a hammer to repair this.")
            return
        }
        if (!hasSaw()) {
            mes("You need a saw to repair this.")
            return
        }

        if (MahoganyHomesFurniture.repairsWithBar(kind)) {
            if (inv.count("obj.steel_bar") < 1) {
                mes("You need a steel bar to repair this.")
                return
            }
            if (invDel(inv, "obj.steel_bar", 1).failure) return

            resetAnim()
            anim(BUILD_ANIM)
            soundSynth(BUILD_METAL_SYNTH)
            val task = RepairTask(def.slot, MahoganyHomesRepairState.FIXED, xp = MahoganyHomesTier.BAR_REPAIR_XP)
            weakQueue(REPAIR_QUEUE, BUILD_REPAIR_CADENCE_TICKS, task)
            return
        }

        val plankCost = MahoganyHomesFurniture.repairPlankCost(kind) ?: return
        val plankType = PlankType.ofObj(tier.plankObj) ?: return
        if (!hasPlanks(tier.plankObj, plankType, plankCost)) {
            mes("You don't have enough planks to repair this.")
            return
        }
        if (!tryConsumePlanks(tier.plankObj, plankType, plankCost)) {
            mes("You don't have enough planks to repair this.")
            return
        }

        resetAnim()
        anim(BUILD_ANIM)
        soundSynth(BUILD_WOOD_SYNTH)
        val task = RepairTask(def.slot, MahoganyHomesRepairState.FIXED, xp = plankCost * tier.repairXp)
        weakQueue(REPAIR_QUEUE, BUILD_REPAIR_CADENCE_TICKS, task)
    }

    private fun ProtectedAccess.finishRepair(task: RepairTask) {
        MahoganyHomesManager.writeDisplaySlot(player, task.slot, task.doneState)
        val xp = task.xp * MahoganyHomesManager.carpenterOutfitMultiplier(player)
        player.statAdvance("stat.construction", xp)
        resetAnim()
        mes("You repair the item.")
    }

    private fun ProtectedAccess.inActiveHouse(house: MahoganyHomesHouse): Boolean {
        val current = MahoganyHomesManager.currentHouse(player)
        if (current != house) {
            mes("You don't have a contract for this house.")
            return false
        }
        return true
    }

    private fun ProtectedAccess.hasHammer(): Boolean = HAMMERS.any { it in inv }

    private fun ProtectedAccess.hasSaw(): Boolean = SAWS.any { it in inv || it in player.worn }

    private fun ProtectedAccess.hasPlanks(plankObj: String, type: PlankType, amount: Int): Boolean =
        inv.count(plankObj) + PlankSack.stored(player, type) >= amount

    // Inventory and sack consumption must roll back together on failure.
    private fun ProtectedAccess.tryConsumePlanks(plankObj: String, type: PlankType, amount: Int): Boolean {
        val fromInv = minOf(inv.count(plankObj), amount)
        val fromSack = amount - fromInv

        if (fromInv > 0 && invDel(inv, plankObj, fromInv).failure) {
            return false
        }
        if (fromSack > 0 && !PlankSack.tryConsume(player, type, fromSack)) {
            if (fromInv > 0) invAdd(inv, plankObj, fromInv)
            return false
        }
        return true
    }

    private data class BuildTask(val slot: Int, val doneState: Int, val plankCost: Int, val xpPerPlank: Double)

    private data class RepairTask(val slot: Int, val doneState: Int, val xp: Double)

    private companion object {
        const val BUILD_QUEUE = "queue.mahogany_homes_build"
        const val REPAIR_QUEUE = "queue.mahogany_homes_repair"
        const val BUILD_REPAIR_CADENCE_TICKS = 2

        val HAMMERS = listOf("obj.hammer", "obj.imcando_hammer", "obj.imcando_hammer_offhand")
        val SAWS =
            listOf("obj.poh_saw", "obj.eyeglo_crystal_saw", "obj.wearable_saw", "obj.wearable_saw_offhand")

        const val BUILD_ANIM = "seq.human_poh_build"
        const val BUILD_WOOD_SYNTH = "synth.POH_BUILD_WOOD"
        const val BUILD_METAL_SYNTH = "synth.POH_BUILD_METAL"
    }
}
