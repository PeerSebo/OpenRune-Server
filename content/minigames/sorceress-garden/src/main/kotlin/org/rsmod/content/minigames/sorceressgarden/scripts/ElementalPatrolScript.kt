package org.rsmod.content.minigames.sorceressgarden.scripts

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import org.rsmod.api.hunt.PlayerSearch
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onAiTimer
import org.rsmod.content.minigames.sorceressgarden.configs.ElementalPatrols
import org.rsmod.content.minigames.sorceressgarden.configs.GardenSeason
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// Vision follows the last actual movement step, not the queued waypoint.
// Resolve contact reactions before advancing patrol movement.
class ElementalPatrolScript
@Inject
constructor(
    private val playerSearch: PlayerSearch,
    private val protectedAccess: ProtectedAccessLauncher,
    private val worldRepo: WorldRepository,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for ((npcSymbol, patrol) in ElementalPatrols.all) {
            val state =
                PatrolState(
                    index = 0,
                    lastStep =
                        checkNotNull(stepDelta(patrol.loop[0], patrol.loop[1 % patrol.loop.size])),
                )
            onAiTimer(npcSymbol) {
                val moved = stepDelta(state.lastCoords, npc.coords)
                if (state.lastCoords != CoordGrid.NULL && moved != null && moved != Step.None) {
                    state.lastStep = moved
                }
                state.lastCoords = npc.coords
                npc.patrolTick(patrol, state)
            }
        }
    }

    private fun Npc.patrolTick(patrol: ElementalPatrols.Patrol, state: PatrolState) {
        aiTimer(1)

        val intruderSlot = state.intruderSlot
        state.intruderSlot = -1
        val pendingCastSlot = state.pendingCastSlot
        state.pendingCastSlot = -1

        if (pendingCastSlot != -1 && castAtAdjacent(this, patrol.season, pendingCastSlot)) {
            return
        }

        if (spotPlayers(this, patrol.season, state.lastStep)) {
            return
        }

        if (intruderSlot != -1 && chaseIntruder(this, patrol.season, intruderSlot, state)) {
            return
        }

        val sameTile =
            playerSearch
                .findAll(coords, 1, HuntVis.LineOfSight)
                .firstOrNull { it.coords == coords }
        if (sameTile != null) {
            state.intruderSlot = sameTile.slotId
            return
        }

        val loop = patrol.loop
        var targetIndex = state.index
        if (coords == loop[targetIndex]) {
            targetIndex = (targetIndex + 1) % loop.size
        } else if (cyclesWithoutMovement >= StuckSkipCycles) {
            // Skip persistently blocked waypoints: the greedy walker does not retry routing.
            targetIndex = (targetIndex + 1) % loop.size
        }
        walk(loop[targetIndex])

        state.index = targetIndex
    }

    private fun chaseIntruder(
        npc: Npc,
        season: GardenSeason,
        intruderSlot: Int,
        state: PatrolState,
    ): Boolean {
        val intruder =
            playerSearch
                .findAll(npc.coords, DetectionRange, HuntVis.LineOfSight)
                .firstOrNull { it.slotId == intruderSlot } ?: return false
        if (intruder.coords == npc.coords) {
            val caught = catchPlayer(npc, season, intruder, distance = 0)
            if (caught) {
                npc.resetFaceEntity()
                npc.anim(ElementalPointSeq)
            }
            return caught
        }
        val adjacent =
            playerSearch
                .findAll(npc.coords, 1, HuntVis.LineOfSight)
                .filter { it.coords != npc.coords && it.coords.level == npc.coords.level }
                .minByOrNull { it.slotId }
        if (adjacent != null) {
            npc.facePlayer(intruder)
            state.pendingCastSlot = adjacent.slotId
            return true
        }
        npc.facePlayer(intruder)
        npc.walk(chaseStep(npc.coords, intruder.coords))
        return true
    }

    private fun castAtAdjacent(npc: Npc, season: GardenSeason, targetSlot: Int): Boolean {
        val target =
            playerSearch
                .findAll(npc.coords, 1, HuntVis.LineOfSight)
                .firstOrNull {
                    it.slotId == targetSlot &&
                        it.coords != npc.coords &&
                        it.coords.level == npc.coords.level
                } ?: return false
        val caught = catchPlayer(npc, season, target, distance = 1)
        if (caught) {
            npc.resetFaceEntity()
            npc.faceSquare(target.coords)
            npc.anim(ElementalPointSeq)
        }
        return caught
    }

    private fun chaseStep(from: CoordGrid, towards: CoordGrid): CoordGrid =
        from.translate((towards.x - from.x).coerceIn(-1, 1), (towards.z - from.z).coerceIn(-1, 1))

    private fun spotPlayers(npc: Npc, season: GardenSeason, facing: Step): Boolean {
        val candidates = playerSearch.findAll(npc.coords, DetectionRange, HuntVis.LineOfSight)
        var cast = false
        for (player in candidates) {
            val distance = facing.stepsTo(npc.coords, player.coords) ?: continue
            val caught = catchPlayer(npc, season, player, distance)
            if (caught && !cast) {
                // Clear entity-facing before the cast's coordinate-facing update.
                npc.resetFaceEntity()
                npc.faceSquare(player.coords)
                npc.anim(ElementalPointSeq)
                cast = true
            }
        }
        return cast
    }

    // Report launch failure so protected players do not consume a successful catch.
    private fun catchPlayer(
        npc: Npc,
        season: GardenSeason,
        player: Player,
        distance: Int,
    ): Boolean =
        protectedAccess.launch(player) {
            stopAction()
            spotanim(CurseImpactSpot, delay = 46 + 10 * distance, height = 124)
            worldRepo.projAnim(curseProjectile(npc, player, distance))
            soundSynth(SpottedSound)
            delay(2)
            mes("You've been spotted by an elemental and teleported out of its garden.")
            delay(2)
            telejump(season.catchEjectCoord)
            delay(1)
            soundSynth(EjectLandSound)
        }

    // Projectile timing uses client cycles; impact delay grows with tile distance.
    private fun curseProjectile(npc: Npc, target: Player, distance: Int): ProjAnim =
        ProjAnim(
            spotanim = CurseTravelSpot.asRSCM(RSCMType.SPOTANIM),
            startHeight = 31,
            endHeight = 31,
            startTime = 61,
            endTime = 46 + 10 * distance,
            angle = 16,
            progress = 0,
            sourceIndex = 0,
            targetIndex = -(target.slotId + 1),
            startCoord = npc.coords,
            endCoord = target.coords,
        )

    private class PatrolState(
        var index: Int,
        var lastStep: Step,
        var lastCoords: CoordGrid = CoordGrid.NULL,
        var intruderSlot: Int = -1,
        var pendingCastSlot: Int = -1,
    )

    private data class Step(val dx: Int, val dz: Int) {
        fun stepsTo(from: CoordGrid, to: CoordGrid): Int? {
            if (this == None || from.level != to.level) {
                return null
            }
            for (i in 1..DetectionRange) {
                if (from.translate(dx * i, dz * i) == to) {
                    return i
                }
            }
            return null
        }

        companion object {
            val None = Step(0, 0)
        }
    }

    private fun stepDelta(from: CoordGrid, to: CoordGrid): Step? {
        if (from == CoordGrid.NULL || from.level != to.level) {
            return null
        }
        val dx = to.x - from.x
        val dz = to.z - from.z
        if (dx == 0 && dz == 0) {
            return Step.None
        }
        if (dx !in -1..1 || dz !in -1..1) {
            return null
        }
        return Step(dx, dz)
    }

    private companion object {
        const val DetectionRange = 3

        const val StuckSkipCycles = 3

        const val ElementalPointSeq = "seq.100_osman_seasons_elemental_point"

        const val CurseImpactSpot = "spotanim.curse_impact"
        const val CurseTravelSpot = "spotanim.curse_travel"

        const val SpottedSound = "synth.STUN_ALL"
        const val EjectLandSound = "synth.STUNNED"
    }
}
