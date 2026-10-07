package org.rsmod.api.combat.commons

import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.mes
import org.rsmod.game.entity.Player

public object DragonfireProtection {

    public enum class DragonfireType { Chromatic, Metal, WyvernIce }

    /**
     * Send on every dragonfire attack at launch, including zero-damage hits, before the delayed
     * impact.
     */
    public const val SHIELD_ABSORB_MESSAGE: String = "Your shield absorbs most of the dragon fire."

    private val antifireShields = setOf(
        "obj.antidragonbreathshield",
        "obj.dragonfire_shield",
        "obj.dragonfire_shield_uncharged",
        "obj.dragonfire_ward",
        "obj.dragonfire_ward_uncharged",
        "obj.wyvern_shield",
        "obj.wyvern_shield_uncharged",
    )

    private val wyvernIceShields = setOf(
        "obj.elemental_shield",
        "obj.elemental_mind_shield",
        "obj.dragonfire_shield",
        "obj.dragonfire_shield_uncharged",
        "obj.dragonfire_ward",
        "obj.dragonfire_ward_uncharged",
        "obj.wyvern_shield",
        "obj.wyvern_shield_uncharged",
    )

    public fun resolveMaxHit(player: Player, type: DragonfireType, baseMax: Int): Int {
        if (type == DragonfireType.WyvernIce) {
            return when {
                wyvernIceShields.any { it in player.worn } -> 10
                isProtectingFromMagic(player) -> 20
                else -> baseMax
            }
        }

        if (hasSuperAntifire(player)) return 0

        val shield = hasAntifireShield(player)
        val protect = type == DragonfireType.Chromatic && isProtectingFromMagic(player)
        val antifire = hasAntifire(player)

        if (antifire && (shield || protect)) return 0

        var cap = when {
            // Chromatic dragonfire with an anti-dragon shield is capped at 10.
            shield -> 10
            protect -> 10
            else -> baseMax
        }
        if (antifire) cap = (cap - 15).coerceAtLeast(0)
        return cap
    }

    public fun sendAbsorbMessage(player: Player, type: DragonfireType): Boolean {
        if (type == DragonfireType.WyvernIce) return false
        if (!hasAntifireShield(player)) return false
        player.mes(SHIELD_ABSORB_MESSAGE, ChatType.Spam)
        return true
    }

    private fun hasAntifireShield(player: Player): Boolean =
        antifireShields.any { it in player.worn }

    private fun hasSuperAntifire(player: Player): Boolean =
        player.vars["varbit.super_antifire_potion"] > 0

    private fun hasAntifire(player: Player): Boolean =
        player.vars["varbit.antifire_potion"] > 0

    private fun isProtectingFromMagic(player: Player): Boolean =
        player.vars["varbit.prayer_protectfrommagic"] > 0
}
