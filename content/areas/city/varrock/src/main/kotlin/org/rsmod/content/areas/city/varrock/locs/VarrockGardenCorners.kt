package org.rsmod.content.areas.city.varrock.locs

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.dbcol.DbColumnCodec.CoordGridCodec
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.LocIdCodec
import dev.openrune.types.dbcol.column
import dev.openrune.types.dbcol.multiColumn
import jakarta.inject.Inject
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onGameStartup
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.loc.LocLayerConstants.GROUND
import org.rsmod.routefinder.loc.LocShapeConstants.CENTREPIECE_STRAIGHT

class VarrockGardenCorners @Inject constructor(private val locs: LocRepository) : PluginScript() {
    override fun ScriptContext.startup() {
        onGameStartup { replaceCorners() }
    }

    internal fun replaceCorners() {
        val paving = "loc.garden_paving_curved".asRSCM()
        for (row in DbHelper.table("dbtable.varrock_garden_corners")) {
            val original = row.column("dbcol.varrock_garden_corners:patch", LocIdCodec)
            val corner = row.column("dbcol.varrock_garden_corners:corner", LocIdCodec)
            for (coords in row.multiColumn("dbcol.varrock_garden_corners:coords", CoordGridCodec)) {
                val existing = locs.findAll(coords).toList()
                val patch = existing.firstOrNull { it.layer == GROUND }
                if (patch != null && patch.id != original) continue
                val border = existing.firstOrNull { it.id == paving } ?: continue
                locs.add(LocInfo(GROUND, coords,
                    LocEntity(corner, patch?.shape?.id ?: CENTREPIECE_STRAIGHT, border.angle.id)), Int.MAX_VALUE)
            }
        }
    }
}
