package org.rsmod.content.minigames.pyramidplunder.scripts

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.BasType
import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.mechanics.stun.Stun
import org.rsmod.api.mechanics.stun.isStunActionBlocked
import org.rsmod.api.mechanics.stun.stun
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseThievingLvl
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.utils.skills.SkillingSuccessRate
import org.rsmod.content.minigames.pyramidplunder.PlunderRun.startPlunderRun
import org.rsmod.content.minigames.pyramidplunder.PlunderState
import org.rsmod.content.minigames.pyramidplunder.PlunderState.outsideDoorState
import org.rsmod.content.minigames.pyramidplunder.PlunderState.setOutsideDoorState
import org.rsmod.content.minigames.pyramidplunder.PlunderWorldStateManager
import org.rsmod.content.minigames.pyramidplunder.configs.PlunderLocs
import org.rsmod.content.minigames.pyramidplunder.configs.PlunderRooms
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class PlunderEntryScript
@Inject
constructor(private val worldState: PlunderWorldStateManager, private val random: GameRandom) :
    PluginScript() {
    override fun ScriptContext.startup() {
        PlunderLocs.OUTER_DOORS.forEachIndexed { index, loc ->
            val direction = index + 1
            onOpLoc1(loc) { door -> searchOuterDoor(door.loc.coords, direction) }
        }
        PlunderLocs.OUTER_DOORS_OPEN.forEachIndexed { index, loc ->
            val direction = index + 1
            onOpLoc1(loc) { door -> searchOuterDoor(door.loc.coords, direction) }
        }

        onOpNpc1(PlunderLocs.MUMMY_GUARDIAN) { talkToGuardian() }
        onOpNpc3(PlunderLocs.MUMMY_GUARDIAN) { startMinigame() }

        onPlayerQueueWithArgs<OuterTask>(QUEUE_OUTER_SEARCH) { runStage(it.args) }
    }

    private enum class OuterStage {
        REPEAT,
        OPEN,
        PUNCH,
        KNOCKBACK,
        STUN_SPOT,

        // queueHit requires delay >= 1; queue at +6 so damage lands at +7.
        HIT,
        CLEAR,
    }

    private data class OuterTask(
        val stage: OuterStage,
        val direction: Int,
        val doorCoords: CoordGrid,
    )

    private suspend fun ProtectedAccess.searchOuterDoor(doorCoords: CoordGrid, direction: Int) {
        arriveDelay()
        if (isStunActionBlocked) {
            return
        }
        if (player.outsideDoorState(direction) == PlunderState.OUTER_DOOR_PUNCHING) {
            return
        }

        val correct = direction == worldState.correctOuterDoor
        if (correct && player.baseThievingLvl < PlunderRooms.OUTER_DOOR_LEVEL_REQ) {
            mes(
                "You need to have thieving skill of at least " +
                    "${PlunderRooms.OUTER_DOOR_LEVEL_REQ} to enter the pyramid of Jalsavrah.",
            )
            return
        }

        spam("You use your thieving skills to search the stone panel.")
        anim(PlunderLocs.SEQ_SEARCH)
        soundSynth(PlunderLocs.SYNTH_OUTER_SEARCH)
        val task = { stage: OuterStage -> OuterTask(stage, direction, doorCoords) }
        // Interaction-phase queue delays are literal tick gaps (docs/skills.md §2).
        queue(QUEUE_OUTER_SEARCH, cycles = 1, args = task(OuterStage.REPEAT))
        queue(QUEUE_OUTER_SEARCH, cycles = 2, args = task(OuterStage.REPEAT))

        if (outerRollPassed()) {
            queue(QUEUE_OUTER_SEARCH, cycles = 3, args = task(OuterStage.OPEN))
        } else {
            queue(QUEUE_OUTER_SEARCH, cycles = 3, args = task(OuterStage.PUNCH))
            queue(QUEUE_OUTER_SEARCH, cycles = 5, args = task(OuterStage.KNOCKBACK))
            queue(QUEUE_OUTER_SEARCH, cycles = 6, args = task(OuterStage.STUN_SPOT))
            queue(QUEUE_OUTER_SEARCH, cycles = 9, args = task(OuterStage.HIT))
            queue(QUEUE_OUTER_SEARCH, cycles = 10, args = task(OuterStage.CLEAR))
        }
    }

    private fun ProtectedAccess.outerRollPassed(): Boolean {
        val rate =
            SkillingSuccessRate.successRate(
                PlunderRooms.OUTER_DOOR_LOW,
                PlunderRooms.OUTER_DOOR_HIGH,
                player.baseThievingLvl,
                MAX_LEVEL,
            )
        return rate > random.randomDouble()
    }

    private suspend fun ProtectedAccess.runStage(task: OuterTask) {
        when (task.stage) {
            OuterStage.REPEAT -> {
                anim(PlunderLocs.SEQ_SEARCH)
                soundSynth(PlunderLocs.SYNTH_OUTER_SEARCH)
            }
            OuterStage.OPEN -> {
                // Abandoned searches must not pull the player back from a remote tile.
                if (player.coords.chebyshevDistance(task.doorCoords) > ABANDON_RANGE) {
                    return
                }
                val correct = task.direction == worldState.correctOuterDoor
                spam(
                    if (correct) "You find a door! You open it successfully."
                    else "You find a door! You open it.",
                )
                player.setOutsideDoorState(task.direction, PlunderState.OUTER_DOOR_OPEN)
                statAdvance("stat.thieving", PlunderRooms.OUTER_DOOR_XP)
                val chamber = worldState.outerDoorChamber(task.direction)
                telejump(PlunderLocs.ANTECHAMBER_LANDINGS[chamber])
            }
            OuterStage.PUNCH -> {
                if (player.coords.chebyshevDistance(task.doorCoords) > ABANDON_RANGE) {
                    return
                }
                player.setOutsideDoorState(task.direction, PlunderState.OUTER_DOOR_PUNCHING)
                // Keep movement locked until the punching leaf closes.
                stun(movementCycles = PUNCH_WINDOW_CYCLES)
            }
            OuterStage.KNOCKBACK -> {
                mes("A mysterious bandaged hand punches you away from the door.  Ouch!")
                soundSynth(PlunderLocs.SYNTH_PUNCH)
                knockBack(task.direction)
            }
            OuterStage.STUN_SPOT ->
                spotanim(PlunderLocs.SPOT_STUNNED, height = Stun.DEFAULT_SPOTANIM_HEIGHT)
            OuterStage.HIT ->
                queueHit(delay = 1, type = HitType.Typeless, damage = random.of(1, 4))
            OuterStage.CLEAR -> {
                player.setOutsideDoorState(task.direction, PlunderState.OUTER_DOOR_CLOSED)
                soundSynth(PlunderLocs.SYNTH_PUNCH_HIT)
            }
        }
    }

    // Hold the stumble BAS through forced movement; a normal animation would be replaced by walking.
    private suspend fun ProtectedAccess.knockBack(direction: Int) {
        val (dx, dz, face) =
            when (direction) {
                1 -> Triple(0, 1, constants.em_face_south)
                2 -> Triple(1, 0, constants.em_face_west)
                3 -> Triple(0, -1, constants.em_face_north)
                4 -> Triple(-1, 0, constants.em_face_east)
                else -> error("Invalid outer door direction: $direction")
            }
        val start = player.coords
        val end = start.translate(dx * KNOCKBACK_TILES, dz * KNOCKBACK_TILES)
        val stumble = PlunderLocs.SEQ_STUMBLE_BACK.asRSCM(RSCMType.SEQ)
        val previous = player.bas
        player.bas =
            BasType(
                readyAnim = stumble,
                turnOnSpot = stumble,
                walkForward = stumble,
                walkBack = stumble,
                walkLeft = stumble,
                walkRight = stumble,
                running = stumble,
            )
        player.rebuildAppearance()
        try {
            exactMove(start, end, delay1 = 0, delay2 = KNOCKBACK_CLIENT_CYCLES, dir = face)
            delay(1)
        } finally {
            player.bas = previous
            player.rebuildAppearance()
        }
    }

    // TODO: implement the Guardian mummy's first-time dialogue.
    private fun ProtectedAccess.talkToGuardian() {
        mes("The Guardian mummy silently gestures for you to start the minigame.")
    }

    // TODO: reproduce the native start teleport restrictions.
    private fun ProtectedAccess.startMinigame() {
        startPlunderRun(worldState)
    }

    private companion object {
        const val MAX_LEVEL = 99

        const val PUNCH_WINDOW_CYCLES = 7

        const val KNOCKBACK_TILES = 2
        const val KNOCKBACK_CLIENT_CYCLES = 30

        const val ABANDON_RANGE = 4

        const val QUEUE_OUTER_SEARCH = "queue.ntk_outer_search"
    }
}
