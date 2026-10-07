package org.rsmod.content.other.consumables.potion

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.timerAt

@Singleton
class StaminaEffectService @Inject constructor(private val worldClock: MapClock) {
    fun apply(access: ProtectedAccess, duration: Int) {
        if (duration <= 0) return
        val player = access.player
        StaminaState.migrate(player)
        player.staminaExpiresAt = access.mapClock + duration
        VarPlayerIntMapSetter.set(player, ACTIVE_VARBIT, 1)
        VarPlayerIntMapSetter.set(player, DURATION_VARBIT, 0)
        VarPlayerIntMapSetter.set(player, DURATION_VARBIT, durationUnits(duration, COUNTDOWN_INTERVAL))
        access.runClientScript(BUFF_BAR_START, BUFF_STRUCT, access.mapClock.coerceAtLeast(1))
        player.clearTimer(TIMER)
        player.timer(TIMER, warningDelay(duration))
    }

    fun process(access: ProtectedAccess) {
        val player = access.player
        val expiresAt = player.staminaExpiresAt
        if (expiresAt == 0) {
            StaminaState.clear(player)
            return
        }
        val remaining = expiresAt - access.mapClock
        if (remaining <= 0) {
            StaminaState.clear(player)
            access.mes("Your stamina enhancement has expired.")
        } else if (remaining > WARNING_LEAD) {
            player.timer(TIMER, remaining - WARNING_LEAD)
        } else {
            access.mes("Your stamina enhancement is about to expire.")
            player.timer(TIMER, remaining)
        }
    }

    internal fun onLogin(player: Player) {
        StaminaState.migrate(player)
        val remaining = player.savedStaminaTicks
        if (remaining <= 0) {
            StaminaState.clear(player)
            return
        }
        player.staminaExpiresAt = worldClock.cycle + remaining
        syncDisplay(player, remaining)
        player.clearTimer(TIMER)
        player.timerAt(TIMER, worldClock.cycle, warningDelay(remaining))
    }

    internal fun onLogout(player: Player) {
        StaminaState.migrate(player)
        snapshot(player)
        syncDisplay(player, player.savedStaminaTicks)
    }

    internal fun snapshot(player: Player) {
        val expiresAt = player.staminaExpiresAt
        player.savedStaminaTicks = if (expiresAt == 0) 0 else (expiresAt - player.currentMapClock).coerceAtLeast(0)
    }

    private fun syncDisplay(player: Player, remaining: Int) {
        VarPlayerIntMapSetter.set(player, ACTIVE_VARBIT, if (remaining > 0) 1 else 0)
        VarPlayerIntMapSetter.set(player, DURATION_VARBIT, durationUnits(remaining, COUNTDOWN_INTERVAL))
    }

    private fun warningDelay(remaining: Int): Int =
        (if (remaining > WARNING_LEAD) remaining - WARNING_LEAD else remaining).coerceAtLeast(1)

    companion object {
        const val TIMER = "timer.potion_stamina"
        const val ACTIVE_VARBIT = "varbit.stamina_active"
        const val DURATION_VARBIT = "varbit.stamina_duration"
        private const val BUFF_BAR_START = "clientscript.[clientscript,buff_bar_start]"
        private const val BUFF_STRUCT = 3086
        private const val COUNTDOWN_INTERVAL = 10
        private const val WARNING_LEAD = 25
    }
}
