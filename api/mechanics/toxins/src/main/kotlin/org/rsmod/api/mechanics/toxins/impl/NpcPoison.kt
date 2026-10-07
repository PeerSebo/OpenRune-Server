package org.rsmod.api.mechanics.toxins.impl

import org.rsmod.api.config.refs.done.hitmark_groups
import org.rsmod.api.config.refs.params
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.game.entity.Npc
import org.rsmod.game.hit.HitType

public object NpcPoison {
    public const val TIMER: String = "timer.npc_poison"
    public const val TICK_INTERVAL: Int = PlayerPoison.TICK_INTERVAL

    private const val SEVERITY_VARN = "varn.npc_poison_severity"
    private val noModifier = NpcHitModifier {}

    public fun isPoisoned(npc: Npc): Boolean = npc.vars[SEVERITY_VARN] > 0

    public fun isImmune(npc: Npc): Boolean =
        (npc.visType.paramOrNull(params.poison_immunity) ?: 0) > 0

    public fun tryPoison(
        npc: Npc,
        initialDamage: Int? = null,
        severity: Int? = null,
    ): Boolean {
        val incoming = severity ?: PlayerPoison.severityForInitialDamage(initialDamage ?: 0)
        if (incoming <= 0 || npc.hitpoints <= 0 || isImmune(npc)) return false
        val current = npc.vars[SEVERITY_VARN]
        if (current >= incoming) return false
        queuePoisonHit(npc, PlayerPoison.damageForSeverity(incoming))
        setSeverity(npc, incoming - 1)
        return true
    }

    public fun onTimerTick(npc: Npc) {
        val severity = npc.vars[SEVERITY_VARN]
        if (severity <= 0 || npc.hitpoints <= 0) {
            clear(npc)
            return
        }
        queuePoisonHit(npc, PlayerPoison.damageForSeverity(severity))
        setSeverity(npc, severity - 1)
    }

    public fun clear(npc: Npc) {
        npc.vars[SEVERITY_VARN] = 0
        npc.clearTimer(TIMER)
    }

    private fun setSeverity(npc: Npc, severity: Int) {
        if (severity <= 0) {
            clear(npc)
            return
        }
        npc.vars[SEVERITY_VARN] = severity
        npc.timer(TIMER, TICK_INTERVAL)
    }

    private fun queuePoisonHit(npc: Npc, damage: Int) {
        val capped = minOf(damage, npc.hitpoints)
        if (capped <= 0) return
        npc.queueHit(
            delay = 1,
            type = HitType.Typeless,
            damage = capped,
            modifier = noModifier,
            hitmark = hitmark_groups.poison_damage,
        )
    }
}
