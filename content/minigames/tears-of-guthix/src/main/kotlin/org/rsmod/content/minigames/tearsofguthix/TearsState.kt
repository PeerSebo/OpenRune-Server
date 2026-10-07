package org.rsmod.content.minigames.tearsofguthix

import java.time.LocalDate
import java.time.ZoneOffset
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player

internal object TearsState {
    const val Active = "varp.tears_session_phase"
    const val Start = "varp.tears_session_start"
    const val Deadline = "varp.tears_session_deadline"
    const val Wall = "varp.tears_session_wall"
    const val Collected = "varbit.tog_tears_collected"
    const val Countdown = "varbit.tog_countdown"
    const val Best = "varbit.tog_max_tears_collected"
    const val HasPlayed = "varp.tears_has_played"
    const val LastDay = "varp.tears_last_day"
    const val LastQp = "varp.tears_last_qp"
    const val LastXpLow = "varp.tears_last_xp_low"
    const val LastXpHigh = "varp.tears_last_xp_high"
    const val NotifyDay = "varp.tears_notified_day"
    const val RunMode = "varp.tears_previous_run"
    const val LogoutAfter = "varp.tears_logout_after_drink"
    const val RouteStep = "varp.tears_route_step"

    fun set(player: Player, name: String, value: Int) = VarPlayerIntMapSetter.set(player, name, value)
    fun active(player: Player) = player.vars[Active] != 0
    fun day(): Int = LocalDate.now(ZoneOffset.UTC).toEpochDay().toInt()
    fun totalXp(player: Player): Long = TearsData.skills.sumOf { player.statMap.getXP(it.stat).toLong() }
    fun previousXp(player: Player): Long = (player.vars[LastXpHigh].toLong() shl 32) or
        (player.vars[LastXpLow].toLong() and 0xffffffffL)

    fun eligibility(player: Player, day: Int = day()): String? {
        if (player.vars[HasPlayed] == 0) return null
        val days = (TearsData.rules.weeklyDays - (day - player.vars[LastDay])).coerceAtLeast(0)
        if (days > 0) return "You must wait $days more ${if (days == 1) "day" else "days"} before collecting tears again."
        val gained = (totalXp(player) - previousXp(player)).coerceAtLeast(0)
        if (gained >= TearsData.rules.xpRequirement || player.vars["varp.qp"] > player.vars[LastQp]) return null
        val remaining = TearsData.rules.xpRequirement - gained
        return "You need another $remaining experience points or one quest point before collecting tears again."
    }

    fun rememberReward(player: Player, day: Int = day()) {
        val xp = totalXp(player)
        set(player, LastDay, day)
        set(player, LastQp, player.vars["varp.qp"])
        set(player, LastXpLow, xp.toInt())
        set(player, LastXpHigh, (xp ushr 32).toInt())
        set(player, HasPlayed, 1)
    }

    fun resetEligibility(player: Player) {
        for (variable in listOf(HasPlayed, LastDay, LastQp, LastXpLow, LastXpHigh, NotifyDay)) {
            set(player, variable, 0)
        }
    }

    fun clear(player: Player) {
        for (variable in listOf(Active, Start, Deadline, Wall, Collected, Countdown, LogoutAfter,
            RouteStep)) set(player, variable, 0)
        set(player, "varbit.tog_minigame_collecting", 0)
    }
}
