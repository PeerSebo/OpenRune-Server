package org.rsmod.content.skills.sailing

import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.content.skills.sailing.configs.SailingConstants
import org.rsmod.game.entity.Player

internal const val VARBIT_ON_PLAYER_BOAT: String = "varbit.sailing_player_is_on_player_boat"

internal const val VARBIT_FACILITY_LOCKEDIN: String = "varbit.sailing_boat_facility_lockedin"

internal var Player.sailingOnPlayerBoat: Int by intVarBit(VARBIT_ON_PLAYER_BOAT)

internal fun Player.isOnPlayerBoat(): Boolean = sailingOnPlayerBoat == ON_PLAYER_BOAT_ABOARD

internal const val ON_PLAYER_BOAT_ABOARD: Int = 1

internal const val ON_PLAYER_BOAT_ASHORE: Int = 0

internal var Player.sailingFacilityLockedIn: Int by intVarBit(VARBIT_FACILITY_LOCKEDIN)

// boardedBoat is one bit, not the owned-slot index.
internal var Player.boardedBoat: Int by intVarBit("varbit.sailing_boarded_boat")

internal var Player.sailingSelectedBoatSlot: Int by
    intVarBit("varbit.sailing_previous_boat_data_slot")

internal var Player.boardedBoatType: Int by intVarBit("varbit.sailing_boarded_boat_type")

internal var Player.boardedBoatTypeStored: Int by
    intVarBit("varbit.sailing_boarded_boat_type_stored")

internal var Player.boardedBoatWorld: Int by intVarBit("varbit.sailing_boarded_boat_world")

internal var Player.boardedBoatWorldStored: Int by
    intVarBit("varbit.sailing_boarded_boat_world_stored")

internal var Player.sailingLastDock: Int by intVarBit("varbit.sailing_boarded_boat_last_dock")

internal var Player.sailingLastStandardDock: Int by
    intVarBit("varbit.sailing_boarded_boat_last_standard_dock")

internal var Player.sailingLastMooringPoint: Int by
    intVarBit("varbit.sailing_boarded_boat_last_mooring_point")

internal var Player.keepBoatWhenLeavingHelm: Boolean by
    boolVarBit("varbit.keep_sailing_boat_when_leaving_helm")

internal var Player.portTaskOpenedSlot: Int by intVarBit("varbit.sailing_port_task_opened_slot")

internal fun Player.readSailingVar(symbol: String): Int = vars[symbol]

internal fun Player.writeSailingVar(symbol: String, value: Int) {
    VarPlayerIntMapSetter.set(this, symbol, value)
}

internal fun Player.ownsBoat(slot: Int): Boolean = readSailingVar(SailingBoatVars.owned(slot)) != 0

internal fun Player.ownedBoatSlots(): List<Int> =
    (1..SailingConstants.MAX_BOAT_SLOTS).filter { ownsBoat(it) }

internal fun Player.activeBoatSlot(): Int? {
    val owned = ownedBoatSlots()
    if (owned.isEmpty()) {
        return null
    }
    val selected = sailingSelectedBoatSlot
    if (selected in owned) {
        return selected
    }
    if (isOnPlayerBoat()) {
        val boardedType = boardedBoatType
        val match =
            owned.firstOrNull { readSailingVar(SailingBoatVars.field(it, "type")) == boardedType }
        if (match != null) {
            return match
        }
    }
    return owned.first()
}

internal object SailingBoatVars {

    const val MAX_HOTSPOTS: Int = 13

    // Boat names store three word-table indices.
    const val NAME_PARTS: Int = 3

    fun field(slot: Int, field: String): String {
        require(slot in 1..SailingConstants.MAX_BOAT_SLOTS) { "Boat slot out of range: $slot" }
        return "varbit.sailing_boat_${slot}_$field"
    }

    fun owned(slot: Int): String = field(slot, "owned")

    fun port(slot: Int): String = field(slot, "port")

    fun keel(slot: Int): String = field(slot, "keel")

    fun hull(slot: Int): String = field(slot, "hull")

    fun sail(slot: Int): String = field(slot, "sail")

    fun flag(slot: Int): String = field(slot, "flag")

    fun brazier(slot: Int): String = field(slot, "brazier")

    fun name(slot: Int, part: Int): String {
        require(part in 1..NAME_PARTS) { "Boat name part out of range: $part" }
        return field(slot, "name_$part")
    }

    fun hotspot(slot: Int, index: Int): String {
        require(index in 0 until MAX_HOTSPOTS) { "Hotspot index out of range: $index" }
        return field(slot, "hotspot_$index")
    }

    fun hotspotExtraData(slot: Int, index: Int): String {
        require(index in 0 until MAX_HOTSPOTS) { "Hotspot index out of range: $index" }
        return field(slot, "hotspot_${index}_extra_data")
    }
}
