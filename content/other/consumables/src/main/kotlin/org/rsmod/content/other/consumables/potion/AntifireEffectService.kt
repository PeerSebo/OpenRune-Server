package org.rsmod.content.other.consumables.potion

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.table.PotionEffectRow
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.timerAt

@Singleton
class AntifireEffectService @Inject constructor(private val worldClock: MapClock) {
    fun apply(access: ProtectedAccess, effect: PotionEffectRow) {
        if (effect.duration <= 0) return
        val state = AntifireState.forProtection(effect.fullProtection)
        val player = access.player
        state.migrate(player)
        state.setExpiresAt(player, access.mapClock + effect.duration)
        VarPlayerIntMapSetter.set(player, state.displayVarbit, 0)
        VarPlayerIntMapSetter.set(
            player, state.displayVarbit, durationUnits(effect.duration, state.countdownInterval),
        )
        access.runClientScript(BUFF_BAR_START, state.buffStruct, access.mapClock.coerceAtLeast(1))
        player.clearTimer(state.timer)
        player.timer(state.timer, warningDelay(effect.duration))
    }

    fun process(access: ProtectedAccess, fullProtection: Boolean) {
        val state = AntifireState.forProtection(fullProtection)
        val player = access.player
        val expiresAt = state.expiresAt(player)
        if (expiresAt == 0) {
            state.clear(player)
            return
        }
        val remaining = expiresAt - access.mapClock
        if (remaining <= 0) {
            state.clear(player)
            access.mes("Your ${state.potionName} has expired.")
            return
        }
        if (remaining > WARNING_LEAD) {
            player.timer(state.timer, remaining - WARNING_LEAD)
            return
        }
        access.mes("Your ${state.potionName} is about to expire.")
        player.timer(state.timer, remaining)
    }

    internal fun onLogin(player: Player) {
        for (state in AntifireState.entries) {
            state.migrate(player)
            val remaining = state.remaining(player)
            if (remaining <= 0) {
                state.clear(player)
                continue
            }
            state.setExpiresAt(player, worldClock.cycle + remaining)
            VarPlayerIntMapSetter.set(
                player, state.displayVarbit, durationUnits(remaining, state.countdownInterval),
            )
            player.clearTimer(state.timer)
            player.timerAt(state.timer, worldClock.cycle, warningDelay(remaining))
        }
    }

    internal fun onLogout(player: Player) {
        for (state in AntifireState.entries) {
            state.migrate(player)
            val expiresAt = state.expiresAt(player)
            val remaining = if (expiresAt == 0) 0 else (expiresAt - player.currentMapClock).coerceAtLeast(0)
            state.setRemaining(player, remaining)
            VarPlayerIntMapSetter.set(
                player, state.displayVarbit, durationUnits(remaining, state.countdownInterval),
            )
        }
    }

    internal fun snapshot(player: Player) {
        for (state in AntifireState.entries) {
            val expiresAt = state.expiresAt(player)
            state.setRemaining(player, if (expiresAt == 0) 0 else (expiresAt - player.currentMapClock).coerceAtLeast(0))
        }
    }

    private fun warningDelay(remaining: Int): Int =
        if (remaining > WARNING_LEAD) remaining - WARNING_LEAD else remaining.coerceAtLeast(1)

    companion object {
        const val ANTIFIRE_TIMER: String = "timer.potion_antifire"
        const val SUPER_ANTIFIRE_TIMER: String = "timer.potion_super_antifire"
        const val ANTIFIRE_VARBIT: String = "varbit.antifire_potion"
        const val SUPER_ANTIFIRE_VARBIT: String = "varbit.super_antifire_potion"
        private const val BUFF_BAR_START = "clientscript.[clientscript,buff_bar_start]"
        private const val WARNING_LEAD = 25
    }
}
