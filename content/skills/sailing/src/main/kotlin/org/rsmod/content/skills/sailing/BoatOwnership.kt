package org.rsmod.content.skills.sailing

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.stat.baseSailingLvl
import org.rsmod.api.table.sailing.SailingBoatFacilityStatsRow
import org.rsmod.api.table.sailing.SailingBoatRow
import org.rsmod.content.skills.sailing.configs.SailingBoats
import org.rsmod.content.skills.sailing.configs.SailingConstants
import org.rsmod.content.skills.sailing.configs.SailingDocks
import org.rsmod.game.entity.Player

internal data class BoatPartTier(
    val index: Int,
    val name: String,
    val sailingRequirement: Int,
    val constructionRequirement: Int,
    val stats: SailingBoatFacilityStatsRow?,
)

internal data class OwnedBoat(
    val slot: Int,
    val boat: SailingBoatRow,
    val dockId: Int,
    val dock: SailingDocks.SailingDock?,
    val hp: Int,
    val maxHp: Int,
) {
    val inlineName: String
        get() = boat.inlineName

    val displayName: String
        get() = boat.displayname ?: boat.name

    val missingHp: Int
        get() = (maxHp - hp).coerceAtLeast(0)

    val isSunk: Boolean
        get() = maxHp > 0 && hp <= 0
}

@Singleton
internal class BoatOwnershipService @Inject constructor() {

    fun slotAllowance(player: Player): Int = SailingBoats.ownableBoatCount(player.baseSailingLvl)

    fun ownedSlots(player: Player): List<Int> = player.ownedBoatSlots()

    fun ownedCount(player: Player): Int = ownedSlots(player).size

    fun canOwnAnother(player: Player): Boolean = ownedCount(player) < slotAllowance(player)

    // Reuse the lowest unoccupied slot, not owned-count + 1.
    fun freeSlot(player: Player): Int? =
        if (!canOwnAnother(player)) {
            null
        } else {
            (1..SailingConstants.MAX_BOAT_SLOTS).firstOrNull { !player.ownsBoat(it) }
        }

    fun nextSlotLevel(player: Player): Int? {
        val nth = ownedCount(player) + 1
        return if (nth > SailingConstants.MAX_BOAT_SLOTS) {
            null
        } else {
            SailingBoats.boatSlotRequirement(nth)
        }
    }

    fun purchasableBoats(player: Player): List<SailingBoatRow> {
        val base = player.baseSailingLvl
        return SailingBoats.all.filter { it.levelRequired <= base }
    }

    fun boatTypeId(player: Player, slot: Int): Int =
        player.readSailingVar(SailingBoatVars.field(slot, TYPE_FIELD))

    // Slot type stores type_id; zero is a valid raft, so ownership needs its own bit.
    fun boat(player: Player, slot: Int): SailingBoatRow? =
        if (!player.ownsBoat(slot)) null else SailingBoats.byTypeId(boatTypeId(player, slot))

    fun activeSlot(player: Player): Int? = player.activeBoatSlot()

    fun mooredDockId(player: Player, slot: Int): Int =
        player.readSailingVar(SailingBoatVars.port(slot))

    fun mooredDock(player: Player, slot: Int): SailingDocks.SailingDock? =
        SailingDocks.byDockId(mooredDockId(player, slot))

    fun hp(player: Player, slot: Int): Int =
        player.readSailingVar(SailingBoatVars.field(slot, STORED_HP_FIELD))

    fun maxHp(player: Player, slot: Int): Int =
        player.readSailingVar(SailingBoatVars.field(slot, STORED_MAX_HP_FIELD))

    fun owned(player: Player, slot: Int): OwnedBoat? {
        val boat = boat(player, slot) ?: return null
        val dockId = mooredDockId(player, slot)
        return OwnedBoat(
            slot = slot,
            boat = boat,
            dockId = dockId,
            dock = SailingDocks.byDockId(dockId),
            hp = hp(player, slot),
            maxHp = maxHp(player, slot),
        )
    }

    fun ownedBoats(player: Player): List<OwnedBoat> =
        ownedSlots(player).mapNotNull { owned(player, it) }

    fun tiers(boat: SailingBoatRow, part: ShipPartCategory): List<BoatPartTier> {
        val base = part.firstOptionVarValue
        return when (part) {
            ShipPartCategory.KEEL ->
                boat.keelOption
                    .sortedBy { it.sailingRequirement }
                    .mapIndexed { i, row ->
                        BoatPartTier(
                            index = base + i,
                            name = row.name,
                            sailingRequirement = row.sailingRequirement,
                            constructionRequirement = row.constructionRequirement,
                            stats = row.facilityStats,
                        )
                    }
            ShipPartCategory.HULL ->
                boat.hullOption
                    .sortedBy { it.sailingRequirement }
                    .mapIndexed { i, row ->
                        BoatPartTier(
                            index = base + i,
                            name = row.name,
                            sailingRequirement = row.sailingRequirement,
                            constructionRequirement = row.constructionRequirement,
                            stats = row.facilityStats,
                        )
                    }
            ShipPartCategory.SAIL ->
                boat.sailOption
                    .sortedBy { it.sailingRequirement }
                    .mapIndexed { i, row ->
                        BoatPartTier(
                            index = base + i,
                            name = row.name,
                            sailingRequirement = row.sailingRequirement,
                            constructionRequirement = row.constructionRequirement,
                            stats = row.facilityStats,
                        )
                    }
            ShipPartCategory.STEERING ->
                boat.steeringOption
                    .sortedBy { it.sailingRequirement }
                    .mapIndexed { i, row ->
                        BoatPartTier(
                            index = base + i,
                            name = row.name,
                            sailingRequirement = row.sailingRequirement,
                            constructionRequirement = row.constructionRequirement,
                            // Steering's facility_stats column is optional.
                            stats = row.facilityStats,
                        )
                    }
            ShipPartCategory.BRAZIER ->
                listOfNotNull(boat.brazierOption).mapIndexed { i, row ->
                    BoatPartTier(
                        index = base + i,
                        name = row.name,
                        sailingRequirement = row.sailingRequirement,
                        constructionRequirement = row.constructionRequirement,
                        stats = row.facilityStats,
                    )
                }
            ShipPartCategory.TRIM ->
                boat.trimOption
                    .sortedBy { it.sailingRequirement }
                    .mapIndexed { i, row ->
                        BoatPartTier(
                            index = base + i,
                            name = row.name,
                            sailingRequirement = row.sailingRequirement,
                            constructionRequirement = row.constructionRequirement,
                            stats = null,
                        )
                    }
            ShipPartCategory.FLAG ->
                boat.flagOption
                    .sortedBy { it.sailingRequirement }
                    .mapIndexed { i, row ->
                        BoatPartTier(
                            index = base + i,
                            name = row.name,
                            sailingRequirement = row.sailingRequirement,
                            constructionRequirement = row.constructionRequirement,
                            stats = null,
                        )
                    }
            ShipPartCategory.SAIL_PATTERN ->
                boat.sailPatternOption
                    .sortedBy { it.sailingRequirement }
                    .mapIndexed { i, row ->
                        BoatPartTier(
                            index = base + i,
                            name = row.name,
                            sailingRequirement = row.sailingRequirement,
                            constructionRequirement = row.constructionRequirement,
                            stats = null,
                        )
                    }
        }
    }

    fun partTier(player: Player, slot: Int, part: ShipPartCategory): Int =
        player.readSailingVar(part.varSymbol(slot))

    fun setPartTier(player: Player, slot: Int, part: ShipPartCategory, tier: Int) {
        player.writeSailingVar(part.varSymbol(slot), tier)
    }

    fun fittedTier(player: Player, slot: Int, part: ShipPartCategory): BoatPartTier? {
        val boat = boat(player, slot) ?: return null
        val stored = partTier(player, slot, part)
        return tiers(boat, part).firstOrNull { it.index == stored }
    }

    fun fittedStats(player: Player, slot: Int): List<SailingBoatFacilityStatsRow> =
        ShipPartCategory.entries
            .filter { it.affectsStats }
            .mapNotNull { fittedTier(player, slot, it)?.stats }

    fun recoveryCost(player: Player, slot: Int): Int {
        val owned = owned(player, slot) ?: return 0
        val surcharge =
            fittedStats(player, slot).sumOf { it.boatAdditionalRecoveryCostPercentage ?: 0 }
        val scaled = owned.boat.retrievalCost.toLong() * (100L + surcharge) / 100L
        val damage = owned.missingHp.toLong() * REPAIR_GP_PER_MISSING_HP
        return (scaled + damage).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }

    fun repairCost(player: Player, slot: Int): Int {
        val owned = owned(player, slot) ?: return 0
        return owned.missingHp * REPAIR_GP_PER_MISSING_HP
    }

    fun registerPurchase(
        player: Player,
        slot: Int,
        boat: SailingBoatRow,
        dock: SailingDocks.SailingDock,
    ) {
        require(slot in 1..SailingConstants.MAX_BOAT_SLOTS) { "Boat slot out of range: $slot" }
        player.writeSailingVar(SailingBoatVars.owned(slot), 1)
        player.writeSailingVar(SailingBoatVars.field(slot, TYPE_FIELD), boat.typeId)
        player.writeSailingVar(SailingBoatVars.port(slot), dock.row.dockId)
        for (part in ShipPartCategory.entries) {
            player.writeSailingVar(part.varSymbol(slot), 0)
        }
        refreshMaxHp(player, slot)
        player.sailingSelectedBoatSlot = slot
    }

    fun moveTo(player: Player, slot: Int, dock: SailingDocks.SailingDock) {
        player.writeSailingVar(SailingBoatVars.port(slot), dock.row.dockId)
    }

    fun setHp(player: Player, slot: Int, hp: Int) {
        val max = maxHp(player, slot)
        player.writeSailingVar(SailingBoatVars.field(slot, STORED_HP_FIELD), hp.coerceIn(0, max))
    }

    fun repairFully(player: Player, slot: Int) {
        player.writeSailingVar(SailingBoatVars.field(slot, STORED_HP_FIELD), maxHp(player, slot))
    }

    fun refreshMaxHp(player: Player, slot: Int) {
        val hullHp = fittedTier(player, slot, ShipPartCategory.HULL)?.stats?.boatHpMax ?: 0
        val keelHp = fittedTier(player, slot, ShipPartCategory.KEEL)?.stats?.boatHpMax ?: 0
        val max = hullHp + keelHp
        player.writeSailingVar(SailingBoatVars.field(slot, STORED_MAX_HP_FIELD), max)
        player.writeSailingVar(SailingBoatVars.field(slot, STORED_HP_FIELD), max)
    }

    fun clearSlot(player: Player, slot: Int) {
        require(slot in 1..SailingConstants.MAX_BOAT_SLOTS) { "Boat slot out of range: $slot" }
        player.writeSailingVar(SailingBoatVars.owned(slot), 0)
        player.writeSailingVar(SailingBoatVars.field(slot, TYPE_FIELD), 0)
        player.writeSailingVar(SailingBoatVars.port(slot), 0)
        for (part in ShipPartCategory.entries) {
            player.writeSailingVar(part.varSymbol(slot), 0)
        }
        player.writeSailingVar(SailingBoatVars.field(slot, STORED_HP_FIELD), 0)
        player.writeSailingVar(SailingBoatVars.field(slot, STORED_MAX_HP_FIELD), 0)
    }

    companion object {
        private const val TYPE_FIELD: String = "type"

        private const val STORED_HP_FIELD: String = "stored_hp"

        private const val STORED_MAX_HP_FIELD: String = "stored_maxhp"

        const val REPAIR_GP_PER_MISSING_HP: Int = 50
    }
}
