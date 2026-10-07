package org.rsmod.content.other.emirsarena

import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player

internal object EmirsArenaState {
    private const val MIGRATED = "varp.emirs_arena_state_migrated"

    fun wins(player: Player, ranked: Boolean): Int =
        get(player, if (ranked) Field.RankedWins else Field.LegacyWins) ?: 0

    fun losses(player: Player, ranked: Boolean): Int =
        get(player, if (ranked) Field.RankedLosses else Field.LegacyLosses) ?: 0

    fun recordWin(player: Player, ranked: Boolean) {
        val field = if (ranked) Field.RankedWins else Field.LegacyWins
        set(player, field, wins(player, ranked) + 1)
    }

    fun recordLoss(player: Player, ranked: Boolean) {
        val field = if (ranked) Field.RankedLosses else Field.LegacyLosses
        set(player, field, losses(player, ranked) + 1)
    }

    fun winStreak(player: Player): Int = get(player, Field.WinStreak) ?: 0

    fun setWinStreak(player: Player, streak: Int) = set(player, Field.WinStreak, streak)

    fun savedPreset(player: Player): Int? = get(player, Field.SavedPreset)

    fun savePreset(player: Player, packed: Int) = set(player, Field.SavedPreset, packed)

    fun lastRules(player: Player): Int? = get(player, Field.LastRules)

    fun saveLastRules(player: Player, packed: Int) = set(player, Field.LastRules, packed)

    fun migrate(player: Player) {
        val shouldImport = player.vars[MIGRATED] == 0
        for (field in Field.entries) {
            val key = AttributeKey<Int>(persistenceKey = field.nameInSave)
            if (shouldImport && field.symbol !in player.vars) {
                player.attr[key]?.let { VarPlayerIntMapSetter.set(player, field.symbol, it) }
            }
            if (player.attr.has(key)) player.attr.remove(key)
        }
        if (shouldImport) VarPlayerIntMapSetter.set(player, MIGRATED, 1)
    }

    private fun get(player: Player, field: Field): Int? {
        migrate(player)
        return if (field.symbol in player.vars) player.vars[field.symbol] else null
    }

    private fun set(player: Player, field: Field, value: Int) {
        migrate(player)
        VarPlayerIntMapSetter.set(player, field.symbol, value)
    }

    private enum class Field(val nameInSave: String) {
        LegacyWins("emirs_arena_legacy_wins"),
        LegacyLosses("emirs_arena_legacy_losses"),
        RankedWins("emirs_arena_ranked_wins"),
        RankedLosses("emirs_arena_ranked_losses"),
        WinStreak("emirs_arena_win_streak"),
        SavedPreset("emirs_arena_duel_preset"),
        LastRules("emirs_arena_last_rules");

        val symbol: String = "varp.$nameInSave"
    }
}
