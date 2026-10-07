package org.rsmod.content.skills.hunter.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.map.MapSingletons.collision
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hunterLvl
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.hunter.ActiveTrap
import org.rsmod.content.skills.hunter.HunterCatchEvent
import org.rsmod.content.skills.hunter.HunterTrapManager
import org.rsmod.content.skills.hunter.TrapState
import org.rsmod.content.skills.hunter.configs.HunterMessages
import org.rsmod.content.skills.hunter.configs.HunterParams
import org.rsmod.content.skills.hunter.configs.HunterPrey
import org.rsmod.content.skills.hunter.configs.HunterPreyTable
import org.rsmod.content.skills.hunter.configs.NetTrapColour
import org.rsmod.content.skills.hunter.configs.TrapFamily
import org.rsmod.content.skills.hunter.configs.TrapLocs
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.Direction
import org.rsmod.game.map.collision.firstStepDestination
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal enum class TrapAction {
    CHECK,
    DISMANTLE,
    RESET,
}

internal data class HunterLayTask(val trap: ActiveTrap)

internal data class HunterTrapTask(val trap: ActiveTrap, val generation: Int, val action: TrapAction)

class HunterTrapScript
@Inject
constructor(
    private val manager: HunterTrapManager,
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
    private val xpMods: XpModifiers,
    private val random: GameRandom,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.LateCycle> { manager.tick() }
        onPlayerLogout { manager.onPlayerLogout(player) }

        registerBirdSnare()
        registerBoxTrap()
        registerNetTrap()
        registerDeadfall()

        onPlayerQueueWithArgs<HunterLayTask>(HunterParams.LAY_QUEUE) { completeLay(it.args) }
        onPlayerQueueWithArgs<HunterTrapTask>(HunterParams.CHECK_QUEUE) { completeAction(it.args) }
    }

    private fun ScriptContext.registerBirdSnare() {
        onOpHeld1(HunterParams.BIRD_SNARE_OBJ) {
            layGroundTrap(
                family = TrapFamily.BIRD_SNARE,
                actionText = HunterMessages.SET_BIRD_SNARE,
                laySynth = HunterParams.SNARE_LAY_SYNTH,
            )
        }
        onOpLoc1(TrapLocs.SNARE_SET) { dismantle(it.loc) }
        onOpLoc2(TrapLocs.SNARE_SET) { investigate(it.loc) }
        onOpLoc1(TrapLocs.SNARE_BROKEN) { dismantle(it.loc) }
        for (suffix in HunterPreyTable.suffixes(TrapFamily.BIRD_SNARE)) {
            onOpLoc1(TrapLocs.snareFull(suffix)) { checkTrap(it.loc) }
        }
    }

    private fun ScriptContext.registerBoxTrap() {
        onOpHeld1(HunterParams.BOX_TRAP_OBJ) {
            if (!org.rsmod.content.quest.manager.QuestRequirements.hasCompleted(player, "quest_eaglespeak")) {
                mes("You need to learn box trapping during Eagles' Peak first.")
                return@onOpHeld1
            }
            layGroundTrap(
                family = TrapFamily.BOX_TRAP,
                actionText = HunterMessages.SET_BOX_TRAP,
                laySynth = HunterParams.BOX_LAY_SYNTH,
            )
        }
        onOpLoc1(TrapLocs.BOX_SET) { dismantle(it.loc) }
        onOpLoc2(TrapLocs.BOX_SET) { investigate(it.loc) }
        onOpLoc1(TrapLocs.BOX_FAILED) { dismantle(it.loc) }
        onOpLoc2(TrapLocs.BOX_FAILED) { resetTrap(it.loc) }
        for (suffix in HunterPreyTable.suffixes(TrapFamily.BOX_TRAP)) {
            onOpLoc1(TrapLocs.boxFull(suffix)) { checkTrap(it.loc) }
            onOpLoc2(TrapLocs.boxFull(suffix)) { resetTrap(it.loc) }
        }
    }

    private fun ScriptContext.registerNetTrap() {
        for (colour in NetTrapColour.values()) {
            onOpLoc1(TrapLocs.saplingBare(colour.setSuffix)) { setNetTrap(it.loc, colour) }

            onOpLoc1(TrapLocs.saplingBent(colour.setSuffix)) { dismantle(it.loc) }
            onOpLoc2(TrapLocs.saplingBent(colour.setSuffix)) { investigate(it.loc) }
            onOpLoc1(TrapLocs.saplingArmed(colour.setSuffix)) { dismantle(it.loc) }
            onOpLoc2(TrapLocs.saplingArmed(colour.setSuffix)) { investigate(it.loc) }

            // Swamp caught locs use catchSuffix green, not setSuffix swamp.
            onOpLoc1(TrapLocs.saplingFull(colour.catchSuffix)) { checkTrap(it.loc) }
            onOpLoc2(TrapLocs.saplingFull(colour.catchSuffix)) { resetTrap(it.loc) }
        }
    }

    private fun ScriptContext.registerDeadfall() {
        onOpLoc1(TrapLocs.DEADFALL_BOULDER) { setDeadfall(it.loc) }
        onOpLoc1(TrapLocs.DEADFALL_SET) { dismantle(it.loc) }
        onOpLoc2(TrapLocs.DEADFALL_SET) { investigate(it.loc) }
        for (suffix in HunterPreyTable.suffixes(TrapFamily.DEADFALL)) {
            onOpLoc1(TrapLocs.deadfallFull(suffix)) { checkTrap(it.loc) }
        }
    }

    private suspend fun ProtectedAccess.layGroundTrap(
        family: TrapFamily,
        actionText: String,
        laySynth: String,
    ) {
        if (!meetsHunterLevel(family.levelReq, actionText)) {
            return
        }
        if (!withinTrapCap(family)) {
            return
        }
        if (!isTileFree(coords)) {
            mes(HunterMessages.TILE_OCCUPIED)
            return
        }

        if (!consumeLayTools(family)) {
            mes(constants.dm_default)
            return
        }

        val angle = if (family == TrapFamily.BOX_TRAP) LocAngle.East else LocAngle.West
        val trap = manager.beginLay(player, family, coords, angle, LocShape.CentrepieceStraight)

        anim(HunterParams.LAY_TRAP_ANIM)
        soundSynth(laySynth)
        spam(HunterMessages.LAY_START)

        // Held-item ops run before queue decrement; compensate initial scheduling with +1.
        queue(HunterParams.LAY_QUEUE, HunterParams.LAY_TICKS + 1, HunterLayTask(trap))
    }

    // Reserve without arriveDelay; suspension would allow duplicate trap claims.
    private fun ProtectedAccess.setNetTrap(loc: BoundLocInfo, colour: NetTrapColour) {
        val levelReq = HunterPreyTable.netTrapPrey(colour)?.levelReq ?: TrapFamily.NET_TRAP.levelReq
        if (!meetsHunterLevel(levelReq, HunterMessages.SET_NET_TRAP)) {
            return
        }
        if (!withinTrapCap(TrapFamily.NET_TRAP, colour)) {
            return
        }
        if (!manager.canLayNetTrap(loc.coords, loc.angle, loc.shape)) {
            mes(HunterMessages.TILE_OCCUPIED)
            return
        }
        if (!consumeLayTools(TrapFamily.NET_TRAP)) {
            mes(HunterMessages.NEED_ROPE_AND_NET)
            return
        }

        val trap = manager.beginLay(
            player,
            TrapFamily.NET_TRAP,
            loc.coords,
            loc.angle,
            loc.shape,
            netColour = colour,
        )

        faceLoc(loc)
        anim(HunterParams.LAY_NET_ANIM)
        soundSynth(HunterParams.NET_LAY_SYNTH)
        spam(HunterMessages.LAY_START)

        // Loc ops run after queue processing; their initial delay needs no +1.
        queue(
            HunterParams.LAY_QUEUE,
            HunterParams.LAY_TICKS,
            HunterLayTask(trap),
        )
    }

    private fun ProtectedAccess.setDeadfall(loc: BoundLocInfo) {
        if (!meetsHunterLevel(TrapFamily.DEADFALL.levelReq, HunterMessages.SET_DEADFALL)) {
            return
        }
        if (!withinTrapCap(TrapFamily.DEADFALL)) {
            return
        }
        if (manager.isTrapTileReserved(loc.coords)) {
            mes(HunterMessages.TILE_OCCUPIED)
            return
        }
        if (HunterParams.KNIVES.none { inv.count(it) > 0 }) {
            mes(HunterMessages.NEED_KNIFE_AND_LOGS)
            return
        }
        val log = HunterParams.DEADFALL_LOGS.firstOrNull { inv.count(it) > 0 }
        if (log == null) {
            mes(HunterMessages.NEED_KNIFE_AND_LOGS)
            return
        }
        val logPreserved = random.of(100) < HunterParams.DEADFALL_LOG_PRESERVE_CHANCE
        if (!logPreserved && !invDel(inv, log, count = 1).success) {
            mes(constants.dm_default)
            return
        }

        val trap = manager.beginLay(
            player, TrapFamily.DEADFALL, loc.coords, loc.angle, loc.shape,
            materials = if (logPreserved) emptyList() else listOf(log),
        )

        faceLoc(loc)
        anim(HunterParams.LAY_TRAP_ANIM)
        soundSynth(HunterParams.DEADFALL_LAY_SYNTH)
        spam(HunterMessages.LAY_START)

        queue(
            HunterParams.LAY_QUEUE,
            HunterParams.LAY_TICKS,
            HunterLayTask(trap),
        )
    }

    private fun ProtectedAccess.completeLay(task: HunterLayTask) {
        val trap = task.trap
        if (manager.trapAt(trap.coords) !== trap || trap.owner != player.uid || trap.state != TrapState.LAYING) return
        // The laying sequence loops beyond the action; reset it on completion.
        resetAnim()

        if (manager.finishLay(player, trap)) {
            if ((trap.family == TrapFamily.BIRD_SNARE || trap.family == TrapFamily.BOX_TRAP) && coords == trap.coords) {
                stepAwayFromTrap(trap.coords)
            }
            return
        }
        // The reservation owns its refund; stale queues must not refund it twice.
        manager.cancelLay(trap)
    }

    private fun ProtectedAccess.stepAwayFromTrap(trapCoords: CoordGrid) {
        val dest = collision.firstStepDestination(trapCoords, listOf(Direction.West)) ?: return
        walk(dest)
    }

    private fun ProtectedAccess.dismantle(loc: BoundLocInfo) {
        val owned = manager.ownedTrapAt(player, loc.coords, TrapState.SET, TrapState.FAILED)
        if (owned == null) {
            mes(HunterMessages.NOT_YOUR_TRAP)
            return
        }
        startTrapAction(loc, TrapAction.DISMANTLE, HunterParams.DISMANTLE_SYNTH)
    }

    private fun ProtectedAccess.checkTrap(loc: BoundLocInfo) {
        if (manager.ownedTrapAt(player, loc.coords, TrapState.FULL) == null) {
            mes(HunterMessages.NOT_YOUR_TRAP)
            return
        }
        startTrapAction(loc, TrapAction.CHECK, HunterParams.TAKE_CREATURE_SYNTH)
    }

    private fun ProtectedAccess.resetTrap(loc: BoundLocInfo) {
        if (manager.ownedTrapAt(player, loc.coords, TrapState.FULL, TrapState.FAILED) == null) {
            mes(HunterMessages.NOT_YOUR_TRAP)
            return
        }
        startTrapAction(loc, TrapAction.RESET, HunterParams.TAKE_CREATURE_SYNTH)
    }

    private fun ProtectedAccess.startTrapAction(
        loc: BoundLocInfo,
        action: TrapAction,
        synth: String,
    ) {
        val trap = manager.trapAt(loc.coords) ?: return
        faceLoc(loc)
        anim(if (trap.family == TrapFamily.BOX_TRAP) HunterParams.CHECK_BOX_ANIM else HunterParams.CHECK_TRAP_ANIM)
        soundSynth(synth)
        // Loc ops run after queue processing; their initial delay needs no +1.
        queue(
            HunterParams.CHECK_QUEUE,
            HunterParams.CHECK_TICKS,
            HunterTrapTask(trap, trap.generation, action),
        )
    }

    private fun ProtectedAccess.completeAction(task: HunterTrapTask) {
        val trap = task.trap
        if (manager.trapAt(trap.coords) !== trap || trap.owner != player.uid || trap.generation != task.generation) {
            return
        }
        resetAnim()

        // Recheck state after suspension; the trap may have sprung.
        when (task.action) {
            TrapAction.DISMANTLE -> {
                if (trap.state != TrapState.SET && trap.state != TrapState.FAILED) {
                    return
                }
                val tools = manager.takeEquipment(trap)
                manager.release(trap)
                returnTools(tools)
                mes(HunterMessages.DISMANTLED)
            }
            TrapAction.CHECK -> {
                if (trap.state != TrapState.FULL) {
                    return
                }
                deliverCatch(trap)
                val tools = manager.takeEquipment(trap)
                manager.release(trap)
                returnTools(tools)
            }
            TrapAction.RESET -> {
                if (trap.state != TrapState.FULL && trap.state != TrapState.FAILED) {
                    return
                }
                if (trap.state == TrapState.FULL) {
                    deliverCatch(trap)
                }
                val tools = manager.takeEquipment(trap)
                manager.release(trap)
                if (trap.family == TrapFamily.NET_TRAP && !manager.canLayNetTrap(trap.coords, trap.angle, trap.shape)) {
                    returnTools(tools)
                    mes(HunterMessages.TILE_OCCUPIED)
                    return
                }
                val replacement = manager.beginLay(
                    player, trap.family, trap.coords, trap.angle, trap.shape, trap.netColour,
                    materials = tools,
                )
                anim(if (trap.family == TrapFamily.NET_TRAP) HunterParams.LAY_NET_ANIM else HunterParams.LAY_TRAP_ANIM)
                soundSynth(if (trap.family == TrapFamily.NET_TRAP) HunterParams.NET_LAY_SYNTH else HunterParams.BOX_LAY_SYNTH)
                spam(HunterMessages.LAY_START)
                // Queue callbacks precede the new queue's first decrement.
                queue(HunterParams.LAY_QUEUE, HunterParams.LAY_TICKS + 1, HunterLayTask(replacement))
            }
        }
    }

    private fun ProtectedAccess.deliverCatch(trap: ActiveTrap) {
        val prey = trap.prey ?: return
        for (item in prey.loot) {
            val count = prey.lootCounts[item]?.let { random.of(it.first, it.last) } ?: 1
            invAddOrDrop(objRepo, item, count = count)
        }
        statAdvance(
            HunterParams.HUNTER_STAT,
            prey.xp * xpMods.get(player, HunterParams.HUNTER_STAT),
        )
        spam(HunterMessages.caught(creatureName(prey)))
        publish(HunterCatchEvent(player, prey.npc))
    }

    private fun ProtectedAccess.returnTools(tools: List<String>) {
        for (tool in tools) {
            invAddOrDrop(objRepo, tool, count = 1)
        }
    }

    private suspend fun ProtectedAccess.investigate(loc: BoundLocInfo) {
        arriveDelay()
        val trap = manager.trapAt(loc.coords)
        if (trap == null || trap.state != TrapState.SET) {
            mes(constants.dm_default)
            return
        }
        mes(HunterMessages.INVESTIGATE_SET)
    }

    private fun ProtectedAccess.meetsHunterLevel(levelReq: Int, action: String): Boolean {
        if (player.hunterLvl >= levelReq) {
            return true
        }
        mes(HunterMessages.levelRequirement(levelReq, action))
        return false
    }

    private fun ProtectedAccess.withinTrapCap(
        family: TrapFamily,
        netColour: NetTrapColour? = null,
    ): Boolean {
        if (family.singleTrapOnly) {
            if (manager.ownedDeadfallCount(player) < HunterParams.DEADFALL_TRAP_CAP) {
                return true
            }
            mes(HunterMessages.deadfallCap(HunterParams.DEADFALL_TRAP_CAP))
            return false
        }
        val max = manager.maxTraps(player, family, netColour)
        if (manager.ownedTrapCount(player) < max) {
            return true
        }
        mes(HunterMessages.maxTraps(max))
        return false
    }

    private fun ProtectedAccess.consumeLayTools(family: TrapFamily): Boolean = consumeTools(HunterParams.consumedTools(family))

    private fun ProtectedAccess.consumeTools(tools: List<String>): Boolean {
        val removed = mutableListOf<String>()
        for (tool in tools) {
            if (invDel(inv, tool, count = 1).success) {
                removed += tool
            } else {
                for (rollback in removed) {
                    invAdd(inv, rollback, count = 1)
                }
                return false
            }
        }
        return true
    }

    private fun isTileFree(coords: CoordGrid): Boolean {
        if (manager.isTrapTileReserved(coords)) {
            return false
        }
        return locRepo.findExact(coords, LocShape.CentrepieceStraight) == null
    }

    private fun creatureName(prey: HunterPrey): String =
        ServerCacheManager.getNpc(prey.npc.asRSCM(RSCMType.NPC))?.name ?: "creature"
}
