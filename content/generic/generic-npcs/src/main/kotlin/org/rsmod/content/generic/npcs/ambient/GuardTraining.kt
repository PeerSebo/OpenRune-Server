package org.rsmod.content.generic.npcs.ambient

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import java.util.WeakHashMap
import org.rsmod.annotations.InternalApi
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onAiTimer
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class GuardTraining
@Inject
constructor(
    private val random: GameRandom,
    private val npcs: NpcRepository,
    private val locs: LocRepository,
    private val world: WorldRepository,
) : PluginScript() {
    private val captains = WeakHashMap<Npc, CaptainState>()
    private val drills = WeakHashMap<Npc, Int>()

    override fun ScriptContext.startup() {
        for (type in Trainees) onAiTimer(type) { trainAtDummy(npc) }
        onAiTimer(Captain) { paceAndInstruct(npc) }
        onAiTimer(DrillSergeant) { drill(npc) }
        onAiTimer(TrainingSergeant) { instructAtDummies(npc) }
        onAiTimer(TrainingSoldier) { trainAtSwordDummy(npc) }
    }

    internal fun trainAtDummy(npc: Npc) {
        npc.aiTimer = random.of(9, 12)
        if (!npc.available() || npc.coords.level != 2 || npc.coords.z != 3490 || npc.coords.x !in 3209..3217) return
        val dummyTile = npc.coords.translate(0, -1)
        val dummy = locs.findAll(dummyTile).firstOrNull { it.id == Dummy.asRSCM(RSCMType.LOC) } ?: return
        npc.faceSquare(dummyTile)
        npc.anim("seq.human_sword_stab")
        world.locAnim(dummy, "seq.varrock_heavybag_defend")
        world.soundArea(npc.coords, "synth.STABSWORD_STAB", radius = 4)
    }

    internal fun paceAndInstruct(npc: Npc) {
        if (!npc.available() || npc.spawnCoords.level != 2 || npc.spawnCoords.x !in 3208..3218 || npc.spawnCoords.z !in 3487..3496) return
        val state = captains.getOrPut(npc) { CaptainState() }
        if (--state.untilShout <= 0) {
            npc.say(Instructions[random.of(0, Instructions.lastIndex)])
            state.untilShout = 20
        }
        if (npc.routeDestination.isNotEmpty()) return
        val destination = Patrol[state.waypoint]
        if (npc.coords == destination) state.waypoint = (state.waypoint + 1) % Patrol.size
        npc.walk(Patrol[state.waypoint])
    }

    internal fun drill(sergeant: Npc) {
        if (!sergeant.available() || sergeant.spawnCoords != DrillPosition) return
        val step = drills[sergeant] ?: 0
        val instruction = Drill[step]
        sergeant.say(instruction.first)
        sergeant.faceSquare(CoordGrid(2893, 3539, 0))
        val soldierId = DrillingSoldier.asRSCM(RSCMType.NPC)
        for (soldier in npcs.findAll(ZoneKey.from(DrillPosition), 1)) {
            if (soldier.id != soldierId || soldier.spawnCoords !in Formation || !soldier.available()) continue
            soldier.faceSquare(soldier.coords.translate(instruction.second.first, instruction.second.second))
            soldier.anim("seq.human_turnonspot")
        }
        drills[sergeant] = (step + 1) % Drill.size
    }

    internal fun trainAtSwordDummy(npc: Npc) {
        if (!npc.available() || npc.spawnCoords !in TrainingPositions) return
        val dummyTile = npc.coords.translate(1, 0)
        if (locs.findAll(dummyTile).none { it.id == SwordDummy.asRSCM(RSCMType.LOC) }) return
        npc.faceSquare(dummyTile)
        npc.anim("seq.human_sword_slash")
    }

    internal fun instructAtDummies(sergeant: Npc) {
        sergeant.aiTimer = random.of(35, 70)
        if (!sergeant.available() || sergeant.spawnCoords != TrainingSergeantPosition ||
            sergeant.coords != TrainingSergeantPosition) return
        val soldierId = TrainingSoldier.asRSCM(RSCMType.NPC)
        val soldiers = TrainingPositions.flatMap { npcs.findAll(it).toList() }
            .filter { it.id == soldierId && it.spawnCoords in TrainingPositions && it.available() }
        if (soldiers.isEmpty()) return
        val soldier = soldiers[random.of(0, soldiers.lastIndex)]
        sergeant.faceSquare(soldier.coords)
        sergeant.say(TrainingInstructions[random.of(0, TrainingInstructions.lastIndex)])
        soldier.say("Yes sir!")
    }

    @OptIn(InternalApi::class)
    private fun Npc.available(): Boolean = isVisible && !isBusy && activeCoroutine == null && !hasInteraction()

    private class CaptainState(var waypoint: Int = 0, var untilShout: Int = 1)

    internal companion object {
        const val Captain = "npc.fai_varrock_guard_trainer02"
        const val Dummy = "loc.fai_varrock_sword_dummy"
        const val DrillSergeant = "npc.death_ig_sergeant_drilling"
        const val DrillingSoldier = "npc.death_ig_solider_drilling"
        const val TrainingSergeant = "npc.death_ig_sergeant_training"
        const val TrainingSoldier = "npc.death_ig_solider_training"
        const val SwordDummy = "loc.sworddummy"
        val Trainees = listOf(
            "npc.fai_varrock_guard_training02", "npc.fai_varrock_guard_training02_variant01",
            "npc.fai_varrock_guard_training02_f", "npc.fai_varrock_guard_training02_f_variant01",
        )
        val Patrol = listOf(
            CoordGrid(3218, 3493, 2), CoordGrid(3208, 3493, 2),
            CoordGrid(3208, 3488, 2), CoordGrid(3218, 3488, 2),
        )
        val Instructions = listOf(
            "Bah, why do I bother with you lot?",
            "Hit the dummy before I come over there and hit you!",
            "Just hit the thing already!",
            "Make those dummies suffer!",
        )
        val DrillPosition = CoordGrid(2893, 3541, 0)
        val Formation = (2891..2895).flatMap { x -> listOf(CoordGrid(x, 3537, 0), CoordGrid(x, 3539, 0)) }.toSet()
        val TrainingPositions = setOf(CoordGrid(2900, 3531, 0), CoordGrid(2900, 3533, 0))
        val TrainingSergeantPosition = CoordGrid(2900, 3532, 0)
        val TrainingInstructions = listOf(
            "Put your back into it soldier!",
            "That dummy is the enemy. Kill it!",
            "Good work soldier!",
            "Keep it up soldier!",
            "Work it!",
        )
        val Drill = listOf(
            "Squad... ATTENTION!" to (0 to 1),
            "Left turn!" to (-1 to 0),
            "Right turn!" to (0 to 1),
            "About turn!" to (0 to -1),
        )
    }
}
