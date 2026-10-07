package org.rsmod.content.bosses.giants

import dtx.core.ArgMap
import dtx.core.RollResult
import dtx.core.flatten
import jakarta.inject.Inject
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.droptable.DropRollItem
import org.rsmod.api.droptable.DropTableRegistry
import org.rsmod.api.droptable.rollCount
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.events.InstanceEndedEvent
import org.rsmod.api.instances.events.instanceEventId
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.*
import org.rsmod.content.interfaces.collectionlog.CollectionLog
import org.rsmod.game.entity.Npc
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class GiantAccess @Inject internal constructor(
    private val rooms: GiantRooms,
    private val deps: BossDeps,
    private val objRepo: ObjRepository,
    private val locRepo: LocRepository,
    private val drops: DropTableRegistry,
    private val npcHitModifier: NpcHitModifier,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerLogin { player.migrateGiantUnlocks() }
        for (gate in listOf("loc.hillgiant_boss_entrance_l", "loc.hillgiant_boss_entrance_r")) {
            onOpLoc1(gate) { enter(GiantBoss.OBOR) }
        }
        onOpLoc1("loc.gb_moss_door_in") { enter(GiantBoss.BRYOPHYTA) }
        onOpLoc2("loc.gb_moss_door_in") { checkCount(GiantBoss.BRYOPHYTA) }
        for (exit in listOf("loc.hillgiant_boss_exit_l", "loc.hillgiant_boss_exit_r", "loc.gb_moss_caverock_exit")) {
            onOpLoc1(exit) { leave() }
            onOpLoc3(exit) { leave() }
        }
        onOpLoc1("loc.hillgiant_boss_climbingrocks") { climb() }
        onOpLoc1("loc.gb_log_withaxe") {
            if (rooms.forPlayer(player)?.kind == GiantBoss.BRYOPHYTA) {
                invAddOrDrop(objRepo, "obj.bronze_axe", 1)
                mes("You take a bronze axe from the log.")
            }
        }
        for (kind in GiantBoss.entries) {
            onOpLoc1(kind.chest) { openChest(kind, it.loc) }
            onOpLoc1(kind.openChest) { mes("The chest is empty. Defeat ${kind.label} again first.") }
            onEvent<InstanceEndedEvent>(instanceEventId(kind.instanceKey)) { rooms.remove(instanceId) }
        }
        for (tool in GiantTools.all) {
            onOpNpcU("npc.gb_growthling", tool) { finishGrowthling(it.npc, tool) }
        }
        onEvent<GameLifecycle.LateCycle> { rooms.tick() }
    }

    private suspend fun ProtectedAccess.enter(kind: GiantBoss) {
        arriveDelay()
        if (!GiantRules.mayEnter(kind.unlocked(player), invTotal(inv, kind.key))) {
            mes("You need ${if (kind == GiantBoss.OBOR) "a giant" else "a mossy"} key to unlock this entrance.")
            return
        }
        when (val result = rooms.create(player, kind)) {
            is InstanceManager.Result.Created -> {
                // No suspension between allocation, arrival and ownership finalization.
                telejump(result.enter)
                if (player.coords != result.enter) {
                    rooms.manager.cancelPendingEntry(player, mapClock)
                    return
                }
                rooms.manager.finalizeEntry(player, result.session, mapClock)
                kind.unlock(player)
                val room = checkNotNull(rooms.forPlayer(player))
                if (kind == GiantBoss.BRYOPHYTA) rooms.start(room)
                else mes("You enter Obor's lair. Climb down the rocks when you are ready.")
            }
            is InstanceManager.Result.Failed -> mes(result.reason)
            is InstanceManager.Result.Joined -> Unit
        }
    }

    private fun ProtectedAccess.leave() {
        val room = rooms.forPlayer(player) ?: return
        val session = rooms.manager.sessionForId(room.id) ?: return
        val exit = session.placement.exitCoord
        telejump(exit)
        if (player.coords == exit) rooms.manager.leave(player, session, mapClock)
    }

    private suspend fun ProtectedAccess.climb() {
        val room = rooms.forPlayer(player) ?: return
        if (room.kind != GiantBoss.OBOR) return
        val down = room.source(player.coords).z >= 9807
        if (!down && !room.life.dead) {
            mes("You cannot climb out while Obor is still alive!")
            return
        }
        arriveDelay()
        anim(if (down) "seq.human_climbing_down" else "seq.human_climbing")
        telejump(room.tile(CoordGrid(3092, if (down) 9804 else 9807)))
        room.enteredPit = down
        if (down) rooms.start(room)
        delay(1)
        resetAnim()
    }

    private fun ProtectedAccess.openChest(kind: GiantBoss, chest: BoundLocInfo) {
        val room = rooms.forPlayer(player) ?: return
        if (room.kind != kind) return
        if (!room.life.dead) { mes("You must defeat ${kind.label} before opening the chest."); return }
        if (room.life.chestAttempted) { mes("You have already searched the chest after this fight."); return }
        val table = drops.forLoc(kind.chest) ?: error("Missing ${kind.label} chest drop table")
        if (!room.life.attemptChest(mapClock)) return
        if (invTotal(inv, kind.key) == 0) {
            mes("You need ${if (kind == GiantBoss.OBOR) "a giant" else "a mossy"} key to open the chest.")
            return // Even an unsuccessful attempt starts the native fifteen-tick respawn.
        }
        invDel(inv, kind.key)
        anim("seq.human_openchest")
        locRepo.change(chest, kind.openChest, 15)
        VarPlayerIntMapSetter.set(player, kind.kc, player.vars[kind.kc] + 1)
        when (val result = table.roll(player, ArgMap()).flatten()) {
            is RollResult.Nothing -> Unit
            is RollResult.Single -> giveDrop(result.result)
            is RollResult.ListOf -> result.results.forEach { giveDrop(it) }
        }
        mes("You unlock the chest and collect ${kind.label}'s treasure.")
        checkCount(kind)
    }

    private fun ProtectedAccess.checkCount(kind: GiantBoss) {
        mes("Your ${kind.label} kill count is: <col=ff0000>${player.vars[kind.kc]}</col>.")
    }

    private fun ProtectedAccess.giveDrop(drop: DropRollItem) {
        if (drop.isNothing || !drop.condition(player)) return
        val obj = drop.transformObj(player) ?: drop.obj
        val count = drop.rollCount(random)
        CollectionLog.grant(player, obj, count)
        invAddOrDrop(objRepo, obj, count)
        drop.bonusDrops.forEach { giveDrop(it) }
    }

    private fun ProtectedAccess.finishGrowthling(npc: Npc, tool: String) {
        val room = rooms.forPlayer(player) ?: return
        if (npc !in room.growthlings || npc.hitpoints <= 0 || npc in room.finishing) return
        if (invTotal(inv, tool) == 0 || GiantRules.distance(npc.coords, 1, player.coords) > 1) return
        if (npc.hitpoints > 1) { mes("Weaken the growthling first, or equip a woodcutting axe to cut it down."); return }
        room.finishing += npc
        anim("seq.human_axe_chop")
        npc.queueHit(player, 1, HitType.Melee, npc.hitpoints, npcHitModifier)
    }
}

internal object GiantTools {
    val all = listOf("bronze_axe", "iron_axe", "steel_axe", "black_axe", "mithril_axe", "adamant_axe",
        "rune_axe", "dragon_axe", "infernal_axe", "3a_axe", "trail_gilded_axe", "crystal_axe",
        "trailblazer_axe", "league_trailblazer_axe", "trailblazer_reloaded_axe", "fairy_enchanted_secateurs",
        "infernal_axe_empty", "crystal_axe_inactive", "bronze_axe_2h", "iron_axe_2h", "steel_axe_2h",
        "black_axe_2h", "mithril_axe_2h", "adamant_axe_2h", "rune_axe_2h", "dragon_axe_2h",
        "crystal_axe_2h", "crystal_axe_2h_inactive", "3a_axe_2h", "secateurs").map { "obj.$it" }
    val equipped = all.filterNot { it == "obj.secateurs" }
}
