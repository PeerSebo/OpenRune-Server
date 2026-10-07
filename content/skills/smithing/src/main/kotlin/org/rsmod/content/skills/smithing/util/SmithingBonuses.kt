package org.rsmod.content.skills.smithing.util

import kotlin.math.abs
import org.rsmod.api.random.GameRandom
import org.rsmod.content.skills.smithing.table.SmithingBar
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory
import org.rsmod.map.CoordGrid

object SmithingBonuses {
    const val SMITHING_CATALYST = "obj.smithing_catalyst"
    private const val COAL = "obj.coal"

    private val VARROCK_ARMOUR_BY_TIER =
        listOf(
            "obj.varrock_armour_easy",
            "obj.varrock_armour_medium",
            "obj.varrock_armour_hard",
            "obj.varrock_armour_elite",
        )

    private val EDGEVILLE_FURNACE = CoordGrid(0, 48, 54, 33, 40)

    fun isEdgevilleFurnace(locInternal: String, coords: CoordGrid): Boolean {
        if (locInternal.contains("edgeville", ignoreCase = true)) {
            return true
        }
        if (coords.level != EDGEVILLE_FURNACE.level) {
            return false
        }
        return abs(coords.x - EDGEVILLE_FURNACE.x) <= 3 && abs(coords.z - EDGEVILLE_FURNACE.z) <= 3
    }

    fun Player.varrockArmourTier(): Int? {
        for ((index, armour) in VARROCK_ARMOUR_BY_TIER.withIndex()) {
            if (armour in worn) {
                return index + 1
            }
        }
        return null
    }

    fun maxBarLevelForVarrockTier(tier: Int): Int =
        when (tier) {
            1 -> 30
            2 -> 50
            3 -> 70
            else -> Int.MAX_VALUE
        }

    fun isBarEligibleForVarrockArmour(bar: SmithingBar, tier: Int): Boolean =
        bar.levelReq <= maxBarLevelForVarrockTier(tier)

    // TODO: Verify the assumed 10% Varrock armour proc chance.
    fun rollVarrockDoubleBar(random: GameRandom): Boolean = random.of(100) < 10

    fun requiresCoal(bar: SmithingBar): Boolean =
        bar.input.getOrNull(1)?.internalName == COAL &&
            (bar.input.getOrNull(1)?.let { bar.inputAmount.getOrNull(1) } ?: 0) > 0

    fun hasCatalystBoost(inventory: Inventory, bar: SmithingBar): Boolean =
        requiresCoal(bar) && inventory.contains(SMITHING_CATALYST)

    fun halfCoalAmount(bar: SmithingBar): Int =
        ((bar.inputAmount.getOrNull(1) ?: 0) / 2).coerceAtLeast(1)

    // Menu counts approximate catalyst supply; the production loop charges exact coal per bar.
    fun effectiveCoalAmount(inventory: Inventory, bar: SmithingBar): Int {
        val secondaryAmt = bar.input.getOrNull(1)?.let { bar.inputAmount.getOrNull(1) } ?: 0
        if (!hasCatalystBoost(inventory, bar)) {
            return secondaryAmt
        }
        return halfCoalAmount(bar)
    }
}
