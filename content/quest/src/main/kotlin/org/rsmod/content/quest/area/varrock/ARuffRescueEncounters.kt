package org.rsmod.content.quest.area.varrock

import jakarta.inject.Inject
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.content.quest.manager.Quest
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class ARuffRescueEncounters @Inject constructor(
    private val npcRepo: NpcRepository,
    private val mapClock: MapClock,
) : PluginScript() {
    private val active = mutableMapOf<Player, Encounter>()

    override fun ScriptContext.startup() {
        onPlayerCoordsChanged { update(player) }
        onPlayerLogout { clear(player) }
    }

    private fun update(player: Player) {
        val stage = Quest.get("quest_ruffsituation")?.getQuestStage(player) ?: return
        if (player.coords.level != 0) {
            clear(player)
            return
        }
        val firstRescue = stage == ARuffSituation.FOUND_CLUES &&
            player.coords.x in 3145..3155 && player.coords.z in 3452..3460
        val lastRescue = stage in ARuffSituation.SECOND_PUPPY until ARuffSituation.THIRD_PUPPY &&
            player.coords.x in 3133..3142 && player.coords.z in 3462..3473
        val encounter = when {
            firstRescue -> EncounterType.FIRST
            lastRescue -> EncounterType.LAST
            else -> null
        }
        if (encounter == null) {
            clear(player)
            return
        }
        if (active[player]?.type == encounter) {
            return
        }
        clear(player)
        if (firstRescue) {
            active[player] = Encounter(
                EncounterType.FIRST,
                listOf(spawn(player, "npc.dogq_goblin_picklenose", CoordGrid(3149, 3456, 0))),
            )
            return
        }
        val outlaws = buildList {
            if (stage == ARuffSituation.SECOND_PUPPY) {
                add(spawn(player, "npc.dogq_outlaw1", CoordGrid(3137, 3467, 0)))
            }
            if (stage < ARuffSituation.OUTLAWS_DEFEATED) {
                add(spawn(player, "npc.dogq_outlaw2", CoordGrid(3138, 3467, 0)))
            }
        }
        active[player] = Encounter(
            EncounterType.LAST,
            outlaws + spawn(player, "npc.dogq_puppy_three", CoordGrid(3139, 3467, 0)),
        )
    }

    private fun spawn(player: Player, type: String, coords: CoordGrid): Npc {
        val npc = Npc(type, coords)
        npcRepo.add(npc, Int.MAX_VALUE)
        npc.respawns = false
        npc.assignSpawnOwner(player, mapClock.cycle)
        if (type == "npc.dogq_outlaw1" || type == "npc.dogq_outlaw2") {
            npc.setHunt(0)
        }
        return npc
    }

    private fun clear(player: Player) {
        active.remove(player)?.npcs?.forEach { npc ->
            if (npc.isSlotAssigned) {
                npcRepo.del(npc, Int.MAX_VALUE)
            }
        }
    }

    private data class Encounter(val type: EncounterType, val npcs: List<Npc>)

    private enum class EncounterType { FIRST, LAST }
}
