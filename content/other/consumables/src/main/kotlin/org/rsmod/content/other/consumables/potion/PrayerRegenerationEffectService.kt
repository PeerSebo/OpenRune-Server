package org.rsmod.content.other.consumables.potion

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.timerAt

@Singleton
class PrayerRegenerationEffectService @Inject constructor(private val worldClock: MapClock) {
    fun apply(access: ProtectedAccess, duration: Int) {
        val pulses = duration / INTERVAL
        if (pulses <= 0) return
        val player = access.player
        PrayerRegenerationState.migrate(player)
        player.prayerRegenerationPulses = pulses
        player.prayerRegenerationNextPulseAt = access.mapClock + INTERVAL
        restartDisplay(player, pulses)
        access.runClientScript(BUFF_BAR_START, BUFF_STRUCT, access.mapClock.coerceAtLeast(1))
        player.clearTimer(TIMER)
        player.timer(TIMER, INTERVAL)
    }

    fun process(access: ProtectedAccess) {
        val player = access.player
        val pulses = player.prayerRegenerationPulses
        if (pulses <= 0) {
            clear(player)
            return
        }
        access.restoreIfDrained(stat = "stat.prayer", constant = 1, percent = 0)
        val remaining = pulses - 1
        if (remaining <= 0) {
            clear(player)
            access.mes("Your Prayer Regeneration potion has worn off.")
            return
        }
        player.prayerRegenerationPulses = remaining
        player.prayerRegenerationNextPulseAt = access.mapClock + INTERVAL
        VarPlayerIntMapSetter.set(player, DISPLAY_VARBIT, remaining)
        player.timer(TIMER, INTERVAL)
    }

    internal fun onLogin(player: Player) {
        PrayerRegenerationState.migrate(player)
        val pulses = player.savedPrayerRegenerationPulses
        if (pulses <= 0) {
            clear(player)
            return
        }
        val delay = player.savedPrayerRegenerationDelay.coerceIn(1, INTERVAL)
        player.prayerRegenerationPulses = pulses
        player.prayerRegenerationNextPulseAt = worldClock.cycle + delay
        restartDisplay(player, pulses)
        player.clearTimer(TIMER)
        player.timerAt(TIMER, worldClock.cycle, delay)
    }

    internal fun onLogout(player: Player) {
        PrayerRegenerationState.migrate(player)
        val pulses = player.prayerRegenerationPulses
        if (pulses <= 0) {
            clear(player)
            return
        }
        player.savedPrayerRegenerationPulses = pulses
        val next = player.prayerRegenerationNextPulseAt.takeIf { it != 0 }
            ?: (player.currentMapClock + INTERVAL)
        player.savedPrayerRegenerationDelay = (next - player.currentMapClock).coerceIn(1, INTERVAL)
        VarPlayerIntMapSetter.set(player, DISPLAY_VARBIT, pulses)
    }

    internal fun snapshot(player: Player) {
        val pulses = player.prayerRegenerationPulses.coerceAtLeast(0)
        player.savedPrayerRegenerationPulses = pulses
        val next = player.prayerRegenerationNextPulseAt.takeIf { it != 0 }
            ?: (player.currentMapClock + INTERVAL)
        player.savedPrayerRegenerationDelay = if (pulses == 0) 0 else (next - player.currentMapClock).coerceIn(1, INTERVAL)
    }

    fun clear(player: Player) {
        PrayerRegenerationState.clear(player)
    }

    private fun restartDisplay(player: Player, pulses: Int) {
        VarPlayerIntMapSetter.set(player, DISPLAY_VARBIT, 0)
        VarPlayerIntMapSetter.set(player, DISPLAY_VARBIT, pulses)
    }

    companion object {
        const val TIMER: String = "timer.potion_prayer_regeneration"
        const val DISPLAY_VARBIT: String = "varbit.prayer_regeneration_potion_timer"
        internal const val INTERVAL: Int = 12
        private const val BUFF_BAR_START: String = "clientscript.[clientscript,buff_bar_start]"
        private const val BUFF_STRUCT: Int = 1014
    }
}
