package org.rsmod.content.minigames.pyramidplunder.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseThievingLvl
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.utils.skills.SkillingSuccessRate
import org.rsmod.content.minigames.pyramidplunder.PlunderRun.sceptreEject
import org.rsmod.content.minigames.pyramidplunder.PlunderState.plunderChestState
import org.rsmod.content.minigames.pyramidplunder.PlunderState.plunderRoom
import org.rsmod.content.minigames.pyramidplunder.configs.PlunderLocs
import org.rsmod.content.minigames.pyramidplunder.configs.PlunderRooms
import org.rsmod.content.minigames.pyramidplunder.drops.PlunderDropTables
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class PlunderChestScript
@Inject
constructor(
    private val objRepo: ObjRepository,
    private val npcRepo: NpcRepository,
    private val random: GameRandom,
    private val eventBus: EventBus,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(PlunderLocs.CHEST_CLOSED) { chest -> searchChest(chest.loc.coords) }
        onPlayerQueueWithArgs<ChestTask>(QUEUE_CHEST_OPEN) { finish(it.args) }
    }

    private data class ChestTask(
        val room: Int,
        val avoidedTrap: Boolean,
        val chestCoords: CoordGrid,
    )

    private suspend fun ProtectedAccess.searchChest(chestCoords: CoordGrid) {
        arriveDelay()
        val room = player.plunderRoom
        if (room == 0) {
            return
        }
        val info = PlunderRooms.forRoom(room)
        if (player.baseThievingLvl < info.levelReq) {
            mes("You need a Thieving level of ${info.levelReq} to search this chest.")
            return
        }
        if (player.plunderChestState != 0) {
            mes("You have already looted this chest.")
            return
        }

        val rate =
            SkillingSuccessRate.successRate(
                PlunderRooms.CHEST_LOW,
                PlunderRooms.CHEST_HIGH,
                player.baseThievingLvl,
                MAX_LEVEL,
                flatBonus = -(room - 1.0),
            )
        val avoidedTrap = rate > random.randomDouble()

        // TODO: verify the chest-search animation.
        anim(PlunderLocs.SEQ_SEARCH)

        queue(QUEUE_CHEST_OPEN, cycles = 1, args = ChestTask(room, avoidedTrap, chestCoords))
    }

    private fun ProtectedAccess.finish(task: ChestTask) {
        if (player.plunderRoom != task.room) {
            return
        }
        player.plunderChestState = 1

        val info = PlunderRooms.forRoom(task.room)
        invAddOrDrop(objRepo, PlunderDropTables.chestArtefact(task.room, random))

        if (task.avoidedTrap) {
            statAdvance("stat.thieving", info.chestXp)
            soundSynth(PlunderLocs.SYNTH_CHEST_OPEN)
            mes("You open the chest and find an artefact inside!")
        } else {
            mes("A scarab swarm bursts out of the chest!")
            soundSynth(PlunderLocs.SYNTH_SCARABS)
            queueHit(delay = 1, type = HitType.Typeless, damage = random.of(1, 4))
            spawnScarabSwarm(task.chestCoords)
        }

        if (PlunderDropTables.rollSceptre(info.sceptreDenominator, random)) {
            invAddOrDrop(objRepo, PlunderDropTables.SCEPTRE)
            sceptreEject(eventBus)
        }
    }

    // TODO: give scarabs owner-scoped aggression and remove them when that player leaves.
    private fun spawnScarabSwarm(coords: CoordGrid) {
        val type =
            ServerCacheManager.getNpc(PlunderLocs.SCARAB_SWARM.asRSCM(RSCMType.NPC)) ?: return
        val npc = Npc(type, coords)
        npcRepo.add(npc, SCARAB_LIFETIME_CYCLES)
    }

    private companion object {
        const val MAX_LEVEL = 99
        const val QUEUE_CHEST_OPEN = "queue.ntk_chest_open"
        const val SCARAB_LIFETIME_CYCLES = 20
    }
}
