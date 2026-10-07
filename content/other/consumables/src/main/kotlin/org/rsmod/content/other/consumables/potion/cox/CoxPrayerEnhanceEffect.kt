package org.rsmod.content.other.consumables.potion.cox

import jakarta.inject.Singleton
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.nullableIntVarp
import org.rsmod.game.entity.Player

@Singleton
class CoxPrayerEnhanceEffect {
    private var Player.expiresAt by nullableIntVarp("varp.consumables_cox_prayer_enhance_expires_at")
    private var Player.warned by boolVarBit("varbit.consumables_cox_prayer_enhance_warned")

    internal fun apply(
        access: ProtectedAccess,
        tier: CoxPotionTier,
    ) {
        with(access) {
            val activations = tier.prayerEnhanceActivations(player.statBase(PRAYER)).coerceAtLeast(1)
            val interval = (EFFECT_TICKS / activations).coerceIn(1, MAX_INTERVAL)

            player.expiresAt = mapClock + EFFECT_TICKS
            player.warned = false

            startDisplay(player, tier, activations, interval)

            player.clearTimer(TIMER)
            player.timer(TIMER, interval)
        }
    }

    internal fun process(
        access: ProtectedAccess,
    ) {
        with(access) {
            val expiresAt = player.expiresAt ?: return
            val remainingTicks = expiresAt - mapClock

            if (remainingTicks <= 0) {
                clear(player)

                mes("Your prayer enhance effect has worn off.")
                return
            }

            if (!player.warned && remainingTicks <= WARNING_LEAD) {
                mes("Your prayer enhance potion is about to expire.")
                player.warned = true
            }

            restorePrayer()

            val remainingActivations = player.vars[PRAYER_ENHANCE_TIMER_VARBIT] - 1

            VarPlayerIntMapSetter.set(player, PRAYER_ENHANCE_TIMER_VARBIT, remainingActivations.coerceAtLeast(0))

            if (remainingActivations <= 0) {
                clear(player)

                mes("Your prayer enhance effect has worn off.")
                return
            }

            val nextDelay = minOf(player.vars[PRAYER_ENHANCE_RATE_VARBIT], remainingTicks).coerceAtLeast(1)
            player.timer(TIMER, nextDelay)
        }
    }

    internal fun clear(
        player: Player,
    ) {
        player.expiresAt = null
        player.warned = false
        player.clearTimer(TIMER)

        VarPlayerIntMapSetter.set(player, PRAYER_ENHANCE_TIMER_VARBIT, 0)
        VarPlayerIntMapSetter.set(player, PRAYER_ENHANCE_RATE_VARBIT, 0)
        VarPlayerIntMapSetter.set(player, PRAYER_ENHANCE_TIER_VARBIT, 0)
    }

    private fun ProtectedAccess.restorePrayer() {
        if (player.stat(PRAYER) >= player.statBase(PRAYER)) return
        statHeal(stat = PRAYER, constant = RESTORE_PER_ACTIVATION, percent = 0)
    }

    private fun startDisplay(
        player: Player,
        tier: CoxPotionTier,
        activations: Int,
        interval: Int,
    ) {
        VarPlayerIntMapSetter.set(player, PRAYER_ENHANCE_TIMER_VARBIT, 0)
        VarPlayerIntMapSetter.set(player, PRAYER_ENHANCE_RATE_VARBIT, interval)
        VarPlayerIntMapSetter.set(player, PRAYER_ENHANCE_TIER_VARBIT, tier.clientTier)
        VarPlayerIntMapSetter.set(player, PRAYER_ENHANCE_TIMER_VARBIT, activations)
        val buff = when (tier.clientTier) {
            1 -> 4150
            2 -> 3087
            3 -> 4151
            else -> error("Invalid prayer-enhancement tier: ${tier.clientTier}")
        }
        player.runClientScript("clientscript.[clientscript,buff_bar_start]", buff, player.currentMapClock.coerceAtLeast(1))
    }

    companion object {
        const val TIMER = "timer.potion_cox_prayer_enhance"
        private const val PRAYER_ENHANCE_TIMER_VARBIT = "varbit.raids_prayerenhance_timer"
        private const val PRAYER_ENHANCE_RATE_VARBIT = "varbit.raids_prayerenhance_rate"
        private const val PRAYER_ENHANCE_TIER_VARBIT = "varbit.raids_prayer_enhance_tier"
        private const val EFFECT_TICKS = 500
        private const val MAX_INTERVAL = 63
        private const val RESTORE_PER_ACTIVATION = 1
        private const val PRAYER = "stat.prayer"
        private const val WARNING_LEAD = 50
    }
}
