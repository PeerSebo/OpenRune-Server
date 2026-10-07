package org.rsmod.content.raids.theatreofblood

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.config.refs.params
import org.rsmod.api.instances.events.InstanceEndedEvent
import org.rsmod.api.instances.events.InstancePlayerLeaveUnboundEvent
import org.rsmod.api.instances.events.instanceEventId
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.player.events.PlayerHitEvents
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.forcedWalk
import org.rsmod.api.script.*
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class TheatreScript @Inject internal constructor(
    private val manager: TheatreManager,
    private val inventory: TheatreInventory,
    private val encounters: TheatreEncounters,
    private val nylocas: TheatreNylocas,
) : PluginScript() {
    override fun ScriptContext.startup() {
        TheatreData.roomsList
        TheatreData.rules
        TheatreData.nylocasWaves
        TheatreData.rewardsList
        TheatreData.suppliesList
        onIfOverlayButton("component.tob_hud:spectator_exit") { entrance() }
        onOpLoc1(TheatreRoomObjects.EXIT) { entrance(crystal = true) }
        onOpLoc1("loc.tob_surface_notice_board") { partyMenu() }
        onOpLoc1("loc.tob_surface_raid_entrance") { entrance() }
        onOpNpc1("npc.tob_door_guard") { entrance() }
        onOpNpc1("npc.tob_male_orator") { explain() }
        onOpNpc1("npc.tob_female_orator") { explain() }
        onOpNpc1("npc.tob_stranger") { explain() }
        onOpNpc1(TheatreRoomObjects.ORATOR) { orator() }
        onOpLoc1("loc.tob_arena_barrier") { passBarrier(it.loc) }
        onOpLoc1("loc.tob_walkway_verzik_barrier_op") { entrance(crystal = true) }
        for (symbol in listOf(
            "loc.tob_dungeon_walkway_exit_clickbox",
            "loc.tob_dungeon_xarpus_arena_door_exit",
        )) {
            onOpLoc1(symbol) { manager.advanceRoom(player) }
        }
        onOpLoc2("loc.tob_dungeon_walkway_exit_clickbox") { manager.advanceRoom(player) }
        onOpLoc2("loc.tob_dungeon_xarpus_arena_door_exit") { manager.advanceRoom(player) }
        for (symbol in listOf("loc.tob_midway_chest_closed", "loc.tob_midway_chest_open")) {
            onOpLoc1(symbol) { supplies() }
        }
        onOpLoc1("loc.tob_surface_gravestone_chest") { recovery() }
        for (symbol in listOf(
            "loc.tob_treasureroom_chest_mine_standard", "loc.tob_treasureroom_chest_mine_rare",
            "loc.tob_treasureroom_chest_open", "loc.tob_rewards_chest_lobby_closed",
            "loc.tob_rewards_chest_lobby_open", "loc.tob_rewards_chest_lobby_multi",
        )) onOpLoc1(symbol) { manager.claimRewards(player) }

        onPlayerLogin { manager.login(player) }
        onPlayerLogout { manager.parties.remove(player) }
        onEvent<InstancePlayerLeaveUnboundEvent> {
            if (key == TheatreManager.KEY) manager.departed(player, instanceId)
        }
        onEvent<InstanceEndedEvent>(instanceEventId(TheatreManager.KEY)) { manager.ended(instanceId) }
        onEvent<PlayerHitEvents.Modify> {
            if (manager.run(player) != null && player.vars[TheatreManager.DEAD] != 0) hit.damage = 0
        }
        onEvent<NpcHitEvents.AnyModify> {
            val run = manager.run(npc) ?: return@onEvent
            if (run.room == 2) nylocas.atLaunch(run, npc, hit)
        }
        for (symbol in TheatreData.rules.ownedNpcs + "npc.nylocas_boss_spawning") {
            val npcType = checkNotNull(ServerCacheManager.getNpc(symbol.asRSCM(RSCMType.NPC)))
            onEvent<NpcHitEvents.BeforeImpact>(npcType.id) {
                val run = manager.run(npc) ?: return@onEvent
                damage = if (run.room == 2) nylocas.beforeImpact(run, npc, hit, damage)
                    else encounters.beforeImpact(run, npc, hit, damage)
            }
            onNpcQueue(npcType, "queue.death") {
                val run = manager.run(npc) ?: return@onNpcQueue
                if (run.room == 2 && npc !== run.boss) {
                    nylocas.dying(run, npc)
                    return@onNpcQueue
                }
                if (npc === run.boss) {
                    npc.hideAllOps()
                    npc.resetMovement()
                    npc.movementLocked = true
                    if (run.room == 0) {
                        for ((model, sequence) in listOf(
                            "npc.tob_maiden_dying_a" to "seq.maiden_death_a",
                            "npc.tob_maiden_dying_b" to "seq.maiden_death_b",
                        )) {
                            npc.transmog(checkNotNull(ServerCacheManager.getNpc(model.asRSCM(RSCMType.NPC))), Int.MAX_VALUE)
                            anim(sequence)
                            delay(checkNotNull(ServerCacheManager.getAnim(sequence.asRSCM(RSCMType.SEQ))))
                            if (manager.run(npc) !== run || !manager.active(run)) return@onNpcQueue
                        }
                    } else {
                        val deathAnimation = param(params.death_anim)
                        anim(RSCM.getReverseMapping(RSCMType.SEQ, deathAnimation.id))
                        delay(deathAnimation)
                    }
                    if (manager.run(npc) !== run || !manager.active(run)) return@onNpcQueue
                }
                if (run.room == 2) {
                    nylocas.killed(run, npc)
                    manager.remove(run, npc)
                } else encounters.killed(run, npc)
            }
        }
        onEvent<NpcHitEvents.AnyImpact> {
            val run = manager.run(npc) ?: return@onEvent
            encounters.impact(run, npc, hit)
            if (hit.isFromPlayer && hit.damage > 0) {
                val source = hit.resolvePlayerSource(manager.deps.playerList) ?: return@onEvent
                if (source in manager.combatants(run)) {
                    val total = source.vars[TheatreManager.DAMAGE].toLong() + hit.damage
                    TheatreManager.set(source, TheatreManager.DAMAGE, total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                    val roomTotal = source.vars["varp.theatre_room_damage"].toLong() + hit.damage
                    TheatreManager.set(source, "varp.theatre_room_damage", roomTotal.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                }
            }
        }
        onEvent<NpcStateEvents.Delete> {
            val run = manager.run(npc) ?: return@onEvent
            manager.remove(run, npc)
        }
    }

    private suspend fun ProtectedAccess.passBarrier(loc: BoundLocInfo) {
        val run = manager.run(player) ?: return
        val barrier = manager.source(run, loc.coords)
        if (barrier !in run.roomrow.barriers) return
        if (player.vars[TheatreManager.DEAD] != 0) {
            mes("You will rejoin your party when this room is cleared.")
            return
        }
        val room = run.room
        val inside = run.roomrow.contains(manager.source(run, coords))
        val title = if (inside) "Leave the arena?" else "Are you ready to enter the arena?"
        if (choose(title, "Yes", "No") != 1) return
        if (manager.run(player) !== run || run.room != room || player.vars[TheatreManager.DEAD] != 0) return
        val crossing = TheatreBarriers.crossing(manager.source(run, coords), barrier, loc.angleId)
        combatClearQueue()
        player.queueList.removeAll("queue.hit")
        player.queueList.removeAll("queue.impact_hit")
        if (coords.level != barrier.level) {
            telejump(manager.coord(run, manager.source(run, coords).copy(level = barrier.level)), TeleportType.Exempt)
        }
        val approach = manager.coord(run, crossing.approach)
        forcedWalk(approach, coords.chebyshevDistance(approach))
        val destination = manager.coord(run, crossing.destination)
        forcedWalk(destination, 2)
        if (barrier.level != run.roomrow.entry.level) {
            if (!inside) {
                telejump(manager.coord(run, crossing.destination.copy(level = run.roomrow.entry.level)), TeleportType.Exempt)
            }
        }
        if (!inside && run.stage in listOf(TheatreManager.LOBBY, TheatreManager.WAITING)) {
            manager.startEncounter(player)
        }
    }

    private suspend fun ProtectedAccess.orator() {
        val run = manager.run(player) ?: return
        if (run.room == 5 && run.stage == TheatreManager.WAITING) {
            when (choose("Verzik Vitur awaits", "Begin the encounter", "Leave the Theatre", "Stay here")) {
                1 -> manager.startEncounter(player)
                2 -> leaveByCrystal()
            }
        } else {
            entrance(crystal = true)
        }
    }

    private suspend fun ProtectedAccess.entrance(crystal: Boolean = false) {
        val run = manager.run(player)
        if (run != null) {
            when (choose("Leave the Theatre?", "Stay here", "Leave the Theatre")) {
                2 -> if (crystal) leaveByCrystal() else manager.leave(player)
            }
            return
        }
        manager.enter(this)
    }

    private suspend fun ProtectedAccess.partyMenu() {
        val party = manager.parties.get(player)
        if (party != null) {
            when (choose("Theatre party", "View party", "Leave party", "Cancel")) {
                1 -> mesbox(party.members.joinToString("\n") { it.displayName })
                2 -> {
                    manager.parties.remove(player)
                    mes("You have left your Theatre party.")
                }
            }
            return
        }
        when (choose(
            "Theatre of Blood", "Create a Normal party", "Join a party", "Cancel",
        )) {
            1 -> manager.parties.create(player)
            2 -> manager.parties.join(player, stringDialog("Enter your party leader's name:"))
        }
    }

    private suspend fun ProtectedAccess.leaveByCrystal() {
        try {
            fadeOverlay(0, 255, 0, 0, 20)
            soundSynth("synth.TELEPORT_ALL")
            delay(1)
            manager.leave(player)
            fadeOverlay(0, 0, 0, 255, 20)
            delay(1)
        } finally {
            closeFadeOverlayNow()
            manager.restoreHud(player)
        }
    }

    private suspend fun ProtectedAccess.explain() {
        mesbox("The Theatre of Blood holds six encounters for parties of up to five adventurers.\nA failed party must recover its lost items for 100,000 coins.")
        entrance()
    }

    private suspend fun ProtectedAccess.recovery() {
        if (inventory.recovery(player).isEmpty()) {
            mes("You have no items to recover.")
            return
        }
        if (choose("Recover your lost items?", "Yes", "No") == 1) inventory.reclaim(player)
    }

    private suspend fun ProtectedAccess.supplies() {
        val run = manager.run(player) ?: return
        if (run.stage != TheatreManager.CLEARED || !TheatreCombatPatterns.supplyChestAfter(run.room)) {
            mes("You cannot buy supplies during this encounter.")
            return
        }
        val rows = TheatreData.suppliesList
        val options = rows.map { supply ->
            val item = checkNotNull(ServerCacheManager.getItem(supply.item.asRSCM(RSCMType.OBJ)))
            "${item.name} (${supply.cost} points)"
        }
        val chosen = choose("Supplies: ${player.vars[TheatreManager.SUPPLIES]} points", *options.take(4).toTypedArray(), "More")
        if (chosen in 1..4) inventory.buySupply(player, chosen - 1)
        else if (chosen == 5) {
            val next = choose("Food supplies", *options.drop(4).toTypedArray(), "Cancel")
            if (next in 1..4) inventory.buySupply(player, next + 3)
        }
    }

    private suspend fun ProtectedAccess.choose(title: String, vararg options: String): Int = when (options.size) {
        2 -> choice2(options[0], 1, options[1], 2, title = title)
        3 -> choice3(options[0], 1, options[1], 2, options[2], 3, title = title)
        5 -> choice5(options[0], 1, options[1], 2, options[2], 3, options[3], 4, options[4], 5, title = title)
        else -> error("Unsupported Theatre menu size")
    }
}
