package org.rsmod.content.areas.city.draynor

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.dbcol.DbColumnCodec.CoordGridCodec
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.LocIdCodec
import dev.openrune.types.dbcol.column
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DraynorSewer @Inject constructor(private val locs: LocRepository) : PluginScript() {
    override fun ScriptContext.startup() {
        for (row in DbHelper.table("dbtable.draynor_sewer_routes")) {
            val type = row.column("dbcol.draynor_sewer_routes:loc", LocIdCodec)
            val origin = row.column("dbcol.draynor_sewer_routes:origin", CoordGridCodec)
            val destination = row.column("dbcol.draynor_sewer_routes:destination", CoordGridCodec)
            if (type == "loc.vampire_trap2".asRSCM()) {
                onOpLoc1("loc.vampire_trap1") { if (it.loc.coords == origin) open(it.loc) }
            }
            onOpLoc1(RSCM.getReverseMapping(RSCMType.LOC, type)) {
                if (it.loc.coords != origin) return@onOpLoc1
                arriveDelay()
                faceLoc(it.loc)
                anim("seq.human_reachforladder")
                delay(1)
                telejump(destination)
            }
        }
    }

    private suspend fun ProtectedAccess.open(trapdoor: BoundLocInfo) {
        arriveDelay()
        faceLoc(trapdoor)
        soundSynth("synth.TRAPDOOR_OPEN")
        locs.change(trapdoor, "loc.vampire_trap2", 500)
    }
}
