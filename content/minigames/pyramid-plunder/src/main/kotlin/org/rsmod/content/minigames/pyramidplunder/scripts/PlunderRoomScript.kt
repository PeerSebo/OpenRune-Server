package org.rsmod.content.minigames.pyramidplunder.scripts

import jakarta.inject.Inject
import kotlin.math.abs
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.forcedWalk
import org.rsmod.api.player.stat.baseThievingLvl
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onPlayerQueue
import org.rsmod.api.utils.skills.SkillingSuccessRate
import org.rsmod.content.minigames.pyramidplunder.PlunderRun.exitPlunderRun
import org.rsmod.content.minigames.pyramidplunder.PlunderState.plunderRoom
import org.rsmod.content.minigames.pyramidplunder.PlunderState.plunderTrapActive
import org.rsmod.content.minigames.pyramidplunder.configs.PlunderLocs
import org.rsmod.content.minigames.pyramidplunder.configs.PlunderRooms
import org.rsmod.events.EventBus
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class PlunderRoomScript
@Inject
constructor(private val random: GameRandom, private val eventBus: EventBus) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(PlunderLocs.SPEAR_TRAP) { passSpearTrap() }

        onOpLoc1(PlunderLocs.TOMB_DOOR_EXIT) { confirmLeave() }
        onOpLoc2(PlunderLocs.TOMB_DOOR_EXIT) { queue(QUEUE_EXIT, cycles = 2) }
        // Antechamber exit must work before a run starts.
        onOpLoc1(PlunderLocs.ANTECHAMBER_EXIT) { queue(QUEUE_EXIT, cycles = 1) }
        onPlayerQueue(QUEUE_EXIT) { exitPlunderRun(eventBus) }
    }

    private suspend fun ProtectedAccess.passSpearTrap() {
        arriveDelay()
        val room = player.plunderRoom
        if (room == 0) {
            return
        }
        val band = PlunderLocs.TRAP_BANDS[room] ?: return
        val landing = PlunderLocs.ROOM_LANDINGS[room] ?: return

        if (!player.plunderTrapActive) {
            // Disarming does not remove the invisible wall; repeat crossings still require forced movement.
            crossTrapBand(band, landing)
            return
        }

        val info = PlunderRooms.forRoom(room)
        if (player.baseThievingLvl < info.levelReq) {
            mes("You need a Thieving level of ${info.levelReq} to pass this trap.", ChatType.Mesbox)
            return
        }

        spam("You carefully try to temporarily deactivate the trap mechanism.")
        val rate =
            SkillingSuccessRate.successRate(
                PlunderRooms.TRAP_LOW,
                PlunderRooms.TRAP_HIGH,
                player.baseThievingLvl,
                MAX_LEVEL,
            )
        val success = rate > random.randomDouble()

        delay(1)
        anim(PlunderLocs.SEQ_SEARCH)
        soundSynth(PlunderLocs.SYNTH_OUTER_SEARCH)
        delay(2)
        anim(PlunderLocs.SEQ_SEARCH)
        delay(2)
        if (!success) {
            spam("You fail to deactivate the trap.")
            soundSynth(PlunderLocs.SYNTH_TRAP_FAIL)
            // Spear-trap damage 1-4 is provisional; knockback is not implemented.
            queueHit(delay = 1, type = HitType.Typeless, damage = random.of(1, 4))
            return
        }
        spam("You deactivate the trap!")
        soundSynth(PlunderLocs.SYNTH_TRAP_DISARM)
        // Clear trap state before crossing to prevent a second roll during the movement window.
        player.plunderTrapActive = false
        statAdvance("stat.thieving", PlunderRooms.TRAP_XP)
        crossTrapBand(band, landing)
    }

    // Forced movement bypasses the static trap band; ordinary routing cannot cross it.
    private suspend fun ProtectedAccess.crossTrapBand(
        band: PlunderLocs.TrapBand,
        landing: CoordGrid,
    ) {
        val dest = band.crossDestination(player.coords, landing) ?: return
        val tiles =
            when (band.axis) {
                PlunderLocs.TrapAxis.X -> abs(player.coords.x - dest.x)
                PlunderLocs.TrapAxis.Z -> abs(player.coords.z - dest.z)
            }
        if (tiles <= 0) {
            return
        }
        forcedWalk(dest, tiles)
    }

    private suspend fun ProtectedAccess.confirmLeave() {
        mes("Do you really want to leave the Tomb?", ChatType.Mesbox)
        val leave = choice2("Yes.", true, "No.", false)
        if (leave) {
            exitPlunderRun(eventBus)
        }
    }

    private companion object {
        const val MAX_LEVEL = 99
        const val QUEUE_EXIT = "queue.ntk_exit"
    }
}
