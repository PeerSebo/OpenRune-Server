package org.rsmod.content.other.consumables.potion.toa

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.boolVarp
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.specials.energy.SpecialAttackEnergyModifier
import org.rsmod.game.entity.Player

internal var Player.toaAdrenalineExpiresAt by intVarp("varp.consumables_toa_adrenaline_expires_at")
internal var Player.toaAdrenalineWarned by boolVarp("varp.consumables_toa_adrenaline_warned")

@Singleton
class ToaLiquidAdrenalineEffect {
    fun apply(access: ProtectedAccess) = with(access) {
        SpecialAttackEnergyModifier.setCostDivisor(player, 2)
        player.toaAdrenalineExpiresAt = mapClock + DURATION
        player.toaAdrenalineWarned = false
        VarPlayerIntMapSetter.set(player, ENERGY_ACTIVE, 0)
        VarPlayerIntMapSetter.set(player, ENERGY_ACTIVE, 1)
        ToaBuffDisplay.Adrenaline.update(player, player.toaAdrenalineExpiresAt)
        player.clearTimer(TIMER)
        player.timer(TIMER, DURATION - WARNING_LEAD)
    }

    fun process(access: ProtectedAccess) = with(access) {
        val expiry = player.toaAdrenalineExpiresAt
        if (expiry <= 0) {
            clear(player)
            return@with
        }
        val remaining = expiry - mapClock
        if (remaining <= 0) {
            val active = SpecialAttackEnergyModifier.isActive(player)
            clear(player)
            if (active) mes("The effects of the liquid adrenaline have worn off.")
            return@with
        }
        if (!player.toaAdrenalineWarned && remaining <= WARNING_LEAD) {
            player.toaAdrenalineWarned = true
            mes("The effects of the liquid adrenaline will wear off in 10 seconds.")
        }
        val delay = if (player.toaAdrenalineWarned) remaining else remaining - WARNING_LEAD
        player.timer(TIMER, delay.coerceAtLeast(1))
    }

    fun clear(player: Player) {
        val active = player.toaAdrenalineExpiresAt > 0 || SpecialAttackEnergyModifier.isActive(player)
        player.toaAdrenalineExpiresAt = 0
        player.toaAdrenalineWarned = false
        player.clearTimer(TIMER)
        SpecialAttackEnergyModifier.clear(player)
        VarPlayerIntMapSetter.set(player, ENERGY_ACTIVE, 0)
        if (active) ToaBuffDisplay.Adrenaline.update(player, 0)
    }

    companion object {
        const val TIMER = "timer.potion_toa_liquid_adrenaline"
        private const val ENERGY_ACTIVE = "varbit.toa_midraidloot_energy_active"
        private const val WARNING_LEAD = 17
        private const val DURATION = 250
    }
}
