package org.rsmod.content.other.consumables.potion

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.other.consumables.heart.HeartEffectService
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.timerAt

@Singleton
class MenaphiteRemedyEffectService
@Inject
constructor(
    private val heartEffects: HeartEffectService,
    private val worldClock: MapClock,
) {
    fun apply(access: ProtectedAccess) {
        with(access) {
            MenaphiteRemedyState.migrate(player)
            heartEffects.cancelMaintainedBoost(player)
            restoreStats()
            player.menaphiteRemedyExpiresAt = mapClock + DURATION
            player.savedMenaphiteRemedyTicks = DURATION
            player.clearTimer(TIMER)
            player.timer(TIMER, INTERVAL)
        }
    }

    fun process(access: ProtectedAccess) {
        with(access) {
            val remaining = player.menaphiteRemedyExpiresAt - mapClock
            if (player.menaphiteRemedyExpiresAt == 0 || remaining <= 0) {
                MenaphiteRemedyState.clear(player)
                return
            }
            restoreStats()
            player.timer(TIMER, minOf(INTERVAL, remaining))
        }
    }

    internal fun onLogin(player: Player) {
        MenaphiteRemedyState.migrate(player)
        val remaining = player.savedMenaphiteRemedyTicks
        if (remaining <= 0) {
            MenaphiteRemedyState.clear(player)
            return
        }
        val clock = worldClock.cycle
        player.menaphiteRemedyExpiresAt = clock + remaining
        player.clearTimer(TIMER)
        player.timerAt(TIMER, clock, minOf(INTERVAL, remaining))
    }

    internal fun onLogout(player: Player) {
        MenaphiteRemedyState.migrate(player)
        val remaining = player.menaphiteRemedyExpiresAt - player.currentMapClock
        if (player.menaphiteRemedyExpiresAt == 0 || remaining <= 0) {
            MenaphiteRemedyState.clear(player)
        } else {
            player.savedMenaphiteRemedyTicks = remaining
        }
    }

    internal fun snapshot(player: Player) {
        val expiresAt = player.menaphiteRemedyExpiresAt
        player.savedMenaphiteRemedyTicks = if (expiresAt == 0) 0 else (expiresAt - player.currentMapClock).coerceAtLeast(0)
    }

    private fun ProtectedAccess.restoreStats() {
        for (stat in RESTORED_STATS) restoreIfDrained(stat, constant = 6, percent = 16)
    }

    companion object {
        const val TIMER = "timer.potion_menaphite_remedy"
        private const val DURATION = 500
        private const val INTERVAL = 25
        private val RESTORED_STATS =
            listOf("stat.attack", "stat.strength", "stat.defence", "stat.ranged", "stat.magic")
    }
}
