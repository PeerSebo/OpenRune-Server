package org.rsmod.content.other.consumables.potion.toa

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.restoreRunEnergyPercent
import org.rsmod.api.player.stat.statRestore
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.boolVarp
import org.rsmod.api.player.vars.intVarp
import org.rsmod.content.other.consumables.potion.nextTimedEffectDelay
import org.rsmod.game.entity.Player

internal var Player.toaSaltsExpiresAt by intVarp("varp.consumables_toa_salts_expires_at")
internal var Player.toaSaltsWarned by boolVarp("varp.consumables_toa_salts_warned")

@Singleton
class ToaSmellingSaltsEffect {
    fun apply(access: ProtectedAccess) = with(access) {
        refreshEffects()
        player.toaSaltsExpiresAt = mapClock + DURATION
        player.toaSaltsWarned = false
        VarPlayerIntMapSetter.set(player, STATS_TIMER, 0)
        VarPlayerIntMapSetter.set(player, STATS_TIMER, DURATION / REFRESH_INTERVAL)
        // Salts reads its countdown from STATS_TIMER; the script only restarts the display.
        ToaBuffDisplay.Salts.update(player, 0)
        player.clearTimer(TIMER)
        player.timer(TIMER, REFRESH_INTERVAL)
    }

    fun process(access: ProtectedAccess) = with(access) {
        val expiry = player.toaSaltsExpiresAt
        if (expiry <= 0) {
            clear(player)
            return@with
        }
        val remaining = expiry - mapClock
        if (remaining <= 0) {
            clear(player)
            mes("The effects of the smelling salts have worn off.")
            return@with
        }
        if (!player.toaSaltsWarned && remaining <= WARNING_LEAD) {
            player.toaSaltsWarned = true
            mes("The effects of the smelling salts will wear off in 10 seconds.")
        }
        refreshEffects()
        VarPlayerIntMapSetter.set(player, STATS_TIMER, (remaining + REFRESH_INTERVAL - 1) / REFRESH_INTERVAL)
        player.timer(
            TIMER,
            nextTimedEffectDelay(remaining, REFRESH_INTERVAL, WARNING_LEAD, player.toaSaltsWarned),
        )
    }

    fun clear(player: Player) {
        val active = player.toaSaltsExpiresAt > 0
        player.toaSaltsExpiresAt = 0
        player.toaSaltsWarned = false
        player.clearTimer(TIMER)
        VarPlayerIntMapSetter.set(player, STATS_TIMER, 0)
        if (active) BOOSTED_STATS.forEach { player.statRestore(it) }
    }

    private fun ProtectedAccess.refreshEffects() {
        BOOSTED_STATS.forEach { statBoost(it, constant = 11, percent = 16) }
        player.restoreRunEnergyPercent(25)
    }

    companion object {
        const val TIMER = "timer.potion_toa_smelling_salts"
        private const val STATS_TIMER = "varbit.toa_midraidloot_stats_timer"
        private const val WARNING_LEAD = 17
        private const val REFRESH_INTERVAL = 25
        private const val DURATION = 800
        private val BOOSTED_STATS = listOf(
            "stat.attack", "stat.strength", "stat.defence", "stat.ranged", "stat.magic",
        )
    }
}
