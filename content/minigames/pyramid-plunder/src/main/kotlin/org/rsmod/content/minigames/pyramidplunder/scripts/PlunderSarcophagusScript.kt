package org.rsmod.content.minigames.pyramidplunder.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseStrengthLvl
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.utils.skills.SkillingSuccessRate
import org.rsmod.content.minigames.pyramidplunder.PlunderRun.sceptreEject
import org.rsmod.content.minigames.pyramidplunder.PlunderState
import org.rsmod.content.minigames.pyramidplunder.PlunderState.plunderRoom
import org.rsmod.content.minigames.pyramidplunder.PlunderState.plunderSarcophagusPush
import org.rsmod.content.minigames.pyramidplunder.PlunderState.plunderSarcophagusState
import org.rsmod.content.minigames.pyramidplunder.configs.PlunderLocs
import org.rsmod.content.minigames.pyramidplunder.configs.PlunderRooms
import org.rsmod.content.minigames.pyramidplunder.drops.PlunderDropTables
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class PlunderSarcophagusScript
@Inject
constructor(
    private val objRepo: ObjRepository,
    private val npcRepo: NpcRepository,
    private val worldRepo: WorldRepository,
    private val random: GameRandom,
    private val eventBus: EventBus,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(PlunderLocs.SARCOPHAGUS_CLOSED) { sarc -> openSarcophagus(sarc.loc) }
        onOpLoc1(PlunderLocs.SARCOPHAGUS_OPEN) { searchOpened() }

        onPlayerQueueWithArgs<SarcTask>(QUEUE_SARCOPHAGUS_OPEN) { runStage(it.args) }
    }

    private enum class SarcStage {
        RESULT,
        SETTLE,
    }

    private data class SarcTask(
        val stage: SarcStage,
        val room: Int,
        val success: Boolean,
        val loc: BoundLocInfo,
    )

    private suspend fun ProtectedAccess.openSarcophagus(loc: BoundLocInfo) {
        arriveDelay()
        val room = player.plunderRoom
        if (room == 0) {
            return
        }
        if (player.plunderSarcophagusState != PlunderState.SARC_CLOSED) {
            searchOpened()
            return
        }
        if (player.plunderSarcophagusPush) {
            return
        }

        val info = PlunderRooms.forRoom(room)
        val rate =
            SkillingSuccessRate.successRate(
                info.sarcophagusOpenLow,
                info.sarcophagusOpenHigh,
                player.baseStrengthLvl,
                MAX_LEVEL,
            )
        val success = rate > random.randomDouble()

        player.plunderSarcophagusPush = true
        anim(PlunderLocs.SEQ_SARCOPHAGUS_PUSH)
        worldRepo.locAnim(loc, PlunderLocs.SEQ_SARC_READY)

        queue(QUEUE_SARCOPHAGUS_OPEN, cycles = 4, args = SarcTask(SarcStage.RESULT, room, success, loc))
        if (success) {
            queue(
                QUEUE_SARCOPHAGUS_OPEN,
                cycles = 9,
                args = SarcTask(SarcStage.SETTLE, room, success, loc),
            )
        }
    }

    private fun ProtectedAccess.runStage(task: SarcTask) {
        if (player.plunderRoom != task.room) {
            return
        }
        when (task.stage) {
            SarcStage.RESULT -> {
                player.plunderSarcophagusPush = false
                worldRepo.locAnim(task.loc, PlunderLocs.SEQ_SARC_ANIM)
                if (!task.success) {
                    mes("You fail to force the sarcophagus lid open.")
                    resetAnim()
                    return
                }
                player.plunderSarcophagusState = PlunderState.SARC_OPENING
                anim(PlunderLocs.SEQ_SARCOPHAGUS_OPEN)
                val info = PlunderRooms.forRoom(task.room)
                statAdvance("stat.strength", info.sarcophagusXp)
                worldRepo.soundArea(task.loc.coords, PlunderLocs.SYNTH_SARCOPHAGUS_OPEN, radius = 5)
                invAddOrDrop(objRepo, PlunderDropTables.sarcophagusArtefact(task.room, random))
                if (PlunderDropTables.rollSceptre(info.sceptreDenominator, random)) {
                    invAddOrDrop(objRepo, PlunderDropTables.SCEPTRE)
                    sceptreEject(eventBus)
                }
            }
            SarcStage.SETTLE -> {
                if (player.plunderSarcophagusState != PlunderState.SARC_OPENING) {
                    return
                }
                player.plunderSarcophagusState = PlunderState.SARC_OPEN
                val info = PlunderRooms.forRoom(task.room)
                if (random.of(NINE) < info.mummySpawnChance) {
                    spawnMummy(task.loc)
                }
            }
        }
    }

    private fun ProtectedAccess.searchOpened() {
        mes("You've already looted the sarcophagus.")
    }

    // TODO: give mummies owner-scoped aggression and remove them when that player leaves.
    private fun spawnMummy(loc: BoundLocInfo) {
        val mummyName = PlunderLocs.MUMMIES[random.of(PlunderLocs.MUMMIES.size)]
        val type = ServerCacheManager.getNpc(mummyName.asRSCM(RSCMType.NPC)) ?: return
        val npc = Npc(type, loc.coords)
        npcRepo.add(npc, MUMMY_LIFETIME_CYCLES)
    }

    private companion object {
        const val MAX_LEVEL = 99
        const val NINE = 9
        const val QUEUE_SARCOPHAGUS_OPEN = "queue.ntk_sarcophagus_open"

        const val MUMMY_LIFETIME_CYCLES = 50
    }
}
