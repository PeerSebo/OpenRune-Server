package org.rsmod.content.other.consumables.potion

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player

internal enum class AntifireState(
    val key: String,
    val timer: String,
    val displayVarbit: String,
    val countdownInterval: Int,
    val buffStruct: Int,
    val potionName: String,
) {
    NORMAL(
        "antifire", AntifireEffectService.ANTIFIRE_TIMER, AntifireEffectService.ANTIFIRE_VARBIT,
        30, 3102, "antifire potion",
    ),
    SUPER(
        "super_antifire", AntifireEffectService.SUPER_ANTIFIRE_TIMER,
        AntifireEffectService.SUPER_ANTIFIRE_VARBIT, 20, 3103, "super antifire potion",
    );

    val expiryVarp: String = "varp.consumables_${key}_expires_at"
    val remainingVarp: String = "varp.consumables_${key}_remaining"
    val migratedVarp: String = "varp.consumables_${key}_migrated"
    private val legacyRemaining = AttributeKey<Int>("consumables.potion.${key}_remaining")

    fun expiresAt(player: Player): Int = player.vars[expiryVarp]

    fun setExpiresAt(player: Player, cycle: Int) {
        VarPlayerIntMapSetter.set(player, expiryVarp, cycle)
    }

    fun remaining(player: Player): Int = player.vars[remainingVarp]

    fun setRemaining(player: Player, ticks: Int) {
        VarPlayerIntMapSetter.set(player, remainingVarp, ticks.coerceAtLeast(0))
    }

    fun migrate(player: Player) {
        if (player.vars[migratedVarp] == 0) {
            val remaining = player.attr[legacyRemaining]
            if (remaining != null && remainingVarp !in player.vars) setRemaining(player, remaining)
            VarPlayerIntMapSetter.set(player, migratedVarp, 1)
        }
        if (player.attr.has(legacyRemaining)) player.attr.remove(legacyRemaining)
    }

    fun clear(player: Player) {
        setExpiresAt(player, 0)
        setRemaining(player, 0)
        VarPlayerIntMapSetter.set(player, migratedVarp, 1)
        if (player.attr.has(legacyRemaining)) player.attr.remove(legacyRemaining)
        VarPlayerIntMapSetter.set(player, displayVarbit, 0)
        player.clearTimer(timer)
    }

    companion object {
        fun forProtection(fullProtection: Boolean): AntifireState = if (fullProtection) SUPER else NORMAL
    }
}
