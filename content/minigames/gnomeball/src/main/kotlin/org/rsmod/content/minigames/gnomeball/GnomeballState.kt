package org.rsmod.content.minigames.gnomeball

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.utils.bits.getBits
import org.rsmod.utils.bits.withBits

internal object GnomeballState {
    const val Session = "varp.gnomeball_session"
    const val Phase = "varp.gnomeball_phase"
    const val Cooldown = "varp.gnomeball_cooldown"
    const val Brought = "varp.gnomeball_brought_ball"
    const val Score = "varbit.gnomeball_score"
    const val Deposit = "inv.gnomeball_deposit"
    const val NpcCooldown = "varn.gnomeball_cooldown"
    const val NpcDeadline = "varn.gnomeball_ball_deadline"
    const val Ball = "obj.ball_gnomeball_game"
    const val Empty = 0
    const val Holding = 1
    const val Flying = 2
    const val Stolen = 3
    const val Won = 4
    private val loanBits by lazy { checkNotNull(ServerCacheManager.getVarObj("varobj.gnomeball_loan".asRSCM())).bits }

    fun active(player: Player) = player.vars[Session] == 1
    fun set(player: Player, name: String, value: Int) = VarPlayerIntMapSetter.set(player, name, value)
    fun loan() = InvObj(Ball, vars = 0.withBits(loanBits, 1))
    fun loan(obj: InvObj?) = obj != null && obj.id == Ball.asRSCM() && obj.vars.getBits(loanBits) == 1
    fun clear(player: Player) {
        for (key in listOf(Session, Phase, Cooldown, Brought, Score)) set(player, key, 0)
    }
}
