package org.rsmod.content.other.consumables.potion.toa

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.other.consumables.potion.restoreIfDrained
import org.rsmod.game.entity.Player

@Singleton
class ToaOverTimeEffect {
    fun applySilkDressing(access: ProtectedAccess) = with(access) {
        restoreIfDrained("stat.hitpoints", constant = 5, percent = 0)
        startPulses(SILK_PULSES, SILK_DRESSING_TIMER, interval = 5, count = 19, ToaBuffDisplay.Silk)
    }

    fun processSilkDressing(access: ProtectedAccess) = with(access) {
        processPulse(
            SILK_PULSES, SILK_DRESSING_TIMER, interval = 5, ToaBuffDisplay.Silk,
            "The effects of the silk dressing have worn off.",
        ) {
            restoreIfDrained("stat.hitpoints", constant = 5, percent = 0)
        }
    }

    fun applyBlessedCrystalScarab(access: ProtectedAccess) = with(access) {
        restoreIfDrained("stat.prayer", constant = 8, percent = 0)
        startPulses(SCARAB_PULSES, BLESSED_SCARAB_TIMER, interval = 4, count = 9, ToaBuffDisplay.Scarab)
    }

    fun processBlessedCrystalScarab(access: ProtectedAccess) = with(access) {
        processPulse(
            SCARAB_PULSES, BLESSED_SCARAB_TIMER, interval = 4, ToaBuffDisplay.Scarab,
            "The effects of the blessed crystal scarab have worn off.",
        ) {
            restoreIfDrained("stat.prayer", constant = 8, percent = 0)
        }
    }

    fun clear(player: Player) {
        clearPulses(player, SILK_PULSES, SILK_DRESSING_TIMER, ToaBuffDisplay.Silk)
        clearPulses(player, SCARAB_PULSES, BLESSED_SCARAB_TIMER, ToaBuffDisplay.Scarab)
    }

    private fun ProtectedAccess.startPulses(
        state: String,
        timer: String,
        interval: Int,
        count: Int,
        buff: ToaBuffDisplay,
    ) {
        VarPlayerIntMapSetter.set(player, state, count)
        player.clearTimer(timer)
        player.timer(timer, interval)
        buff.update(player, mapClock + count * interval)
    }

    private inline fun ProtectedAccess.processPulse(
        state: String,
        timer: String,
        interval: Int,
        buff: ToaBuffDisplay,
        expiryMessage: String,
        apply: ProtectedAccess.() -> Unit,
    ) {
        val remaining = player.vars[state]
        if (remaining <= 0) {
            clearPulses(player, state, timer, buff)
            return
        }
        apply()
        if (remaining == 1) {
            clearPulses(player, state, timer, buff)
            mes(expiryMessage)
        } else {
            VarPlayerIntMapSetter.set(player, state, remaining - 1)
            player.timer(timer, interval)
        }
    }

    private fun clearPulses(player: Player, state: String, timer: String, buff: ToaBuffDisplay) {
        val active = player.vars[state] > 0
        VarPlayerIntMapSetter.set(player, state, 0)
        player.clearTimer(timer)
        if (active) buff.update(player, 0)
    }

    companion object {
        const val SILK_DRESSING_TIMER = "timer.potion_toa_silk_dressing"
        const val BLESSED_SCARAB_TIMER = "timer.potion_toa_blessed_crystal_scarab"
        internal const val SILK_PULSES = "varp.consumables_toa_silk_pulses"
        internal const val SCARAB_PULSES = "varp.consumables_toa_scarab_pulses"
    }
}
