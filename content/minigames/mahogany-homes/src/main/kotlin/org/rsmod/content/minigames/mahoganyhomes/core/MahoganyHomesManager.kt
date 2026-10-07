package org.rsmod.content.minigames.mahoganyhomes.core

import org.rsmod.api.player.stat.constructionLvl
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.content.minigames.mahoganyhomes.configs.MahoganyHomesHouse
import org.rsmod.content.minigames.mahoganyhomes.configs.MahoganyHomesLayoutSlot
import org.rsmod.content.minigames.mahoganyhomes.configs.MahoganyHomesLayouts
import org.rsmod.content.minigames.mahoganyhomes.configs.MahoganyHomesTier
import org.rsmod.game.entity.Player

public object MahoganyHomesManager {

    private var Player.clientOrdinalPlusOne by intVarBit("varbit.mahogany_homes_client")
    private var Player.tierOrdinal by intVarBit("varbit.mahogany_homes_tier")
    private var Player.layoutOrdinal by intVarBit("varbit.mahogany_homes_layout")

    public fun hasActiveContract(player: Player): Boolean = player.clientOrdinalPlusOne != 0

    public fun currentHouse(player: Player): MahoganyHomesHouse? {
        val ordinalPlusOne = player.clientOrdinalPlusOne
        if (ordinalPlusOne == 0) return null
        return MahoganyHomesHouse.ofOrdinal(ordinalPlusOne - 1)
    }

    public fun currentTier(player: Player): MahoganyHomesTier = MahoganyHomesTier.ofOrdinal(player.tierOrdinal)

    private var Player.pointsBalance: Int by intVarBit("varbit.mahogany_homes_points")

    private var Player.completedContractsCount: Int by intVarBit("varbit.mahogany_homes_contracts_completed")

    public fun getPoints(player: Player): Int = player.pointsBalance

    public fun addPoints(player: Player, amount: Int) {
        if (amount <= 0) return
        player.pointsBalance = (player.pointsBalance + amount).coerceAtLeast(0)
    }

    public fun spendPoints(player: Player, amount: Int): Boolean {
        if (amount <= 0) return true
        if (player.pointsBalance < amount) return false
        player.pointsBalance -= amount
        return true
    }

    public fun getContractsCompleted(player: Player): Int = player.completedContractsCount

    public fun eligibleTiers(player: Player): List<MahoganyHomesTier> =
        MahoganyHomesTier.entries.filter { player.constructionLvl >= it.levelReq }

    public fun rollAndAssign(
        player: Player,
        randomBelow: (maxExclusive: Int) -> Int,
        reusePreviousTier: Boolean,
    ): MahoganyHomesHouse? {
        val eligible = eligibleTiers(player)
        if (eligible.isEmpty()) return null

        val tier =
            if (reusePreviousTier && currentTier(player) in eligible) {
                currentTier(player)
            } else {
                eligible[randomBelow(eligible.size)]
            }

        val house = MahoganyHomesHouse.ofOrdinal(randomBelow(MahoganyHomesHouse.entries.size))

        player.clientOrdinalPlusOne = house.ordinal + 1
        player.tierOrdinal = tier.ordinal
        player.layoutOrdinal = 0

        applyStartingStates(player, house)
        return house
    }

    // Clear unused shared display slots so furniture from another house cannot remain visible.
    private fun applyStartingStates(player: Player, house: MahoganyHomesHouse) {
        val layout = MahoganyHomesLayouts.layoutFor(house)
        val bySlot = layout.associateBy { it.hotspotIndex }
        for (slot in 1..MAX_DISPLAY_SLOTS) {
            val startState =
                when (bySlot[slot]?.kind) {
                    MahoganyHomesHotspotKind.BUILD -> MahoganyHomesBuildState.EDITABLE
                    MahoganyHomesHotspotKind.REPAIR -> MahoganyHomesRepairState.BROKEN
                    null -> 0
                }
            writeDisplaySlot(player, slot, startState)
        }
    }

    public fun writeDisplaySlot(player: Player, slot: Int, value: Int) {
        VarPlayerIntMapSetter.set(player, "varbit.con_contract_hotspot_${slot}_display", value)
    }

    public fun readDisplaySlot(player: Player, slot: Int): Int = player.vars["varbit.con_contract_hotspot_${slot}_display"]

    public fun remainingSlots(player: Player): List<MahoganyHomesLayoutSlot> {
        val house = currentHouse(player) ?: return emptyList()
        val tier = currentTier(player)
        return MahoganyHomesLayouts.layoutFor(house).filter { slotState ->
            val current = readDisplaySlot(player, slotState.hotspotIndex)
            val done =
                when (slotState.kind) {
                    MahoganyHomesHotspotKind.BUILD -> MahoganyHomesBuildState.forTierOrdinal(tier.ordinal)
                    MahoganyHomesHotspotKind.REPAIR -> MahoganyHomesRepairState.FIXED
                }
            current != done
        }
    }

    public fun isComplete(player: Player): Boolean = hasActiveContract(player) && remainingSlots(player).isEmpty()

    public fun completeContract(player: Player): CompletionAward {
        val tier = currentTier(player)
        val multiplier = carpenterOutfitMultiplier(player)

        player.clientOrdinalPlusOne = 0
        player.completedContractsCount = (player.completedContractsCount + 1).coerceAtMost(CONTRACTS_COMPLETED_CAP)
        addPoints(player, tier.points)

        return CompletionAward(xp = tier.completionXp * multiplier, points = tier.points)
    }

    public fun carpenterOutfitMultiplier(player: Player): Double {
        var bonus = 0.0
        var pieces = 0
        if ("obj.construction_hat" in player.worn) {
            bonus += 0.004
            pieces++
        }
        if ("obj.construction_shirt" in player.worn) {
            bonus += 0.008
            pieces++
        }
        if ("obj.construction_trousers" in player.worn) {
            bonus += 0.006
            pieces++
        }
        if ("obj.construction_boots" in player.worn) {
            bonus += 0.002
            pieces++
        }
        if (pieces == 4) {
            bonus += 0.005
        }
        return 1.0 + bonus
    }

    public data class CompletionAward(val xp: Double, val points: Int)

    private const val MAX_DISPLAY_SLOTS = 8
    private const val CONTRACTS_COMPLETED_CAP = 30_000
}
