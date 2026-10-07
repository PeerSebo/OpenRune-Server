package org.rsmod.content.other.consumables.potion.overload

import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.other.consumables.potion.PotionStatBoost
import org.rsmod.game.entity.Player

internal class OverloadVars(prefix: String) {
    private val expiry = "varp.${prefix}_expires_at"
    private val constant = "varp.${prefix}_boost_constant"
    private val percent = "varp.${prefix}_boost_percent"
    private val damage = "varp.${prefix}_damage_hits"
    private val warned = "varp.${prefix}_warned"
    private val fields = listOf(expiry, constant, percent, damage, warned)

    fun read(player: Player): OverloadState? {
        if (expiry !in player.vars) return null
        return OverloadState(
            expiresAt = player.vars[expiry],
            boost = PotionStatBoost(player.vars[constant], player.vars[percent]),
            damageHitsRemaining = player.vars[damage],
            warned = player.vars[warned] != 0,
        )
    }

    fun write(player: Player, state: OverloadState) {
        VarPlayerIntMapSetter.set(player, expiry, state.expiresAt)
        VarPlayerIntMapSetter.set(player, constant, state.boost.constant)
        VarPlayerIntMapSetter.set(player, percent, state.boost.percent)
        VarPlayerIntMapSetter.set(player, damage, state.damageHitsRemaining)
        VarPlayerIntMapSetter.set(player, warned, if (state.warned) 1 else 0)
    }

    fun clear(player: Player) {
        fields.forEach { VarPlayerIntMapSetter.clear(player, it) }
    }
}
