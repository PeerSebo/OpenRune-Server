package org.rsmod.content.other.emirsarena.duel

import org.rsmod.game.entity.Player

enum class DuelStage {
    Options,

    Stakes,

    StakeConfirm,

    Confirm,

    Countdown,
    Fighting,
    Finished,
}

enum class DuelEnd {
    Death,
    Forfeit,
    Logout,
}

class Duel(val challenger: Player, val opponent: Player, val ranked: Boolean) {
    var rules: DuelRules = if (ranked) RANKED_RULES else DuelRules.NONE
    var stage: DuelStage = DuelStage.Options
    var arena: DuelArena? = null
    var winner: Player? = null

    private val accepted = BooleanArray(2)

    val players: List<Player>
        get() = listOf(challenger, opponent)

    fun contains(player: Player): Boolean = player === challenger || player === opponent

    fun other(player: Player): Player = if (player === challenger) opponent else challenger

    fun side(player: Player): Int = if (player === challenger) 0 else 1

    fun hasAccepted(player: Player): Boolean = accepted[side(player)]

    fun accept(player: Player): Boolean {
        accepted[side(player)] = true
        return accepted[0] && accepted[1]
    }

    fun resetAcceptance() {
        accepted.fill(false)
    }

    val isActive: Boolean
        get() = stage == DuelStage.Countdown || stage == DuelStage.Fighting

    companion object {
        val RANKED_RULES: DuelRules = DuelRules.NONE.with(DuelRule.ShowInventories)
    }
}
