package org.rsmod.content.minigames.pyramidplunder

import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.game.entity.Player

object PlunderState {
    var Player.plunderRoom by intVarBit("varbit.ntk_room_number")

    // current_room_level stores room + 1, not the room number.
    var Player.plunderRoomTier by intVarBit("varbit.ntk_current_room_level")

    var Player.plunderThievingRequired by intVarBit("varbit.ntk_thieving_required")

    var Player.plunderTrapActive by boolVarBit("varbit.ntk_trap_active")

    var Player.plunderPlayedBefore by boolVarBit("varbit.ntk_played_before")

    var Player.plunderRightDoorOpened by boolVarBit("varbit.ntk_right_door_opened")

    var Player.plunderTimerCount by intVarBit("varbit.ntk_player_timer_count")

    var Player.plunderSarcophagusPush by boolVarBit("varbit.ntk_sarcophagus_push")

    // Sarcophagus transforms are closed=0, open=1, opening=2.
    var Player.plunderSarcophagusState by intVarBit("varbit.ntk_sarcophagus_state")

    var Player.plunderChestState by intVarBit("varbit.ntk_golden_chest_state")

    fun Player.doorState(door: Int): Boolean =
        when (door) {
            1 -> plunderDoor1
            2 -> plunderDoor2
            3 -> plunderDoor3
            4 -> plunderDoor4
            else -> error("Invalid tomb door index: $door")
        }

    fun Player.setDoorState(door: Int, unlocked: Boolean) {
        when (door) {
            1 -> plunderDoor1 = unlocked
            2 -> plunderDoor2 = unlocked
            3 -> plunderDoor3 = unlocked
            4 -> plunderDoor4 = unlocked
            else -> error("Invalid tomb door index: $door")
        }
    }

    var Player.plunderDoor1 by boolVarBit("varbit.ntk_door1_state")
    var Player.plunderDoor2 by boolVarBit("varbit.ntk_door2_state")
    var Player.plunderDoor3 by boolVarBit("varbit.ntk_door3_state")
    var Player.plunderDoor4 by boolVarBit("varbit.ntk_door4_state")

    // Outer-door indices are N/E/S/W; states are closed=0, punching=1, open=2.
    fun Player.outsideDoorState(door: Int): Int = vars["varbit.ntk_outside_door${door}_state"]

    fun Player.setOutsideDoorState(door: Int, value: Int) {
        VarPlayerIntMapSetter.set(this, "varbit.ntk_outside_door${door}_state", value)
    }

    // Urn states are closed=0, looted=1, snake=2, charmed=3.
    fun Player.urnState(urn: Int): Int = vars["varbit.ntk_urn${urn}_state"]

    fun Player.setUrnState(urn: Int, value: Int) {
        VarPlayerIntMapSetter.set(this, "varbit.ntk_urn${urn}_state", value)
    }

    fun Player.resetPlunderRoomContainers() {
        for (urn in 1..URN_COUNT) setUrnState(urn, 0)
        plunderSarcophagusPush = false
        plunderSarcophagusState = 0
        plunderChestState = 0
        plunderDoor1 = false
        plunderDoor2 = false
        plunderDoor3 = false
        plunderDoor4 = false
        plunderTrapActive = true
    }

    fun Player.applyPlunderExitReset() {
        resetPlunderRoomContainers()
        plunderRoom = 0
        plunderRoomTier = ROOM_TIER_RESET
        plunderThievingRequired = THIEVING_REQ_RESET
        plunderTimerCount = 0
        for (door in 1..4) setOutsideDoorState(door, 0)
    }

    const val URN_COUNT: Int = 15

    const val ROOM_TIER_RESET: Int = 2

    const val THIEVING_REQ_RESET: Int = 21

    const val OUTER_DOOR_CLOSED: Int = 0
    const val OUTER_DOOR_PUNCHING: Int = 1
    const val OUTER_DOOR_OPEN: Int = 2

    const val SARC_CLOSED: Int = 0
    const val SARC_OPEN: Int = 1
    const val SARC_OPENING: Int = 2
}
