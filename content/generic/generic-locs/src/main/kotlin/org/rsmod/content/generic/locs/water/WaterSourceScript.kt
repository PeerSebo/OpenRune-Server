package org.rsmod.content.generic.locs.water

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class WaterSourceScript : PluginScript() {

    override fun ScriptContext.startup() {
        for (source in Sources) {
            for (container in source.containers) {
                onOpLocU(source.loc, container.empty) { fill(source, container, it.invSlot) }
            }
            if (source.acceptsWateringCan) {
                for (can in WateringCans) {
                    onOpLocU(source.loc, can.empty) { fill(source, can, it.invSlot) }
                }
                onOpLocU(source.loc, FullWateringCan) { mes("Your watering can is already full.") }
            }
        }

        // Empty uses op3 for fishbowls and op4 for the other filled containers.
        for (container in Containers) {
            when (container.emptyOp) {
                3 -> onOpHeld3(container.filled) { empty(container, it.slot) }
                4 -> onOpHeld4(container.filled) { empty(container, it.slot) }
                else -> error("Unsupported empty op for ${container.filled}: ${container.emptyOp}")
            }
        }

        onPlayerQueueWithArgs<FillTask>(WaterFillQueue) { processFillTick(it.args) }
    }

    private suspend fun ProtectedAccess.fill(
        source: WaterSource,
        container: WaterContainer,
        clickedSlot: Int,
    ) {
        arriveDelay()

        val filled =
            ServerCacheManager.getItem(container.filled.asRSCM(RSCMType.OBJ))
                ?: error("Water container does not resolve: ${container.filled}")

        // Replace an existing chain on re-click; parallel chains would double the fill rate.
        clearWeakQueue(WaterFillQueue)

        // Arrival can suspend: never replace an unrelated item that has since entered this slot.
        if (inv[clickedSlot]?.id != container.empty.asRSCM(RSCMType.OBJ)) return
        val result = invReplaceSlot(inv, clickedSlot, 1, filled)
        if (result.failure) {
            return
        }

        anim(FillAnim)
        soundSynth(FillSound)
        spam("You fill the ${container.noun} from the ${source.noun}.")

        if (inv.count(container.empty) == 0) {
            return
        }

        // Add one cycle because the queue consumes its first count during this pass.
        weakQueue(WaterFillQueue, 2, FillTask(source, container))
    }

    private fun ProtectedAccess.processFillTick(task: FillTask) {
        val (source, container) = task

        val result = invReplace(inv, container.empty, 1, container.filled)
        if (result.failure) {
            return
        }

        anim(FillAnim)
        soundSynth(FillSound)
        spam("You fill the ${container.noun} from the ${source.noun}.")

        if (inv.count(container.empty) == 0) {
            return
        }

        if (player.hasMovedThisCycle) {
            return
        }

        // Self-requeueing needs one extra cycle to compensate for this pass consuming the first count.
        weakQueue(WaterFillQueue, 2, task)
    }

    // Replace the clicked slot, not the first matching item, so another jug or can is not emptied.
    private fun ProtectedAccess.empty(container: WaterContainer, slot: Int) {
        val empty =
            ServerCacheManager.getItem(container.empty.asRSCM(RSCMType.OBJ))
                ?: error("Water container does not resolve: ${container.empty}")

        if (invReplaceSlot(inv, slot, 1, empty).failure) {
            return
        }

        soundSynth(EmptySound)
        spam("You empty the contents of the ${container.noun} on the floor.")
    }

    private data class WaterSource(
        val loc: String,
        val noun: String,
        val containers: List<WaterContainer>,
        val acceptsWateringCan: Boolean = true,
    )

    private data class WaterContainer(
        val empty: String,
        val filled: String,
        val noun: String,
        val emptyOp: Int = 4,
    )

    private data class FillTask(val source: WaterSource, val container: WaterContainer)

    private companion object {
        private const val FillAnim = "seq.human_pickuptable_walkmerge"
        private const val FillSound = "synth.TAP_FILL"
        private const val EmptySound = "synth.LIQUID"

        private const val WaterFillQueue = "queue.water_fill"

        private val Jug = WaterContainer("obj.jug_empty", "obj.jug_water", "jug")
        private val Bucket = WaterContainer("obj.bucket_empty", "obj.bucket_water", "bucket")
        private val Vial = WaterContainer("obj.vial_empty", "obj.vial_water", "vial")
        private val Bowl = WaterContainer("obj.bowl_empty", "obj.bowl_water", "bowl")
        private val Cup = WaterContainer("obj.cup_empty", "obj.cup_water", "cup")

        private val Fishbowl =
            WaterContainer("obj.fishbowl_empty", "obj.fishbowl_water", "fishbowl", emptyOp = 3)

        private val Containers = listOf(Jug, Bucket, Vial, Bowl, Cup, Fishbowl)

        private const val FullWateringCan = "obj.watering_can_8"
        private val WateringCans = (0..7).map {
            WaterContainer("obj.watering_can_$it", FullWateringCan, "watering can")
        }

        private val TapContainers = Containers - Fishbowl

        private val WellContainers = listOf(Bucket, Cup)

        private val Sources =
            listOf(
                WaterSource("loc.qip_cook_sink", "sink", Containers),
                WaterSource("loc.sink", "sink", Containers),
                WaterSource("loc.sink2", "sink", Containers),
                WaterSource("loc.fai_varrock_dirty_sink", "sink", Containers),
                WaterSource("loc.fai_varrock_cooking_kitchen_sink", "sink", Containers),
                WaterSource("loc.fai_varrock_posh_sink", "sink", Containers),
                WaterSource("loc.farm_sink", "sink", Containers),
                WaterSource("loc.mortton_sink", "sink", Containers),
                WaterSource("loc.lunar_moonclan_sink", "sink", Containers),
                WaterSource("loc.kr_sin_sink2", "sink", Containers),
                WaterSource("loc.piscarilius_sink_01", "sink", Containers),
                WaterSource("loc.piscarilius_sink_02", "sink", Containers),
                WaterSource("loc.lovakengj_sink", "sink", Containers),
                WaterSource("loc.darkm_poor_sink", "sink", Containers),
                WaterSource("loc.darkm_middle_sink", "sink", Containers),
                WaterSource("loc.darkm_rich_sink", "sink", Containers),
                WaterSource("loc.akd_shayzien_sink", "sink", Containers),
                WaterSource("loc.con_contract_hosidius_north_sink_fixed", "sink", Containers),
                WaterSource("loc.myq6_kitchen01_sink01", "sink", Containers),
                WaterSource("loc.sink_lassar01_default01", "sink", Containers),

                WaterSource("loc.fountain", "fountain", Containers),
                WaterSource("loc.goldfountain", "fountain", Containers),
                WaterSource("loc.fai_varrock_fountain_splash", "fountain", Containers),
                WaterSource("loc.fai_varrock_small_fountain", "fountain", Containers),
                WaterSource("loc.feud_fountain", "fountain", Containers),
                WaterSource("loc.draynor_fountain", "fountain", Containers),
                WaterSource("loc.dorgesh_fountain", "fountain", Containers),
                WaterSource("loc.fai_falador_fountain", "fountain", Containers),
                WaterSource("loc.hos_fountain", "fountain", Containers),
                WaterSource("loc.shayzien_fountain", "fountain", Containers),
                WaterSource("loc.civitas_fountain01", "fountain", Containers),
                WaterSource("loc.aldarin_fountain01", "fountain", Containers),
                WaterSource("loc.aldarin_fountain02", "fountain", Containers),
                WaterSource("loc.talkasti01_fountain01_default01", "fountain", Containers),
                WaterSource("loc.fountain_2x3", "fountain", Containers),
                WaterSource("loc.villa_fountain", "fountain", Containers),
                WaterSource("loc.ates_fountain", "fountain", Containers),
                WaterSource("loc.ates_fountain_searchable", "fountain", Containers),
                WaterSource("loc.fountain_lassar01_shadow", "fountain", Containers),

                WaterSource("loc.well", "well", WellContainers, acceptsWateringCan = false),
                WaterSource("loc.desertwell", "well", WellContainers, acceptsWateringCan = false),
                WaterSource("loc.fai_varrock_well", "well", WellContainers, acceptsWateringCan = false),
                WaterSource("loc.rellekka_well", "well", WellContainers, acceptsWateringCan = false),
                WaterSource("loc.fai_falador_well", "well", WellContainers, acceptsWateringCan = false),
                WaterSource("loc.gnome_well", "well", WellContainers, acceptsWateringCan = false),
                WaterSource("loc.fossil_well", "well", WellContainers, acceptsWateringCan = false),
                WaterSource("loc.gh_well", "well", WellContainers, acceptsWateringCan = false),
                WaterSource("loc.well_oasis", "well", WellContainers, acceptsWateringCan = false),
                WaterSource("loc.aldarin_well", "well", WellContainers, acceptsWateringCan = false),
                WaterSource("loc.well_drakan01_ruined01", "well", WellContainers, acceptsWateringCan = false),
                WaterSource("loc.well_drakan01_ruined01_3x3", "well", WellContainers, acceptsWateringCan = false),

                WaterSource("loc.fai_varrock_waterpump", "pump", Containers),
                WaterSource("loc.fai_falador_waterpump", "pump", Containers),
                WaterSource("loc.barrows_waterpump", "pump", Containers),
                WaterSource("loc.fortis_water_pump", "pump", Containers),
                WaterSource("loc.kebos_farming_guild_watertap", "pump", TapContainers),
                WaterSource("loc.pandemonium_water_pump", "pump", Containers),

                WaterSource("loc.water_barrel1", "barrel", Containers),
                WaterSource("loc.water_barrel2", "barrel", Containers),
                WaterSource("loc.farm_water_barrel1", "barrel", Containers),
                WaterSource("loc.farm_water_barrel2", "barrel", Containers),
                WaterSource("loc.shayzien_barrel_water", "barrel", Containers),
                WaterSource("loc.gnome_waterbarrel", "barrel", Containers),
                WaterSource("loc.shayzien_barrel_water_dark", "barrel", Containers),
                WaterSource("loc.burgh_barrel_water", "barrel", Containers),
            )
    }
}
