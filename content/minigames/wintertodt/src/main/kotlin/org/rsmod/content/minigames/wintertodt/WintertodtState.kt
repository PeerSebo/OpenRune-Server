package org.rsmod.content.minigames.wintertodt

import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player

internal object WintertodtState {
    const val Round = "varp.winter_round"
    const val Points = "varp.winter_points"
    const val ActionTick = "varp.winter_action_tick"
    const val RegenTick = "varp.winter_regen_tick"
    const val InterruptTick = "varp.winter_interrupt_tick"
    const val PendingObj = "varp.winter_pending_obj"
    const val PendingCount = "varp.winter_pending_count"
    const val PendingNoted = "varp.winter_pending_noted"
    const val Warmth = "varbit.wint_warmth"
    const val RewardPool = "varbit.wint_reward_pool"
    fun set(player: Player, key: String, value: Int) = VarPlayerIntMapSetter.set(player, key, value)
    fun clearSession(player: Player) {
        for (key in listOf(Round, Points, ActionTick, RegenTick, InterruptTick)) set(player, key, 0)
    }
}
