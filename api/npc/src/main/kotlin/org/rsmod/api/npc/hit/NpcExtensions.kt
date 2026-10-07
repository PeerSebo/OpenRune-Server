package org.rsmod.api.npc.hit

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.HitmarkTypeGroup
import dev.openrune.types.ItemServerType
import org.rsmod.api.config.refs.params
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.processor.NpcHitProcessor
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType

// Standard-hit modifiers run when queued, not at impact; NPC impact hooks may still alter final
// damage.
public fun Npc.queueHit(
    source: Npc,
    delay: Int,
    type: HitType,
    damage: Int,
    modifier: NpcHitModifier,
    hitmark: HitmarkTypeGroup = visHitmark(),
    sourceWeapon: ItemServerType? = null,
    sourceSecondary: ItemServerType? = null,
): Hit {
    val builder =
        InternalNpcHits.createBuilder(
            source = source,
            type = type,
            damage = damage,
            righthand = sourceWeapon,
            secondaryObj = sourceSecondary,
            hitmark = hitmark,
            clientDelay = 0,
        )
    return modifyAndQueueHit(delay, builder, modifier)
}

public fun Npc.queueHit(
    source: Player,
    delay: Int,
    type: HitType,
    damage: Int,
    modifier: NpcHitModifier,
    hitmark: HitmarkTypeGroup = visHitmark(),
    specific: Boolean = false,
    sourceSecondary: ItemServerType? = null,
): Hit {
    val builder =
        InternalNpcHits.createBuilder(
            source = source,
            type = type,
            damage = damage,
            secondaryObj = sourceSecondary,
            hitmark = hitmark,
            clientDelay = 0,
            specific = specific,
        )
    return modifyAndQueueHit(delay, builder, modifier)
}

public fun Npc.queueHit(
    delay: Int,
    type: HitType,
    damage: Int,
    modifier: NpcHitModifier,
    hitmark: HitmarkTypeGroup = visHitmark(),
): Hit {
    val builder =
        InternalNpcHits.createBuilder(
            type = type,
            damage = damage,
            righthand = null,
            secondaryObj = null,
            hitmark = hitmark,
            clientDelay = 0,
        )
    return modifyAndQueueHit(delay, builder, modifier)
}

public fun Npc.visHitmark(): HitmarkTypeGroup {
    val current = cachedHitmark
    if (current != null) {
        return current
    }
    val lit = RSCM.getReverseMapping(RSCMType.HITMARK, visType.param(params.hitmark_lit).id)
    val tint = RSCM.getReverseMapping(RSCMType.HITMARK, visType.param(params.hitmark_tint).id)
    val max = RSCM.getReverseMapping(RSCMType.HITMARK, visType.param(params.hitmark_max).id)
    val hitmark = HitmarkTypeGroup(lit, tint, max)
    this.cachedHitmark = hitmark
    return hitmark
}

public fun Npc.isStyleImmuneTo(type: HitType): Boolean =
    when (type) {
        HitType.Melee -> vars["varn.immune_melee"] == 1
        HitType.Ranged -> vars["varn.immune_ranged"] == 1
        HitType.Magic -> vars["varn.immune_magic"] == 1
        HitType.Typeless -> false
    }

private fun Npc.modifyAndQueueHit(delay: Int, builder: HitBuilder, modifier: NpcHitModifier): Hit {
    modifier.modify(builder, this)
    val hit = builder.build()
    queue("queue.hit", delay, hit)
    return hit
}

private fun NpcHitModifier.modify(builder: HitBuilder, target: Npc): Unit = builder.modify(target)

internal fun NpcHitProcessor.process(access: StandardNpcAccess, hit: Hit) {
    access.process(hit)
}
