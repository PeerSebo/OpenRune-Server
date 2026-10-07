package org.rsmod.content.other.consumables.potion.moons

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.clearPositiveStatBoost
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.intVarp
import org.rsmod.content.other.consumables.potion.restoreIfDrained
import org.rsmod.game.entity.Player

internal var Player.moonlightExpiresAt by intVarp("varp.consumables_moonlight_expires_at")

@Singleton
class MoonlightPotionEffect {
    fun handles(handler: String): Boolean = handler == "moonlight_potion"

    fun apply(access: ProtectedAccess) = with(access) {
        val herblore = player.stat("stat.herblore")
        boost("stat.attack", herblore, standardLevel = 3, superLevel = 45)
        boost("stat.strength", herblore, standardLevel = 12, superLevel = 55)
        if (herblore >= ENHANCED_DEFENCE_LEVEL) {
            statBoost("stat.defence", constant = 7, percent = 20)
            player.moonlightExpiresAt = mapClock + DURATION
            for (timer in listOf(MOONLIGHT_TIMER, DIVINE_DEFENCE_TIMER)) {
                VarPlayerIntMapSetter.set(player, timer, 0)
                VarPlayerIntMapSetter.set(player, timer, DURATION)
            }
            player.clearTimer(TIMER)
            player.timer(TIMER, DURATION)
        } else {
            boost("stat.defence", herblore, standardLevel = 30, superLevel = 66)
        }
        if (herblore >= PRAYER_LEVEL) {
            val restored = maxOf(player.statBase("stat.prayer") * 25 / 100, herblore * 30 / 100) + 7
            restoreIfDrained("stat.prayer", constant = restored, percent = 0)
        }
    }

    fun process(access: ProtectedAccess) = with(access) {
        val expiry = player.moonlightExpiresAt
        if (expiry <= 0 || expiry <= mapClock) clear(player)
        else player.timer(TIMER, expiry - mapClock)
    }

    fun clear(player: Player) {
        val active = player.moonlightExpiresAt > 0 || player.vars[MOONLIGHT_TIMER] > 0
        player.moonlightExpiresAt = 0
        player.clearTimer(TIMER)
        VarPlayerIntMapSetter.set(player, MOONLIGHT_TIMER, 0)
        if (active) {
            VarPlayerIntMapSetter.set(player, DIVINE_DEFENCE_TIMER, 0)
            player.clearPositiveStatBoost("stat.defence")
        }
    }

    private fun ProtectedAccess.boost(stat: String, herblore: Int, standardLevel: Int, superLevel: Int) {
        when {
            herblore >= superLevel -> statBoost(stat, constant = 5, percent = 15)
            herblore >= standardLevel -> statBoost(stat, constant = 3, percent = 10)
        }
    }

    companion object {
        const val TIMER = "timer.potion_moonlight"
        private const val MOONLIGHT_TIMER = "varbit.moonlight_potion_time"
        private const val DIVINE_DEFENCE_TIMER = "varbit.divinedefence_potion_time"
        private const val ENHANCED_DEFENCE_LEVEL = 70
        private const val PRAYER_LEVEL = 38
        private const val DURATION = 500
    }
}
