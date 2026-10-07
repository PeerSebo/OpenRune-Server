package org.rsmod.api.npc

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.HitmarkTypeGroup
import dev.openrune.types.NpcMode
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.done.hitmark_groups
import org.rsmod.api.config.refs.params
import org.rsmod.api.npc.headbar.InternalNpcHeadbars
import org.rsmod.game.entity.Npc
import org.rsmod.game.hit.Hitmark

public fun Npc.clearInteractionRoute() {
    clearInteraction()
    abortRoute()
}

public fun Npc.queueDeath() {
    queue("queue.death", 1)
}

public fun Npc.isValidTarget(): Boolean {
    return isSlotAssigned && isVisible && isNotDelayed && hitpoints > 0
}

public fun Npc.heal(
    amount: Int,
    showHitsplat: Boolean = false,
    hitmarkGroup: HitmarkTypeGroup = hitmark_groups.heal,
): Int {
    require(amount >= 0) { "Heal amount `$amount` must not be negative." }
    val before = hitpoints
    hitpoints = (hitpoints + amount).coerceAtMost(baseHitpointsLvl)
    val healed = hitpoints - before
    if (showHitsplat && healed > 0) {
        val hitmarkId = hitmarkGroup.lit.asRSCM(RSCMType.HITMARK)
        val hitmark =
            Hitmark.fromNoSource(
                self = hitmarkId,
                source = hitmarkId,
                public = hitmarkId,
                damage = healed,
                delay = 0,
            )
        showHitmark(hitmark)
        val headbar =
            InternalNpcHeadbars.createFromHitmark(
                hitmark,
                hitpoints,
                baseHitpointsLvl,
                visHeadbar(params.headbar),
            )
        showHeadbar(headbar)
    }
    return healed
}

public fun Npc.isOutOfCombat(): Boolean = !isInCombat()

public fun Npc.isInCombat(): Boolean {
    if (vars["varn.lastattack"] + constants.combat_activecombat_delay >= currentMapClock) {
        return true
    }
    return mode == NpcMode.OpPlayer2 || mode == NpcMode.ApPlayer2 || mode == NpcMode.PlayerEscape
}

public fun Npc.mapMultiway(checker: AreaChecker): Boolean {
    return checker.inArea("area.multiway", coords)
}
