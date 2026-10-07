package org.rsmod.content.skills.woodcutting

import org.rsmod.api.player.back
import org.rsmod.api.player.feet
import org.rsmod.api.player.front
import org.rsmod.api.player.hat
import org.rsmod.api.player.legs
import org.rsmod.api.player.torso
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.isType

object WoodcuttingEquipment {
    private val LUMBERJACK_HATS = listOf("obj.ramble_lumberjack_hat", "obj.forestry_lumberjack_hat")
    private val LUMBERJACK_TOPS = listOf("obj.ramble_lumberjack_top", "obj.forestry_lumberjack_top")
    private val LUMBERJACK_LEGS = listOf("obj.ramble_lumberjack_legs", "obj.forestry_lumberjack_legs")
    private val LUMBERJACK_BOOTS =
        listOf("obj.ramble_lumberjack_boots", "obj.forestry_lumberjack_boots")

    // Cosmetic combat max capes do not retain the skillcape perks.
    private val NEST_CAPES = listOf(
        "obj.skillcape_woodcutting", "obj.skillcape_woodcutting_trimmed",
        "obj.skillcape_max", "obj.skillcape_max_worn",
    )

    fun Player.hasNestCapeBoost(): Boolean = NEST_CAPES.any { back.isType(it) }

    fun Player.hasStrungRabbitFoot(): Boolean = front.isType("obj.hunting_strung_rabbit_foot")

    fun Player.hasLumberjackHat(): Boolean = LUMBERJACK_HATS.any { hat.isType(it) }

    fun Player.hasLumberjackTop(): Boolean = LUMBERJACK_TOPS.any { torso.isType(it) }

    fun Player.hasLumberjackLegs(): Boolean = LUMBERJACK_LEGS.any { legs.isType(it) }

    fun Player.hasLumberjackBoots(): Boolean = LUMBERJACK_BOOTS.any { feet.isType(it) }

    fun Player.hasFullLumberjackOutfit(): Boolean =
        hasLumberjackHat() && hasLumberjackTop() && hasLumberjackLegs() && hasLumberjackBoots()
}
