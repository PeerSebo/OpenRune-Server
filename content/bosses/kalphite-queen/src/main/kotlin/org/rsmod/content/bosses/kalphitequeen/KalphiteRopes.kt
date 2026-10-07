package org.rsmod.content.bosses.kalphitequeen

import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player

internal object KalphiteRopes {
    enum class Attachment { Attached, AlreadyAttached, MissingRope }

    fun attach(player: Player, flag: String): Attachment {
        if (player.vars[flag] == 1) return Attachment.AlreadyAttached
        if (player.inv.count("obj.rope") == 0) return Attachment.MissingRope
        if (!player.invDel(player.inv, "obj.rope", 1, ignoreVirtualStorage = true).success) return Attachment.MissingRope
        VarPlayerIntMapSetter.set(player, flag, 1)
        syncChamber(player)
        return Attachment.Attached
    }

    fun leave(player: Player, flag: String) {
        if (!permanent(player)) VarPlayerIntMapSetter.set(player, flag, 0)
        syncChamber(player)
    }

    fun login(player: Player) {
        if (!permanent(player)) {
            VarPlayerIntMapSetter.set(player, KalphiteLairAccess.SURFACE_ROPE, 0)
            VarPlayerIntMapSetter.set(player, KalphiteLairAccess.CHAMBER_ROPE, 0)
        }
        syncChamber(player)
    }

    private fun permanent(player: Player) = player.vars[KalphiteLairAccess.HARD_DIARY] == 1 || player.vars[KalphiteLairAccess.ELITE_DIARY] == 1
    private fun syncChamber(player: Player) {
        VarPlayerIntMapSetter.set(player, "varbit.kalphite_chamber_entrance", player.vars[KalphiteLairAccess.CHAMBER_ROPE])
        VarPlayerIntMapSetter.set(player, "varbit.kalphite_chamber_entrance_rope", 0)
    }
}
