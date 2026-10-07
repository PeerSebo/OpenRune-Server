package org.rsmod.api.player.hit

import dev.openrune.types.HitmarkTypeGroup
import dev.openrune.types.ItemServerType
import kotlin.math.min
import org.rsmod.api.config.refs.done.hitmark_groups
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.hit.processor.InstantPlayerHitProcessor
import org.rsmod.api.player.hit.processor.QueuedPlayerHitProcessor
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType

// Standard-hit modifiers run when queued, not at impact. Clearing the relevant queue cancels
// the hit.
public fun Player.queueHit(
    source: Npc,
    delay: Int,
    type: HitType,
    damage: Int,
    modifier: PlayerHitModifier,
    hitmark: HitmarkTypeGroup = hitmark_groups.regular_damage,
    specific: Boolean = false,
    sourceWeapon: ItemServerType? = null,
    sourceSecondary: ItemServerType? = null,
    prayerPenetration: Int = 0,
): Hit {
    val cappedDamage = min(hitpoints, damage)
    val builder =
        InternalPlayerHits.createBuilder(
            source = source,
            type = type,
            damage = cappedDamage,
            righthand = sourceWeapon,
            secondaryObj = sourceSecondary,
            hitmark = hitmark,
            clientDelay = 0,
            specific = specific,
        )
    builder.prayerPenetration = prayerPenetration
    return modifyAndStrongQueueHit(delay, builder, modifier)
}

public fun Player.queueHit(
    source: Player,
    delay: Int,
    type: HitType,
    damage: Int,
    modifier: PlayerHitModifier,
    hitmark: HitmarkTypeGroup = hitmark_groups.regular_damage,
    sourceSecondary: ItemServerType? = null,
): Hit {
    val builder =
        InternalPlayerHits.createBuilder(
            target = this,
            source = source,
            type = type,
            damage = damage,
            secondaryObj = sourceSecondary,
            hitmark = hitmark,
            clientDelay = 0,
            specific = false,
        )
    return modifyAndStrongQueueHit(delay, builder, modifier)
}

public fun Player.queueHit(
    delay: Int,
    type: HitType,
    damage: Int,
    modifier: PlayerHitModifier,
    hitmark: HitmarkTypeGroup = hitmark_groups.regular_damage,
    specific: Boolean = false,
    strongQueue: Boolean = true,
): Hit {
    val builder =
        InternalPlayerHits.createBuilder(
            type = type,
            damage = damage,
            righthand = null,
            secondaryObj = null,
            hitmark = hitmark,
            clientDelay = 0,
            specific = specific,
        )
    return if (strongQueue) {
        modifyAndStrongQueueHit(delay, builder, modifier)
    } else {
        modifyAndQueueHit(delay, builder, modifier)
    }
}

private fun Player.modifyAndStrongQueueHit(
    delay: Int,
    builder: HitBuilder,
    modifier: PlayerHitModifier,
): Hit {
    modifier.modify(builder, this)
    val hit = builder.build()
    strongQueue("queue.hit", delay, hit)
    return hit
}

private fun Player.modifyAndQueueHit(
    delay: Int,
    builder: HitBuilder,
    modifier: PlayerHitModifier,
): Hit {
    modifier.modify(builder, this)
    val hit = builder.build()
    queue("queue.hit", delay, hit)
    return hit
}

// Instant hits bypass normal queued-hit modifiers unless explicitly requested.
public fun Player.takeInstantHit(
    type: HitType,
    damage: Int,
    processor: InstantPlayerHitProcessor,
    hitmark: HitmarkTypeGroup = hitmark_groups.regular_damage,
    specific: Boolean = false,
    modifier: PlayerHitModifier = NoopPlayerHitModifier,
): Hit {
    val builder =
        InternalPlayerHits.createBuilder(
            type = type,
            damage = damage,
            righthand = null,
            secondaryObj = null,
            hitmark = hitmark,
            clientDelay = 0,
            specific = specific,
        )

    modifier.modify(builder, this)

    val hit = builder.build()
    processor.process(this, hit)
    return hit
}

// Impact hits resolve modifiers at impact and do not cap damage to current health when queued.
public fun Player.queueImpactHit(
    source: Npc,
    delay: Int,
    type: HitType,
    damage: Int,
    modifier: PlayerHitModifier,
    hitmark: HitmarkTypeGroup = hitmark_groups.regular_damage,
    specific: Boolean = false,
    sourceWeapon: ItemServerType? = null,
    sourceSecondary: ItemServerType? = null,
    prayerPenetration: Int = 0,
) {
    val builder =
        InternalPlayerHits.createBuilder(
            source = source,
            type = type,
            damage = damage,
            righthand = sourceWeapon,
            secondaryObj = sourceSecondary,
            hitmark = hitmark,
            clientDelay = 0,
            specific = specific,
        )
    builder.prayerPenetration = prayerPenetration
    strongQueueImpactHit(delay, builder, modifier)
}

public fun Player.queueImpactHit(
    source: Player,
    delay: Int,
    type: HitType,
    damage: Int,
    modifier: PlayerHitModifier,
    hitmark: HitmarkTypeGroup = hitmark_groups.regular_damage,
    sourceSecondary: ItemServerType? = null,
) {
    val builder =
        InternalPlayerHits.createBuilder(
            target = this,
            source = source,
            type = type,
            damage = damage,
            secondaryObj = sourceSecondary,
            hitmark = hitmark,
            clientDelay = 0,
            specific = false,
        )
    strongQueueImpactHit(delay, builder, modifier)
}

public fun Player.queueImpactHit(
    delay: Int,
    type: HitType,
    damage: Int,
    modifier: PlayerHitModifier,
    hitmark: HitmarkTypeGroup = hitmark_groups.regular_damage,
    specific: Boolean = false,
    strongQueue: Boolean = true,
) {
    val builder =
        InternalPlayerHits.createBuilder(
            type = type,
            damage = damage,
            righthand = null,
            secondaryObj = null,
            hitmark = hitmark,
            clientDelay = 0,
            specific = specific,
        )
    if (strongQueue) {
        strongQueueImpactHit(delay, builder, modifier)
    } else {
        queueImpactHit(delay, builder, modifier)
    }
}

private fun Player.strongQueueImpactHit(
    delay: Int,
    builder: HitBuilder,
    modifier: PlayerHitModifier,
) {
    val deferred = DeferredPlayerHit(builder, modifier)
    strongQueue("queue.impact_hit", delay, deferred)
}

private fun Player.queueImpactHit(delay: Int, builder: HitBuilder, modifier: PlayerHitModifier) {
    val deferred = DeferredPlayerHit(builder, modifier)
    queue("queue.impact_hit", delay, deferred)
}

internal fun ProtectedAccess.processQueuedHit(hit: Hit, processor: QueuedPlayerHitProcessor) {
    processor.process(this, hit)
}

internal fun ProtectedAccess.processQueuedHit(
    builder: HitBuilder,
    modifier: PlayerHitModifier,
    processor: QueuedPlayerHitProcessor,
) {
    modifier.modify(builder, player)

    val hit = builder.build()
    processQueuedHit(hit, processor)
}

public fun PlayerHitModifier.modify(builder: HitBuilder, target: Player): Unit =
    builder.modify(target)

private fun QueuedPlayerHitProcessor.process(access: ProtectedAccess, hit: Hit) {
    access.process(hit)
}

private fun InstantPlayerHitProcessor.process(target: Player, hit: Hit) {
    target.process(hit)
}
