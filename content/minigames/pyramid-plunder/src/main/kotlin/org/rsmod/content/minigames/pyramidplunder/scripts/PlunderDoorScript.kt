package org.rsmod.content.minigames.pyramidplunder.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseThievingLvl
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.utils.skills.SkillingSuccessRate
import org.rsmod.content.minigames.pyramidplunder.PlunderRun.advancePlunderRoom
import org.rsmod.content.minigames.pyramidplunder.PlunderRun.exitPlunderRun
import org.rsmod.content.minigames.pyramidplunder.PlunderState.plunderRoom
import org.rsmod.content.minigames.pyramidplunder.PlunderState.setDoorState
import org.rsmod.content.minigames.pyramidplunder.PlunderWorldStateManager
import org.rsmod.content.minigames.pyramidplunder.configs.PlunderLocs
import org.rsmod.content.minigames.pyramidplunder.configs.PlunderRooms
import org.rsmod.events.EventBus
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class PlunderDoorScript
@Inject
constructor(
    private val worldState: PlunderWorldStateManager,
    private val worldRepo: WorldRepository,
    private val random: GameRandom,
    private val eventBus: EventBus,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(PlunderLocs.TOMB_DOOR_LOCKED) { door ->
            pickLock(doorIndex(door.loc.id), door.loc)
        }
        onOpLoc1(PlunderLocs.TOMB_DOOR_UNLOCKED) { door -> enterOpenDoor(doorIndex(door.loc.id)) }

        onPlayerQueueWithArgs<PickTask>(QUEUE_DOOR_PICK) { runStage(it.args) }
    }

    private fun doorIndex(parentLocId: Int): Int =
        PlunderLocs.TOMB_DOOR_PARENT_TO_INDEX[parentLocId]
            ?: error("Unmapped Pyramid Plunder tomb door parent loc id: $parentLocId")

    private enum class PickStage {
        STAGE2,
        ADVANCE,
        DEAD_END,
    }

    private data class PickTask(val stage: PickStage, val room: Int)

    private suspend fun ProtectedAccess.pickLock(door: Int, locInfo: BoundLocInfo) {
        arriveDelay()
        val room = player.plunderRoom
        if (room == 0) {
            return
        }
        val info = PlunderRooms.forRoom(room)
        if (player.baseThievingLvl < info.levelReq) {
            mes(
                "You require a Thieving skill of ${info.levelReq} to open this door.",
                ChatType.Mesbox,
            )
            return
        }

        val correct = door == worldState.correctTombDoor(room)
        if (room == LAST_ROOM && correct) {
            mes(
                "This is the last room, opening this door will cause you to leave the game. " +
                    "Do you really want to leave the Tomb?",
                ChatType.Mesbox,
            )
            val leave = choice2("Yes.", true, "No.", false)
            if (leave) {
                exitPlunderRun(eventBus)
            }
            return
        }

        val usingLockpick = invTotal(inv, PlunderLocs.LOCKPICK) > 0
        spam(
            if (usingLockpick) "You use your lockpick and attempt to open the door."
            else "You attempt to open the door. Lockpicks would make it easier...",
        )
        anim(PlunderLocs.SEQ_SEARCH)
        soundSynth(PlunderLocs.SYNTH_PICK)

        // The lockpick's success-rate effect is inferred.
        val low = if (usingLockpick) PlunderRooms.DOOR_LOCKPICK_LOW else PlunderRooms.DOOR_LOW
        val high = if (usingLockpick) PlunderRooms.DOOR_LOCKPICK_HIGH else PlunderRooms.DOOR_HIGH
        val rate =
            SkillingSuccessRate.successRate(
                low,
                high,
                player.baseThievingLvl,
                MAX_LEVEL,
                flatBonus = -(room - 1.0),
            )
        if (rate <= random.randomDouble()) {
            spam("Your attempt fails.")
            return
        }

        player.setDoorState(door, true)
        val baseXp = info.doorXp
        if (baseXp != null) {
            statAdvance("stat.thieving", if (usingLockpick) baseXp / 2.0 else baseXp)
        }
        worldRepo.locAnim(locInfo, PlunderLocs.SEQ_TOMB_DOOR_SWING)
        worldRepo.soundArea(locInfo.coords, PlunderLocs.SYNTH_DOOR_SWING, radius = 5)

        // Interaction-phase queue delays are literal tick gaps (docs/skills.md §2).
        queue(QUEUE_DOOR_PICK, cycles = 1, args = PickTask(PickStage.STAGE2, room))
        val outcome = if (correct) PickStage.ADVANCE else PickStage.DEAD_END
        queue(QUEUE_DOOR_PICK, cycles = 3, args = PickTask(outcome, room))
    }

    private fun ProtectedAccess.runStage(task: PickTask) {
        if (player.plunderRoom != task.room) {
            return
        }
        when (task.stage) {
            PickStage.STAGE2 -> {
                anim(PlunderLocs.SEQ_PICK_STAGE2)
                soundSynth(PlunderLocs.SYNTH_PICK_STAGE2)
            }
            PickStage.ADVANCE -> advancePlunderRoom()
            PickStage.DEAD_END -> {
                mes("This door leads to a dead end.")
                soundSynth(PlunderLocs.SYNTH_DEAD_END_1, delay = DEAD_END_SYNTH_DELAY)
                soundSynth(PlunderLocs.SYNTH_DEAD_END_2, delay = DEAD_END_SYNTH_DELAY)
            }
        }
    }

    private suspend fun ProtectedAccess.enterOpenDoor(door: Int) {
        arriveDelay()
        val room = player.plunderRoom
        if (room == 0) {
            return
        }
        if (door == worldState.correctTombDoor(room) && room < LAST_ROOM) {
            advancePlunderRoom()
        } else {
            mes("You've already opened this door and it leads to a dead end.")
        }
    }

    private companion object {
        const val MAX_LEVEL = 99
        const val LAST_ROOM = 8

        // Synth delay is in client cycles.
        const val DEAD_END_SYNTH_DELAY = 20

        const val QUEUE_DOOR_PICK = "queue.ntk_door_pick"
    }
}
