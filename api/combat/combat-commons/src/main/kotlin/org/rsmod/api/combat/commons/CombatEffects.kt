package org.rsmod.api.combat.commons

import org.rsmod.api.mechanics.toxins.impl.PlayerPoison
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.statSub
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

public object CombatEffects {

    public fun poison(target: Player, damage: Int) {
        PlayerPoison.tryPoison(target, initialDamage = damage)
    }

    private const val FREEZE_IMMUNITY_TICKS = 5
    private const val FREEZE_TIMER = "timer.combat_freeze"
    private const val STUN_TIMER = "timer.combat_stun"

    /**
     * Freezes [target] in place for [ticks], after which they are immune to further freezes for
     * a short while. A running freeze is not replaced; a stun ([stun]) that is running does not
     * block a freeze, the two simply overlap.
     */
    public fun freeze(target: Player, ticks: Int): Boolean {
        if (ticks <= 0 || FREEZE_TIMER in target.timerMap || target.freezeImmune) return false
        target.frozen = true
        target.routeDestination.clear()
        target.timer(FREEZE_TIMER, ticks)
        target.mes("You have been frozen!", ChatType.Spam)
        return true
    }

    public fun unfreeze(target: Player) {
        target.frozen = STUN_TIMER in target.timerMap
        target.freezeImmune = true
        target.clearTimer(FREEZE_TIMER)
        target.timer("timer.combat_freeze_immunity", FREEZE_IMMUNITY_TICKS)
    }

    /**
     * Stuns [target] for [ticks]: they cannot move or attack, as with the dragon spear's Shove.
     * Unlike a freeze, a stun ignores freeze immunity, grants none when it ends, and a new stun
     * restarts the timer.
     */
    public fun stun(target: Player, ticks: Int) {
        target.frozen = true
        target.routeDestination.clear()
        target.actionDelay = maxOf(target.actionDelay, target.currentMapClock + ticks)
        target.timer(STUN_TIMER, ticks)
    }

    public fun unstun(target: Player) {
        target.clearTimer(STUN_TIMER)
        target.frozen = FREEZE_TIMER in target.timerMap
    }

    public fun clearFreezeImmunity(target: Player) {
        target.freezeImmune = false
    }

    public fun isFrozen(target: Npc): Boolean {
        val end = target.vars["varn.freeze_end_clock"]
        return end > 0 && end > target.currentMapClock
    }

    public fun freeze(target: Npc, ticks: Int, ignoreImmunity: Boolean = false): Boolean {
        val clock = target.currentMapClock
        if (ticks <= 0 || isFrozen(target)) return false
        val immunityEnd = target.vars["varn.freeze_immunity_end_clock"]
        if (!ignoreImmunity && immunityEnd > 0 && immunityEnd > clock) return false
        val end = clock + ticks
        target.vars["varn.freeze_end_clock"] = end
        target.vars["varn.freeze_immunity_end_clock"] = end + FREEZE_IMMUNITY_TICKS
        target.routeDestination.clear()
        return true
    }

    public fun unfreeze(target: Npc) {
        target.vars["varn.freeze_end_clock"] = 0
        target.vars["varn.freeze_immunity_end_clock"] =
            target.currentMapClock + FREEZE_IMMUNITY_TICKS
    }

    public fun statDrain(target: Player, stats: List<String>, amount: Int) {
        for (stat in stats) {
            target.statSub(stat, constant = amount, percent = 0)
        }
    }
}
