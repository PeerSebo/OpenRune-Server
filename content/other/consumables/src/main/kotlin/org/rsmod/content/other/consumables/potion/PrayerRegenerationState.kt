package org.rsmod.content.other.consumables.potion

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.boolVarp
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player

internal var Player.prayerRegenerationPulses by intVarp("varp.consumables_prayer_regeneration_pulses")
internal var Player.prayerRegenerationNextPulseAt by intVarp("varp.consumables_prayer_regeneration_next_pulse_at")
internal var Player.savedPrayerRegenerationPulses by intVarp("varp.consumables_prayer_regeneration_remaining_pulses")
internal var Player.savedPrayerRegenerationDelay by intVarp("varp.consumables_prayer_regeneration_remaining_delay")
private var Player.prayerRegenerationMigrated by boolVarp("varp.consumables_prayer_regeneration_migrated")

internal object PrayerRegenerationState {
    private val legacyPulses = AttributeKey<Int>("consumables.potion.prayer_regeneration_pulses")
    private val legacyDelay = AttributeKey<Int>("consumables.potion.prayer_regeneration_next_pulse")

    fun migrate(player: Player) {
        if (!player.prayerRegenerationMigrated) {
            if ("varp.consumables_prayer_regeneration_remaining_pulses" !in player.vars) {
                player.attr[legacyPulses]?.let { player.savedPrayerRegenerationPulses = it.coerceAtLeast(0) }
            }
            if ("varp.consumables_prayer_regeneration_remaining_delay" !in player.vars) {
                val delay = player.attr[legacyDelay] ?: PrayerRegenerationEffectService.INTERVAL
                player.savedPrayerRegenerationDelay = delay.coerceIn(1, PrayerRegenerationEffectService.INTERVAL)
            }
            player.prayerRegenerationMigrated = true
        }
        removeLegacy(player)
    }

    fun clear(player: Player) {
        player.prayerRegenerationPulses = 0
        player.prayerRegenerationNextPulseAt = 0
        player.savedPrayerRegenerationPulses = 0
        player.savedPrayerRegenerationDelay = 0
        player.prayerRegenerationMigrated = true
        removeLegacy(player)
        VarPlayerIntMapSetter.set(player, PrayerRegenerationEffectService.DISPLAY_VARBIT, 0)
        player.clearTimer(PrayerRegenerationEffectService.TIMER)
    }

    private fun removeLegacy(player: Player) {
        if (player.attr.has(legacyPulses)) player.attr.remove(legacyPulses)
        if (player.attr.has(legacyDelay)) player.attr.remove(legacyDelay)
    }
}
