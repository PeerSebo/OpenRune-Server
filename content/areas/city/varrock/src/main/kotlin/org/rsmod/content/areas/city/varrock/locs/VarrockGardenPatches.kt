package org.rsmod.content.areas.city.varrock.locs

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.VarBitType
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.LocIdCodec
import dev.openrune.types.dbcol.column
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class VarrockGardenPatches @Inject constructor(
    private val objects: ObjRepository,
    private val xpModifiers: XpModifiers,
) : PluginScript() {
    override fun ScriptContext.startup() {
        val patches = DbHelper.table("dbtable.varrock_garden_patches").map {
            it.column("dbcol.varrock_garden_patches:loc", LocIdCodec)
        }
        val corners = DbHelper.table("dbtable.varrock_garden_corners").map {
            it.column("dbcol.varrock_garden_corners:corner", LocIdCodec)
        }
        for (id in (patches + corners).distinct()) {
            val symbol = RSCM.getReverseMapping(RSCMType.LOC, id)
            val type = checkNotNull(ServerCacheManager.getObject(id))
            val state = checkNotNull(ServerCacheManager.getVarbit(type.multiVarBit))
            onOpLoc1(symbol) { rake(it.loc, state) }
            onOpLoc2(symbol) { inspect(state) }
            onOpLocU(symbol, "obj.rake") { rake(it.loc, state) }
        }
    }

    internal suspend fun ProtectedAccess.rake(loc: BoundLocInfo, state: VarBitType) {
        if (player.vars[state] !in 0 until CLEARED) {
            inspect(state)
            return
        }
        if (inv.count("obj.rake") == 0) {
            mes("You need a rake to clear this patch.")
            return
        }
        faceLoc(loc)
        try {
            while (player.vars[state] in 0 until CLEARED && inv.count("obj.rake") > 0) {
                anim("seq.farming_raking")
                soundSynth("synth.FARMING_RAKING")
                delay(3)
                if (player.vars[state] !in 0 until CLEARED || inv.count("obj.rake") == 0) break
                VarPlayerIntMapSetter.set(player, state, player.vars[state] + 1)
                invAddOrDrop(objects, "obj.weeds", 1)
                statAdvance("stat.farming", 4.0 * xpModifiers.get(player, "stat.farming"))
                spam("You rake some weeds from the patch.")
            }
        } finally {
            resetAnim()
        }
    }

    private fun ProtectedAccess.inspect(state: VarBitType) {
        mes(when (player.vars[state]) {
            in 0 until CLEARED -> "This patch is overgrown with weeds and needs raking."
            CLEARED -> "This patch is clear of weeds."
            else -> "Something is growing in this patch."
        })
    }

    private companion object {
        const val CLEARED = 3
    }
}
