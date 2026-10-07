package org.rsmod.content.skills.sailing

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.ItemServerType
import dev.openrune.util.Wearpos
import java.util.Collections
import java.util.WeakHashMap
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.worn.WornUnequipOp
import org.rsmod.api.table.sailing.SailingBoatCargoholdWhitelistObjRow
import org.rsmod.api.table.sailing.SailingBoatFacilityStatsRow
import org.rsmod.api.table.sailing.SailingBoatHotspotRow
import org.rsmod.content.skills.sailing.configs.SailingBoats
import org.rsmod.content.skills.sailing.configs.SailingConstants
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory
import org.rsmod.game.type.getOrNull
import org.rsmod.game.type.normalize

internal fun Player.cargoHold(boatSlot: Int): Inventory =
    invMap.getOrPut(SailingConstants.cargoHoldInv(boatSlot))

internal var Player.cargoHoldDrawStacked: Boolean by boolVarBit("varbit.cargo_hold_draw_stacked")

internal class CargoHoldSession(
    val boatSlot: Int,
    var quantityMode: CargoQuantityMode = CargoQuantityMode.One,
    var lastXInput: Int = 1,
)

internal enum class CargoQuantityMode {
    One,
    Five,
    Ten,
    X,
    All,
}

internal object CargoHoldSessions {
    private val sessions: MutableMap<Player, CargoHoldSession> =
        Collections.synchronizedMap(WeakHashMap())

    fun open(player: Player, boatSlot: Int): CargoHoldSession {
        val session = CargoHoldSession(boatSlot)
        sessions[player] = session
        return session
    }

    fun current(player: Player): CargoHoldSession? = sessions[player]

    fun close(player: Player) {
        sessions.remove(player)
    }
}

internal fun ProtectedAccess.tryOpenCargoHold(boatSlot: Int): Boolean {
    if (!player.ownsBoat(boatSlot)) {
        return false
    }
    CargoHoldSessions.open(player, boatSlot)
    ifOpenMainSidePair(
        main = "interface.sailing_boat_cargohold",
        side = "interface.sailing_boat_cargohold_side",
    )
    return true
}

internal object SailingCargoCategories {
    val COURIER_CARGO: Int get() = "category.sailing_courier_cargo".asRSCM()

    val BOUNTY_HARVEST: Int get() = "category.sailing_bounty_harvest".asRSCM()

    val SHIPWRECK_SALVAGE: Int get() = "category.sailing_shipwreck_salvage".asRSCM()

    val FILLED_FISH_CRATE: Int get() = "category.sailing_filled_fish_crate".asRSCM()

    val REPAIR_KIT: Int get() = "category.sailing_repair_kit".asRSCM()

    const val CANNONBALL: String = "category.cannonball"
}

internal object SailingCargoHold {
    const val DBROW_WHITELIST: String = "dbrow.sailing_boat_cargohold_whitelist_obj_lookup"

    // Hotspot values are facility_bottle_id, not option indices; zero means empty.
    const val HOTSPOT_EMPTY: Int = 0

    private val whitelistRow: SailingBoatCargoholdWhitelistObjRow by lazy {
        SailingBoatCargoholdWhitelistObjRow.getRow(DBROW_WHITELIST)
    }

    private val whitelistObjIds: Set<Int> by lazy { whitelistRow.entry.mapTo(HashSet()) { it.id } }

    private val whitelistCategories: Set<Int> by lazy { whitelistRow.entryCategory.toHashSet() }

    fun canStore(type: ItemServerType): Boolean {
        val normalized = normalize(type)
        if (normalized.id in whitelistObjIds) {
            return true
        }
        return normalized.category != -1 && normalized.category in whitelistCategories
    }

    fun fittedStats(player: Player, boatSlot: Int): SailingBoatFacilityStatsRow? {
        if (!player.ownsBoat(boatSlot)) {
            return null
        }
        val typeId = player.readSailingVar(SailingBoatVars.field(boatSlot, "type"))
        val boat = SailingBoats.byTypeId(typeId) ?: return null
        for (hotspot in SailingBoats.hotspots(boat)) {
            val stats = hotspotFacilityStats(player, boatSlot, hotspot) ?: continue
            if (stats.boatCargoholdSize != null) {
                return stats
            }
        }
        return null
    }

    private fun hotspotFacilityStats(
        player: Player,
        boatSlot: Int,
        hotspot: SailingBoats.BoatHotspot,
    ): SailingBoatFacilityStatsRow? {
        val built = player.readSailingVar(SailingBoatVars.hotspot(boatSlot, hotspot.hotspotIndex))
        if (built == HOTSPOT_EMPTY) {
            return null
        }
        val layout = SailingBoatHotspotRow.getRow(hotspot.layoutRowId)
        val facility = layout.option.firstOrNull { it.facilityBottleId == built } ?: return null
        return facility.facilityStats
    }

    // Use the fitted facility's capacity, not the backing inventory's 240-slot size.
    fun capacity(player: Player, boatSlot: Int): Int =
        SailingBoats.usableCargoCapacity(fittedStats(player, boatSlot))

    fun isCourierCargo(type: ItemServerType): Boolean =
        normalize(type).category == SailingCargoCategories.COURIER_CARGO

    fun isShipwreckSalvage(type: ItemServerType): Boolean =
        normalize(type).category == SailingCargoCategories.SHIPWRECK_SALVAGE
}

internal object SailingCargoWipe {
    private val TRAWLED_RAW_FISH: List<String> =
        listOf(
            "obj.raw_giant_krill",
            "obj.raw_haddock",
            "obj.raw_yellowfin",
            "obj.raw_halibut",
            "obj.raw_bluefin",
            "obj.raw_marlin",
        )

    private const val OBJ_FINE_FISH_OFFCUTS_KEPT: String = "obj.sailing_fine_fish_offcuts"

    private const val OBJ_FISH_CRATE_EMPTY_KEPT: String = "obj.fish_crate_empty"

    fun isWiped(type: ItemServerType): Boolean {
        val normalized = normalize(type)

        // Test retained forms before broader category wipe rules.
        if (normalized.isType(OBJ_FINE_FISH_OFFCUTS_KEPT)) {
            return false
        }
        if (normalized.isType(OBJ_FISH_CRATE_EMPTY_KEPT)) {
            return false
        }
        if (normalized.isCategoryType(SailingCargoCategories.CANNONBALL)) {
            return false
        }
        if (normalized.category == SailingCargoCategories.REPAIR_KIT) {
            return false
        }

        if (TRAWLED_RAW_FISH.any { normalized.isType(it) }) {
            return true
        }

        return when (normalized.category) {
            SailingCargoCategories.COURIER_CARGO -> true
            SailingCargoCategories.BOUNTY_HARVEST -> true
            SailingCargoCategories.SHIPWRECK_SALVAGE -> true
            SailingCargoCategories.FILLED_FISH_CRATE -> true
            else -> false
        }
    }

    fun wipe(player: Player, eventBus: EventBus, boatSlot: Int? = null): Int {
        var destroyed = 0
        destroyed += wipeInventory(player.inv)
        destroyed += wipeWorn(player, eventBus)
        if (boatSlot != null) {
            destroyed += wipeInventory(player.cargoHold(boatSlot))
        }
        return destroyed
    }

    private fun wipeInventory(inventory: Inventory): Int {
        var destroyed = 0
        for (slot in inventory.indices) {
            val type = getOrNull(inventory[slot]) ?: continue
            if (!isWiped(type)) {
                continue
            }
            inventory[slot] = null
            destroyed++
        }
        return destroyed
    }

    private fun wipeWorn(player: Player, eventBus: EventBus): Int {
        var destroyed = 0
        for (slot in player.worn.indices) {
            val type = getOrNull(player.worn[slot]) ?: continue
            if (!isWiped(type)) {
                continue
            }
            player.worn[slot] = null
            destroyed++

            val wearpos = Wearpos[type.wearpos1]
            if (wearpos != null) {
                WornUnequipOp.notifyWornUnequip(player, wearpos, type, eventBus)
            }
        }
        return destroyed
    }
}
