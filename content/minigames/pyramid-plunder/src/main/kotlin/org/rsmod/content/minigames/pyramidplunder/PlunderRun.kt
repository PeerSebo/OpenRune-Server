package org.rsmod.content.minigames.pyramidplunder

import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.content.minigames.pyramidplunder.PlunderState.applyPlunderExitReset
import org.rsmod.content.minigames.pyramidplunder.PlunderState.plunderPlayedBefore
import org.rsmod.content.minigames.pyramidplunder.PlunderState.plunderRoom
import org.rsmod.content.minigames.pyramidplunder.PlunderState.plunderRoomTier
import org.rsmod.content.minigames.pyramidplunder.PlunderState.plunderThievingRequired
import org.rsmod.content.minigames.pyramidplunder.PlunderState.plunderTimerCount
import org.rsmod.content.minigames.pyramidplunder.PlunderState.resetPlunderRoomContainers
import org.rsmod.content.minigames.pyramidplunder.configs.PlunderLocs
import org.rsmod.content.minigames.pyramidplunder.configs.PlunderRooms
import org.rsmod.events.EventBus

object PlunderRun {
    const val RUN_DURATION_CYCLES: Int = 500

    fun ProtectedAccess.startPlunderRun(worldState: PlunderWorldStateManager) {
        worldState.rerollTombDoors()
        player.plunderPlayedBefore = true
        player.plunderTimerCount = 0
        enterRoom(1)
        timer(PlunderTimer.RUN_TIMER, RUN_DURATION_CYCLES)
        softTimer(PlunderTimer.TICK_TIMER, 1)
        ifOpenOverlay(PlunderLocs.OVERLAY_INTERFACE)
    }

    fun ProtectedAccess.advancePlunderRoom() {
        enterRoom(player.plunderRoom + 1)
    }

    fun ProtectedAccess.exitPlunderRun(eventBus: EventBus) {
        clearTimer(PlunderTimer.RUN_TIMER)
        clearSoftTimer(PlunderTimer.TICK_TIMER)
        player.applyPlunderExitReset()
        telejump(PlunderLocs.EXIT_LANDING)
        player.ifCloseOverlay(PlunderLocs.OVERLAY_INTERFACE, eventBus)
    }

    fun ProtectedAccess.sceptreEject(eventBus: EventBus) {
        mes(
            "Guardian Mummy|You have found my Master's Sceptre! You have been here long " +
                "enough. Begone.",
            ChatType.Dialogue,
        )
        mes("<col=ff0000>You have found a Pharaoh's sceptre!</col>")
        exitPlunderRun(eventBus)
    }

    private fun ProtectedAccess.enterRoom(room: Int) {
        val row = PlunderRooms.forRoom(room)
        player.resetPlunderRoomContainers()
        player.plunderRoom = room
        player.plunderRoomTier = room + 1
        player.plunderThievingRequired = row.levelReq
        val landing =
            PlunderLocs.ROOM_LANDINGS[room] ?: error("No landing tile for plunder room: $room")
        telejump(landing)
    }
}

object PlunderTimer {
    const val RUN_TIMER = "timer.ntk_run_timer"

    const val TICK_TIMER = "timer.ntk_timer_sync"
}
