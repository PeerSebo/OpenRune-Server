package org.rsmod.api.mechanics.toxins

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.boolVarp
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.timerAt

public object ToxinImmunity {
    private var Player.poisonUntil by intVarp("varp.toxin_poison_immunity_until")
    private var Player.venomUntil by intVarp("varp.toxin_venom_immunity_until")
    private var Player.warnedPoisonUntil by intVarp("varp.toxin_poison_immunity_warned_until")
    private var Player.poisonDisplayUntil by intVarp("varp.toxin_poison_immunity_display_until")
    private var Player.savedPoisonTicks by intVarp("varp.toxin_poison_immunity_remaining")
    private var Player.savedVenomTicks by intVarp("varp.toxin_venom_immunity_remaining")
    private var Player.savedPoisonWarning by boolVarp("varp.toxin_poison_immunity_warned")
    private var Player.immunityMigrated by boolVarp("varp.toxin_immunity_migrated")

    public fun hasPoisonImmunity(player: Player): Boolean =
        remaining(player.poisonUntil, player.currentMapClock) > 0

    public fun hasVenomImmunity(player: Player): Boolean =
        remaining(player.venomUntil, player.currentMapClock) > 0

    public fun grantImmunity(player: Player, poisonDuration: Int, venomDuration: Int = 0) {
        if (poisonDuration <= 0 && venomDuration <= 0) return
        val now = player.currentMapClock
        if (poisonDuration > 0) {
            val expiresAt = now + poisonDuration
            if (expiresAt > player.poisonUntil) {
                player.poisonUntil = expiresAt
                player.warnedPoisonUntil = 0
            }
        }
        if (venomDuration > 0) {
            player.venomUntil = maxOf(player.venomUntil, now + venomDuration)
        }
        restartImmunityDisplay(player, now)
        rearmTimer(player, now)
    }

    public fun onTimer(player: Player) {
        val now = player.currentMapClock
        val venomExpiry = player.venomUntil
        val venomExpired = venomExpiry > 0 && venomExpiry <= now
        if (venomExpired) player.venomUntil = 0

        val poisonDisplayExpiry = player.poisonDisplayUntil
        val poisonDisplayExpired = poisonDisplayExpiry > 0 && poisonDisplayExpiry <= now
        if (poisonDisplayExpired) player.poisonDisplayUntil = 0

        val poisonExpiry = player.poisonUntil
        if (poisonExpiry > 0 && poisonExpiry <= now) {
            player.poisonUntil = 0
            player.warnedPoisonUntil = 0
            player.poisonDisplayUntil = 0
            snapshot(player, now)
            Toxin.syncStatusOrbs(player, clock = now)
            player.mes("Your poison resistance has worn off.")
            rearmTimer(player, now)
            return
        }
        if (poisonExpiry > 0 && poisonExpiry - now <= WARNING_LEAD &&
            player.warnedPoisonUntil != poisonExpiry
        ) {
            player.warnedPoisonUntil = poisonExpiry
            player.mes("Your poison resistance is about to wear off.")
        }
        snapshot(player, now)
        val venomActive = remaining(player.venomUntil, now) > 0
        if (poisonExpiry > 0 && !venomActive && (venomExpired || poisonDisplayExpired)) {
            restartImmunityDisplay(player, now)
        } else {
            Toxin.syncStatusOrbs(player, clock = now)
        }
        rearmTimer(player, now)
    }

    public fun clear(player: Player) {
        clearRuntime(player)
        player.savedPoisonTicks = 0
        player.savedVenomTicks = 0
        player.savedPoisonWarning = false
        player.immunityMigrated = true
        migrateLegacyState(player)
        Toxin.syncStatusOrbs(player)
    }

    internal fun statusOrbValue(player: Player, clock: Int = player.currentMapClock): Int? {
        val venomRemaining = remaining(player.venomUntil, clock)
        if (venomRemaining > 0) return -(VENOM_IMMUNITY_OFFSET + timerUnits(venomRemaining))
        val poisonRemaining = remaining(player.poisonUntil, clock)
        if (poisonRemaining <= 0) return null
        return -timerUnits(poisonRemaining).coerceAtMost(VENOM_IMMUNITY_OFFSET)
    }

    private fun timerUnits(remaining: Int): Int =
        (remaining + IMMUNITY_UNIT_TICKS - 1) / IMMUNITY_UNIT_TICKS

    private fun restartImmunityDisplay(player: Player, clock: Int) {
        Toxin.syncStatusOrbs(player, restartAntipoisonBuff = true, clock = clock)
        val poisonRemaining = remaining(player.poisonUntil, clock)
        val venomRemaining = remaining(player.venomUntil, clock)
        player.poisonDisplayUntil = if (poisonRemaining <= 0 || venomRemaining > 0) {
            0
        } else {
            clock + minOf(poisonRemaining, MAX_POISON_DISPLAY_TICKS)
        }
    }

    private fun rearmTimer(player: Player, clock: Int) {
        val delays = mutableListOf<Int>()
        val poisonExpiry = player.poisonUntil
        if (poisonExpiry > clock) {
            val remaining = poisonExpiry - clock
            delays += if (player.warnedPoisonUntil != poisonExpiry && remaining > WARNING_LEAD) {
                remaining - WARNING_LEAD
            } else {
                remaining
            }
        }
        remaining(player.venomUntil, clock).takeIf { it > 0 }?.let(delays::add)
        remaining(player.poisonDisplayUntil, clock).takeIf { it > 0 }?.let(delays::add)
        player.clearTimer(TIMER)
        val delay = delays.minOrNull() ?: return
        player.timerAt(TIMER, mapClock = clock, cycles = delay.coerceAtLeast(1))
    }

    private fun remaining(expiry: Int, clock: Int): Int = (expiry - clock).coerceAtLeast(0)

    internal fun onLogin(player: Player, clock: Int) {
        migrateLegacyState(player)
        val poisonRemaining = player.savedPoisonTicks
        val venomRemaining = player.savedVenomTicks
        if (poisonRemaining > 0) {
            player.poisonUntil = clock + poisonRemaining
            player.warnedPoisonUntil = if (player.savedPoisonWarning) player.poisonUntil else 0
        } else {
            player.poisonUntil = 0
            player.warnedPoisonUntil = 0
            player.savedPoisonTicks = 0
            player.savedPoisonWarning = false
        }
        if (venomRemaining > 0) {
            player.venomUntil = clock + venomRemaining
        } else {
            player.venomUntil = 0
            player.savedVenomTicks = 0
        }
        restartImmunityDisplay(player, clock)
        rearmTimer(player, clock)
    }

    internal fun onLogout(player: Player) {
        snapshot(player, player.currentMapClock)
    }

    private fun snapshot(player: Player, clock: Int) {
        val poisonRemaining = remaining(player.poisonUntil, clock)
        player.savedPoisonTicks = poisonRemaining
        player.savedVenomTicks = remaining(player.venomUntil, clock)
        player.savedPoisonWarning = poisonRemaining > 0 && player.warnedPoisonUntil == player.poisonUntil
    }

    private fun clearRuntime(player: Player) {
        player.poisonUntil = 0
        player.venomUntil = 0
        player.warnedPoisonUntil = 0
        player.poisonDisplayUntil = 0
        player.clearTimer(TIMER)
    }

    private fun migrateLegacyState(player: Player) {
        val shouldImport = !player.immunityMigrated
        for ((symbol, name) in listOf(
            "varp.toxin_poison_immunity_remaining" to "mechanics.toxins.poison_immunity_remaining",
            "varp.toxin_venom_immunity_remaining" to "mechanics.toxins.venom_immunity_remaining",
        )) {
            val legacy = AttributeKey<Int>(persistenceKey = name)
            val value = player.attr[legacy]
            if (shouldImport && value != null && symbol !in player.vars) {
                VarPlayerIntMapSetter.set(player, symbol, value.coerceAtLeast(0))
            }
            player.attr.remove(legacy)
        }
        val warning = AttributeKey<Boolean>(persistenceKey = "mechanics.toxins.poison_immunity_warned")
        if (shouldImport && "varp.toxin_poison_immunity_warned" !in player.vars) {
            player.savedPoisonWarning = player.attr[warning] == true
        }
        player.attr.remove(warning)
        if (shouldImport) player.immunityMigrated = true
    }

    public const val TIMER: String = "timer.toxins"
    private const val IMMUNITY_UNIT_TICKS: Int = 30
    private const val WARNING_LEAD: Int = 50
    private const val VENOM_IMMUNITY_OFFSET: Int = 38
    private const val MAX_POISON_DISPLAY_TICKS: Int = VENOM_IMMUNITY_OFFSET * IMMUNITY_UNIT_TICKS
}
