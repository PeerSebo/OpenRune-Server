package org.rsmod.content.skills.thieving.scripts

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseHitpointsLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.thievingLvl
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.stats.levelmod.InvisibleLevels
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.generic.locs.doors.DoorTranslations
import org.rsmod.content.skills.thieving.configs.DoorTrap
import org.rsmod.content.skills.thieving.configs.GatePanel
import org.rsmod.content.skills.thieving.configs.PickLockDoor
import org.rsmod.content.skills.thieving.configs.ThievingDoors
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class PickLockDoorScript
@Inject
constructor(
    private val locRepo: LocRepository,
    private val xpMods: XpModifiers,
    private val invisibleLvls: InvisibleLevels,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (door in ThievingDoors.all) {
            onOpLoc1(door.closedLoc) { pickLock(it.loc, door) }
            onOpLoc2(door.closedLoc) { pickLock(it.loc, door) }
        }
        onPlayerQueueWithArgs<DoorPickTask>(ThievingDoors.PICK_QUEUE) { event ->
            finishPickLock(event.args)
        }
    }

    private data class DoorPickTask(val loc: BoundLocInfo, val door: PickLockDoor)

    private suspend fun ProtectedAccess.pickLock(loc: BoundLocInfo, door: PickLockDoor) {
        arriveDelay()

        if (actionDelay > mapClock) return

        if (door.requiresLockpick && invTotal(inv, ThievingDoors.LOCKPICK) == 0) {
            mes("You need a lockpick to pick this lock.")
            return
        }

        if (!ThievingLocAction.start(player, loc, locRepo.findExact(loc.coords, loc.shape), 2)) return

        anim(ThievingDoors.PICKLOCK_ANIM)
        soundSynth(ThievingDoors.PICKLOCK_SOUND)

        // Queue committed effects so an interrupted animation cannot discard the trap roll or unlock.
        queue(ThievingDoors.PICK_QUEUE, cycles = 2, args = DoorPickTask(loc, door))
    }

    private fun ProtectedAccess.finishPickLock(task: DoorPickTask) {
        if (!ThievingLocAction.canFinish(player, task.loc, locRepo.findExact(task.loc.coords, task.loc.shape))) return
        val (low, high) = task.door.successRates

        // Trap and unlock rolls are independent; traps can hurt under-levelled players and successful unlocks.
        applyTrap(task.door, low, high)

        if (player.thievingLvl < task.door.levelReq) {
            mes("You need a Thieving level of ${task.door.levelReq} to pick this lock.")
            return
        }

        if (!statRandom("stat.thieving", low, high, invisibleLvls)) {
            mes("You fail to pick the lock.")
            return
        }

        openDoor(task.loc, task.door)
        spam("You manage to pick the lock.")
        statAdvance("stat.thieving", task.door.xp * xpMods.get(player, "stat.thieving"))
    }

    // TODO: Verify trap probabilities; they currently reuse the door success curve.
    private fun ProtectedAccess.applyTrap(door: PickLockDoor, low: Int, high: Int) {
        if (door.trap == DoorTrap.None) {
            return
        }
        if (statRandom("stat.thieving", low, high, invisibleLvls)) {
            return
        }
        val damage = door.trap.calculate(player.hitpoints, player.baseHitpointsLvl)
        if (damage <= 0) {
            return
        }
        mes("You trigger a trap hidden in the lock!")
        queueHit(delay = 1, type = HitType.Typeless, damage = damage)
    }

    private fun ProtectedAccess.openDoor(loc: BoundLocInfo, door: PickLockDoor) {
        when (door.gatePanel) {
            GatePanel.None -> {
                swapPanel(loc, door.openLoc, rotations = 1)
            }
            GatePanel.Left -> {
                swapPanel(loc, door.openLoc, rotations = 3)
                val partnerCoords =
                    DoorTranslations.translateClose(loc.coords, loc.shape, loc.angle)
                openPartner(loc, door, partnerCoords, rotations = 1)
            }
            GatePanel.Right -> {
                swapPanel(loc, door.openLoc, rotations = 1)
                val partnerCoords =
                    DoorTranslations.translateCloseOpposite(loc.coords, loc.shape, loc.angle)
                openPartner(loc, door, partnerCoords, rotations = 3)
            }
        }
    }

    private fun openPartner(
        clicked: BoundLocInfo,
        door: PickLockDoor,
        partnerCoords: CoordGrid,
        rotations: Int,
    ) {
        val partnerLoc = door.gatePartnerLoc ?: return
        val partnerOpenLoc = door.gatePartnerOpenLoc ?: return
        val found = locRepo.findExact(partnerCoords, clicked.shape) ?: return
        if (found.id != partnerLoc.asRSCM(RSCMType.LOC)) {
            return
        }
        swapPanel(found, partnerOpenLoc, rotations)
    }

    private fun swapPanel(panel: BoundLocInfo, openLoc: String, rotations: Int) {
        val openedAngle = panel.turnAngle(rotations)
        val openedCoords = DoorTranslations.translateOpen(panel.coords, panel.shape, panel.angle)
        locRepo.del(panel, ThievingDoors.OPEN_DURATION)
        locRepo.add(openedCoords, openLoc, ThievingDoors.OPEN_DURATION, openedAngle, panel.shape)
    }

    private fun swapPanel(panel: LocInfo, openLoc: String, rotations: Int) {
        val openedAngle = panel.angle.turn(rotations)
        val openedCoords = DoorTranslations.translateOpen(panel.coords, panel.shape, panel.angle)
        locRepo.del(panel, ThievingDoors.OPEN_DURATION)
        locRepo.add(openedCoords, openLoc, ThievingDoors.OPEN_DURATION, openedAngle, panel.shape)
    }
}
