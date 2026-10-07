package org.rsmod.api.combat.commons.player

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.SequenceServerType
import kotlin.math.min
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.lefthand
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.player.vars.boolVarp
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitType
import org.rsmod.game.type.getOrNull

private val ProtectedAccess.autoRetaliateDisabled by boolVarp("varp.option_nodef")

public fun Player.queueCombatRetaliate(source: Npc, delay: Int = 1) {
    strongQueue("queue.com_retaliate_npc", delay, source.uid)
}

public fun ProtectedAccess.combatRetaliate(uid: NpcUid, flinchDelay: Int) {
    if (autoRetaliateDisabled || isBusy2 || player.routeRequest != null) {
        return
    }
    val source = findUid(uid) ?: return

    if (actionDelay < mapClock) {
        actionDelay = mapClock + flinchDelay
    }

    opNpc2(source)
}

public fun Player.queueCombatRetaliate(source: Player, delay: Int = 1) {
    strongQueue("queue.com_retaliate_player", delay, source.uid)
}

public fun ProtectedAccess.combatRetaliate(uid: PlayerUid, flinchDelay: Int) {
    preventLogout("You can't log out until 10 seconds after the end of combat.", 16)
    if (autoRetaliateDisabled || isBusy2 || player.routeRequest != null) {
        return
    }
    val source = findUid(uid) ?: return

    if (actionDelay < mapClock) {
        actionDelay = mapClock + flinchDelay
    }

    opPlayer2(source)
}

/** Defend delay uses 20 ms client cycles; magic hits never play a defend animation. */
public fun Player.finishNpcHit(
    source: Npc,
    delay: Int,
    type: HitType,
    damage: Int,
    modifier: PlayerHitModifier,
    defendClientDelay: Int = 0,
    prayerPenetration: Int = 0,
): Hit {
    // Retaliation and damage land together; existing combat and walking take priority.
    queueCombatRetaliate(source, delay.coerceAtLeast(1))
    val hit = queueHit(source, delay, type, damage, modifier = modifier, prayerPenetration = prayerPenetration)
    if (type != HitType.Magic) {
        combatPlayDefendAnim(defendClientDelay)
    }
    return hit
}

public fun Player.combatPlayDefendAnim(clientDelay: Int = 0) {
    val righthandType = getOrNull(righthand)
    val lefthandType = getOrNull(lefthand)
    val defendAnim = resolveDefendAnim(righthandType, lefthandType)
    anim(defendAnim, delay = clientDelay)
}

/**
 * Resolve symbols at the call site: failure in a static initializer would disable all player
 * damage for the JVM lifetime.
 */
private const val OFFHAND_SHIELD_CATEGORY: String = "category.shield"

private const val OFFHAND_DEFENDER_CATEGORY: String = "category.defender"

private const val SHIELD_DEFEND_ANIM: String = "seq.human_shield_defence"

private const val DEFENDER_DEFEND_ANIM: String = "seq.warguild_parry_defend"

private const val UNARMED_DEFEND_ANIM: String = "seq.human_unarmedblock"

/**
 * Use paramOrNull for off-hand overrides: param supplies an unarmed default even when no
 * override exists. Shield/defender categories then take precedence over the weapon.
 */
private fun resolveDefendAnim(
    righthand: ItemServerType?,
    lefthand: ItemServerType?,
): String {
    if (lefthand != null) {
        val declared: SequenceServerType? = lefthand.paramOrNull(params.defend_anim)
        if (declared != null) {
            return RSCM.getReverseMapping(RSCMType.SEQ, declared.id)
        }
        val categoryAnim = resolveOffhandCategoryDefendAnim(lefthand)
        if (categoryAnim != null) {
            return categoryAnim
        }
    }
    val righthandAnim = righthand?.param(params.defend_anim)
    return if (righthandAnim != null) {
        RSCM.getReverseMapping(RSCMType.SEQ, righthandAnim.id)
    } else {
        UNARMED_DEFEND_ANIM
    }
}

private fun resolveOffhandCategoryDefendAnim(lefthand: ItemServerType): String? =
    when {
        lefthand.isCategoryType(OFFHAND_SHIELD_CATEGORY) -> SHIELD_DEFEND_ANIM
        lefthand.isCategoryType(OFFHAND_DEFENDER_CATEGORY) -> DEFENDER_DEFEND_ANIM
        else -> null
    }

public fun Player.combatPlayDefendSpot(ammo: ItemServerType?, clientDelay: Int) {
    val type =
        ammo?.let { id -> ServerCacheManager.getItems().values.firstOrNull { it.id == id.id } }
            ?: return
    if (!type.isCategoryType("category.javelin")) {
        return
    }
    spotanim("spotanim.ballista_special", delay = clientDelay, height = 146)
}

public fun Player.resolveCombatXpMultiplier(): Double = min(1.125, 1 + (0.025 * (combatLevel / 20)))
