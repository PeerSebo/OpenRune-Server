package org.rsmod.content.other.xprates

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player

internal object XpRateState {
    fun migrate(player: Player) {
        for (field in Field.entries) migrate(player, field)
    }

    fun personalRateHundredths(player: Player): Int? = get(player, Field.PersonalRate)

    fun setPersonalRateHundredths(player: Player, value: Int) = set(player, Field.PersonalRate, value)

    fun levelsResetStamp(player: Player): Int? = get(player, Field.LevelsReset)

    fun markLevelsReset(player: Player) = set(player, Field.LevelsReset, 1)

    private fun get(player: Player, field: Field): Int? {
        migrate(player, field)
        return if (field.symbol in player.vars) player.vars[field.symbol] else null
    }

    private fun set(player: Player, field: Field, value: Int) {
        migrate(player, field)
        VarPlayerIntMapSetter.set(player, field.symbol, value)
    }

    private fun migrate(player: Player, field: Field) {
        val legacy = AttributeKey<Int>(persistenceKey = field.nameInSave)
        if (player.vars[field.migrated] == 0) {
            if (field.symbol !in player.vars) {
                player.attr[legacy]?.let { VarPlayerIntMapSetter.set(player, field.symbol, it) }
            }
            VarPlayerIntMapSetter.set(player, field.migrated, 1)
        }
        if (player.attr.has(legacy)) player.attr.remove(legacy)
    }

    private enum class Field(val nameInSave: String, val migrated: String) {
        PersonalRate("xp_rates_personal_hundredths", "varp.xp_rates_personal_migrated"),
        LevelsReset("xp_rates_levels_reset", "varp.xp_rates_levels_reset_migrated");

        val symbol = "varp.$nameInSave"
    }
}
