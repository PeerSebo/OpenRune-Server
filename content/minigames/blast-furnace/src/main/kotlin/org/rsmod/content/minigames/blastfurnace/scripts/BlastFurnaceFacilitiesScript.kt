package org.rsmod.content.minigames.blastfurnace.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class BlastFurnaceFacilitiesScript : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1("loc.dwarf_keldagrim_factory_stairs") {
            arriveDelay()
            telejump(CoordGrid(1940, 4958, 0))
        }
        onOpLoc1("loc.blast_furnace_stairs_up") {
            arriveDelay()
            telejump(CoordGrid(2931, 10197, 0))
        }

        onOpLoc1("loc.blast_furnace_gauge") { openTemperatureGauge() }
        onIfModalButton("component.blast_furnace_temp_gauge:blast_furnace_temp_gauge_close_button") {
            ifClose()
        }

        onOpLoc1("loc.blast_furnace_sink") { fillBuckets() }
        onOpLocU("loc.blast_furnace_sink", "obj.bucket_empty") { fillBuckets() }
    }

    private suspend fun ProtectedAccess.openTemperatureGauge() {
        vars["varbit.blast_furnace_temperature_reading"] = 50
        vars["varbit.blast_furnace_fuel_low_reading"] = 0
        vars["varbit.blast_furnace_broken_pipe_reading"] = 0
        ifOpenMain("interface.blast_furnace_temp_gauge")
        ifSetAnim(
            "component.blast_furnace_temp_gauge:temp_gauge_needle",
            ServerCacheManager.getAnim("seq.blast_furnace_temp_gauge51".asRSCM()),
        )
    }

    private suspend fun ProtectedAccess.fillBuckets() {
        if (inv.count("obj.bucket_empty") <= 0) {
            mes("You need an empty bucket to fill.")
            return
        }
        while (inv.count("obj.bucket_empty") > 0) {
            if (invReplace(inv, "obj.bucket_empty", 1, "obj.bucket_water").failure) {
                break
            }
            anim("seq.human_pickuptable_walkmerge")
            soundSynth("synth.TAP_FILL")
            mes("You fill the bucket with water.")
            delay(1)
        }
    }
}
