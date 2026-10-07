package org.rsmod.content.drops

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dtx.core.RollResult
import dtx.core.Rollable
import dtx.core.singleRollable
import org.rsmod.api.config.refs.BaseParams
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.KillRollContext
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

public fun vestigeProgressRoll(varp: String, vestigeObj: String): Rollable<Player, DropRollItem> =
    singleRollable {
        selectResult { player, _ ->
            val progress = player.vars[varp] + 1
            if (progress >= VESTIGE_ROLLS_REQUIRED) {
                VarPlayerIntMapSetter.set(player, varp, 0)
                RollResult.Single(DropRollItem(vestigeObj, 1))
            } else {
                VarPlayerIntMapSetter.set(player, varp, progress)
                RollResult.Nothing()
            }
        }
    }

public fun Npc.isDt2AwakenedEncounter(): Boolean = vars["varn.awakened_state"] == 1

public fun dt2Drop(obj: String, regular: Int, awakened: Int): Rollable<Player, DropRollItem> =
    singleRollable {
        selectResult { _, otherArgs ->
            val awakenedKill = otherArgs[KillRollContext.npc]?.isDt2AwakenedEncounter() == true
            RollResult.Single(DropRollItem(obj, if (awakenedKill) awakened else regular))
        }
    }

public fun Player.shouldDropSanguineTorvaKit(npc: Npc, kitObj: String): Boolean {
    if (!npc.isDt2AwakenedEncounter()) return false
    val defeated = npc.paramOrNull(BaseParams.killcount_varp_awakened) ?: return false
    if (kitObj in inv || invMap["inv.bank"]?.let { kitObj in it } == true) return false
    return AWAKENED_KILLCOUNTS.filter { it.id != defeated.id }.all { vars[it] > 0 }
}

private val AWAKENED_KILLCOUNTS by lazy {
    listOf("duke_sucellus", "leviathan", "vardorvis", "whisperer").map { boss ->
        val id = "varp.total_${boss}_awakened_kills".asRSCM(RSCMType.VARP)
        checkNotNull(ServerCacheManager.getVarp(id))
    }
}

private const val VESTIGE_ROLLS_REQUIRED = 3
