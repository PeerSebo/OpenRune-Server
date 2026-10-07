package org.rsmod.content.skills.hunter

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.abs
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.area.checker.isInWilderness
import org.rsmod.api.config.constants
import org.rsmod.api.invtx.invDel
import org.rsmod.api.npc.isValidTarget
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.hunterLvl
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.player.PlayerRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.RayCastValidator
import org.rsmod.content.skills.hunter.configs.HunterCatchRates
import org.rsmod.content.skills.hunter.configs.HunterMessages
import org.rsmod.content.skills.hunter.configs.HunterParams
import org.rsmod.content.skills.hunter.configs.HunterPrey
import org.rsmod.content.skills.hunter.configs.HunterPreyTable
import org.rsmod.content.skills.hunter.configs.NetTrapColour
import org.rsmod.content.skills.hunter.configs.TrapFamily
import org.rsmod.content.skills.hunter.configs.TrapLocs
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.loc.LocLayerConstants

@Singleton
class HunterTrapManager
@Inject
constructor(
    private val locRepo: LocRepository,
    private val npcRepo: NpcRepository,
    private val objRepo: ObjRepository,
    private val playerRepo: PlayerRepository,
    private val worldRepo: WorldRepository,
    private val playerList: PlayerList,
    private val mapClock: MapClock,
    private val random: GameRandom,
    private val areaChecker: AreaChecker,
    private val ray: RayCastValidator,
) {
    private val traps = LinkedHashMap<CoordGrid, ActiveTrap>()
    private data class Approach(val npc: Npc, val uid: NpcUid, val prey: HunterPrey, val destination: CoordGrid, val direction: String, val deadline: Int, val previousMode: NpcMode?)
    private val approaches = mutableMapOf<ActiveTrap, Approach>()
    private val reservedPrey = mutableMapOf<NpcUid, ActiveTrap>()
    private val externalTrapTiles = mutableSetOf<CoordGrid>()

    fun isTrapTileReserved(coords: CoordGrid): Boolean = trapAt(coords) != null || coords in externalTrapTiles

    fun reserveExternalTrap(coords: CoordGrid): Boolean {
        if (isTrapTileReserved(coords)) return false
        return externalTrapTiles.add(coords)
    }

    fun releaseExternalTrap(coords: CoordGrid) { externalTrapTiles.remove(coords) }

    // Resolve after gameval tables load.
    private val preyByNpcId: Map<Int, HunterPrey> by lazy {
        HunterPreyTable.ALL.associateBy { it.npc.asRSCM(RSCMType.NPC) }
    }

    fun tick() {
        if (traps.isEmpty()) {
            return
        }
        // Transitions may unregister traps; iterate a snapshot.
        for (trap in traps.values.toList()) {
            when (trap.state) {
                TrapState.TRAPPING ->
                    if (mapClock.cycle >= trap.transitionCycle) {
                        enterFull(trap)
                    }
                TrapState.FAILING ->
                    if (mapClock.cycle >= trap.transitionCycle) {
                        enterFailed(trap)
                    }
                TrapState.SET -> {
                    if (approaches.containsKey(trap)) {
                        advanceApproach(trap)
                        continue
                    }
                    val elapsed = mapClock.cycle - trap.armedCycle
                    val firstAttempt =
                        if (trap.family == TrapFamily.DEADFALL) {
                            HunterParams.DEADFALL_FIRST_ATTEMPT_TICKS
                        } else {
                            HunterParams.ATTEMPT_INTERVAL_TICKS
                        }
                    val due =
                        elapsed == firstAttempt ||
                            (elapsed > firstAttempt &&
                                (elapsed - firstAttempt) % HunterParams.ATTEMPT_INTERVAL_TICKS == 0)
                    if (due) {
                        attempt(trap)
                    }
                }
                TrapState.LAYING -> if (mapClock.cycle >= trap.layDeadline) cancelLay(trap)
                TrapState.FULL,
                TrapState.FAILED -> Unit
            }
        }
    }

    private fun attempt(trap: ActiveTrap) {
        if (findOwner(trap.owner) == null) return

        if (trap.family.blockedByPlayerOnTile && playerRepo.findAll(trap.coords).any()) {
            return
        }

        val candidates =
            npcRepo
                .findAll(ZoneKey.from(trap.coords), zoneRadius = 1)
                .filter { it.isValidTarget() }
                .filter { it.uid !in reservedPrey && it.coords.level == trap.coords.level }
                .filter { it.coords.chebyshevDistance(trap.coords) <= HunterParams.TRAP_RADIUS }
                .mapNotNull { npc -> preyFor(trap, npc)?.let { npc to it } }
                .toList()
        val (npc, prey) = random.pickOrNull(candidates) ?: return
        val target = when (trap.family) {
            TrapFamily.NET_TRAP -> frontCoords(trap)
            TrapFamily.DEADFALL -> {
                val adjacent = listOf(trap.coords.translateX(-1), trap.coords.translateZ(1), trap.coords.translateX(1), trap.coords.translateZ(-1))
                adjacent.sortedBy { it.chebyshevDistance(npc.coords) }.firstOrNull { ray.hasLineOfWalk(npc.coords, it) } ?: return
            }
            else -> trap.coords
        }
        if (!ray.hasLineOfWalk(npc.coords, target)) return
        val approach = Approach(npc, npc.uid, prey, target, approachDirection(trap.coords, npc.coords), mapClock.cycle + 10, npc.mode)
        approaches[trap] = approach
        reservedPrey[npc.uid] = trap
        // Temporarily own the approach so wandering cannot replace the route to the trap.
        npc.mode = NpcMode.None
        if (npc.coords == target) advanceApproach(trap) else npc.walk(target)
    }

    private fun advanceApproach(trap: ActiveTrap) {
        val approach = approaches[trap] ?: return
        val npc = approach.npc
        if (npc.uid != approach.uid || !ownsApproachControl(approach) || !npc.isValidTarget() || npc.coords.level != trap.coords.level || mapClock.cycle >= approach.deadline) {
            releaseApproach(trap)
            return
        }
        if (npc.coords != approach.destination) return
        if (trap.family.blockedByPlayerOnTile && playerRepo.findAll(approach.destination).any()) return
        val owner = findOwner(trap.owner) ?: run { releaseApproach(trap); return }
        val prey = approach.prey
        val level = owner.hunterLvl
        releaseApproach(trap)

        val chance = HunterGuildOutfit.catchChance(owner, HunterCatchRates.chance(prey.rate, level))
        val bonus = (if (trap.smoked) 2 else 0) + (if (trap.bait != null && trap.bait == HunterTrapEnhancements.baitFor(prey)) 3 else 0)
        val caught = level >= prey.levelReq &&
            if (bonus == 0) random.of(HunterCatchRates.DENOMINATOR) < chance
            else random.of(HunterCatchRates.DENOMINATOR * 100) < chance * 100 + bonus * HunterCatchRates.DENOMINATOR

        if (caught) {
            npcRepo.despawn(npc, HunterParams.PREY_RESPAWN_TICKS)
            enterTrapping(trap, prey, approach.direction)
        } else {
            enterFailing(trap)
        }
    }

    private fun releaseApproach(trap: ActiveTrap) {
        val approach = approaches.remove(trap) ?: return
        reservedPrey.remove(approach.uid, trap)
        // Do not reset a route claimed meanwhile by combat or escape logic.
        if (approach.npc.uid == approach.uid && ownsApproachControl(approach)) {
            approach.npc.abortRoute()
            approach.npc.mode = approach.previousMode
        }
    }

    private fun ownsApproachControl(approach: Approach): Boolean = approach.npc.mode == NpcMode.None &&
        approach.npc.routeDestination.peekLast().let { it == null || it == approach.destination }

    private fun preyFor(trap: ActiveTrap, npc: Npc): HunterPrey? {
        val prey = preyByNpcId[npc.visType.id] ?: return null
        if (prey.family != trap.family) {
            return null
        }
        if (trap.family == TrapFamily.NET_TRAP && prey.locSuffix != trap.netColour?.catchSuffix) {
            return null
        }
        return prey
    }

    fun approachDirection(trap: CoordGrid, prey: CoordGrid): String {
        val dx = prey.x - trap.x
        val dz = prey.z - trap.z
        return if (abs(dx) >= abs(dz)) {
            if (dx >= 0) "e" else "w"
        } else {
            if (dz >= 0) "n" else "s"
        }
    }

    private fun enterTrapping(trap: ActiveTrap, prey: HunterPrey, approach: String) {
        // The armed net's front-tile loc exists only in SET; remove it on every transition.
        clearNetTrapFrontLoc(trap)

        trap.prey = prey
        trap.state = TrapState.TRAPPING
        trap.transitionCycle = mapClock.cycle + HunterParams.TRANSITION_TICKS

        val loc =
            when (trap.family) {
                TrapFamily.BIRD_SNARE -> TrapLocs.snareTrapping(prey.locSuffix)
                TrapFamily.BOX_TRAP -> TrapLocs.boxTrapping(prey.locSuffix, approach)
                TrapFamily.NET_TRAP -> TrapLocs.saplingCatching(prey.locSuffix)
                TrapFamily.DEADFALL -> TrapLocs.deadfallTrapping(prey.locSuffix)
            }
        // The transition loc animates itself; another LocAnim would duplicate presentation.
        swap(trap, loc, Int.MAX_VALUE)
        soundAtTrap(trap, springSynth(trap.family))
    }

    private fun enterFull(trap: ActiveTrap) {
        val prey = trap.prey
        if (prey == null) {
            release(trap)
            return
        }
        trap.state = TrapState.FULL

        val loc =
            when (trap.family) {
                TrapFamily.BIRD_SNARE -> TrapLocs.snareFull(prey.locSuffix)
                TrapFamily.BOX_TRAP -> TrapLocs.boxFull(prey.locSuffix)
                TrapFamily.NET_TRAP -> TrapLocs.saplingFull(prey.locSuffix)
                TrapFamily.DEADFALL -> TrapLocs.deadfallFull(prey.locSuffix)
            }
        swap(trap, loc, HunterParams.CAUGHT_LIFETIME_TICKS) {
            expire(trap, HunterMessages.CREATURE_ESCAPED, playEscapeSound = true)
        }

        when (trap.family) {
            TrapFamily.BIRD_SNARE -> soundAtTrap(trap, HunterParams.SNARE_CAUGHT_SYNTH)
            TrapFamily.BOX_TRAP -> soundAtTrap(trap, HunterParams.BOX_CAUGHT_SYNTH)
            TrapFamily.NET_TRAP,
            TrapFamily.DEADFALL -> Unit
        }
    }

    private fun enterFailing(trap: ActiveTrap) {
        clearNetTrapFrontLoc(trap)

        trap.prey = null
        trap.state = TrapState.FAILING
        trap.transitionCycle = mapClock.cycle + HunterParams.TRANSITION_TICKS

        val loc =
            when (trap.family) {
                TrapFamily.BIRD_SNARE -> TrapLocs.SNARE_FAILING
                TrapFamily.BOX_TRAP -> TrapLocs.BOX_FAILING
                TrapFamily.NET_TRAP -> TrapLocs.saplingFailing(netColour(trap).setSuffix)
                TrapFamily.DEADFALL -> TrapLocs.DEADFALL_FAILING
            }
        swap(trap, loc, Int.MAX_VALUE)
        val failSynth =
            if (trap.family == TrapFamily.BOX_TRAP) {
                HunterParams.BOX_SPRING_SYNTH
            } else {
                HunterParams.ESCAPE_SYNTH
            }
        soundAtTrap(trap, failSynth)
    }

    private fun enterFailed(trap: ActiveTrap) {
        when (trap.family) {
            TrapFamily.BIRD_SNARE -> {
                trap.state = TrapState.FAILED
                swap(trap, TrapLocs.SNARE_BROKEN, HunterParams.FAILED_LIFETIME_TICKS) {
                    expire(trap, HunterMessages.TRAP_COLLAPSED, playEscapeSound = false)
                }
            }
            TrapFamily.BOX_TRAP -> {
                trap.state = TrapState.FAILED
                swap(trap, TrapLocs.BOX_FAILED, HunterParams.FAILED_LIFETIME_TICKS) {
                    expire(trap, HunterMessages.TRAP_COLLAPSED, playEscapeSound = false)
                }
            }
            TrapFamily.NET_TRAP -> {
                dropNetEquipment(trap)
                release(trap)
            }
            TrapFamily.DEADFALL -> release(trap)
        }
    }

    private fun expire(trap: ActiveTrap, message: String, playEscapeSound: Boolean) {
        if (playEscapeSound) {
            soundAtTrap(trap, HunterParams.ESCAPE_SYNTH)
        }
        dropEquipment(trap)
        unregister(trap)
        findOwner(trap.owner)?.mes(message)
    }

    private fun dropNetEquipment(trap: ActiveTrap) {
        dropEquipment(trap)
        findOwner(trap.owner)?.mes(HunterMessages.NET_TRAP_SNAPPED)
    }

    private fun dropEquipment(trap: ActiveTrap) {
        val owner = findOwner(trap.owner)
        val duration = owner?.lootDropDuration ?: constants.lootdrop_duration
        for (tool in takeEquipment(trap)) {
            objRepo.add(tool, trap.coords, duration, receiver = owner)
        }
    }

    fun takeEquipment(trap: ActiveTrap): List<String> = trap.equipment.also {
        trap.equipment = emptyList()
    }

    private fun swap(
        trap: ActiveTrap,
        name: String,
        duration: Int,
        onDespawn: (() -> Unit)? = null,
    ) {
        trap.locName = name
        trap.generation++
        val generation = trap.generation
        val guarded: (() -> Unit)? =
            if (onDespawn == null) {
                null
            } else {
                {
                    // Reject stale timer callbacks from earlier traps on this tile.
                    val current = traps[trap.coords]
                    if (current === trap && current.generation == generation) {
                        onDespawn()
                    }
                }
            }
        locRepo.add(trap.coords, name, duration, trap.angle, trap.shape, guarded)
    }

    fun release(trap: ActiveTrap) {
        if (traps[trap.coords] !== trap) return
        restoreTile(trap)
        unregister(trap)
    }

    private fun restoreTile(trap: ActiveTrap) {
        if (trap.state == TrapState.SET) {
            clearNetTrapFrontLoc(trap)
        }
        // Remove only this trap's state; another world action may have replaced its loc.
        if (trap.locName.isEmpty()) return
        val current = locRepo.findExact(trap.coords, trap.shape) ?: return
        if (current.id != trap.locName.asRSCM(RSCMType.LOC) || current.angle != trap.angle) return
        val bare = bareLoc(trap)
        if (bare != null) {
            // Re-adding the exact static entity removes its override and pending despawn timer.
            locRepo.add(trap.coords, bare, Int.MAX_VALUE, trap.angle, trap.shape)
            return
        }
        // Cancel the spawned loc's add timer explicitly before deletion.
        locRepo.add(trap.coords, trap.locName, Int.MAX_VALUE, trap.angle, trap.shape)
        locRepo.del(locInfo(trap.coords, trap.locName, trap.angle, trap.shape), Int.MAX_VALUE)
    }

    private fun bareLoc(trap: ActiveTrap): String? =
        when (trap.family) {
            TrapFamily.BIRD_SNARE,
            TrapFamily.BOX_TRAP -> null
            TrapFamily.NET_TRAP -> TrapLocs.saplingBare(netColour(trap).setSuffix)
            TrapFamily.DEADFALL -> TrapLocs.DEADFALL_BOULDER
        }

    private fun locInfo(
        coords: CoordGrid,
        name: String,
        angle: LocAngle,
        shape: LocShape,
    ): LocInfo {
        val layer = LocLayerConstants.of(shape.id)
        return LocInfo(layer, coords, LocEntity(name.asRSCM(RSCMType.LOC), shape.id, angle.id))
    }

    private fun soundAtTrap(trap: ActiveTrap, synth: String) {
        worldRepo.soundArea(
            trap.coords,
            synth,
            delay = 0,
            loops = 1,
            radius = HunterParams.TRAP_SOUND_RADIUS,
        )
    }

    private fun springSynth(family: TrapFamily): String =
        when (family) {
            TrapFamily.BIRD_SNARE -> HunterParams.SNARE_SPRING_SYNTH
            TrapFamily.BOX_TRAP -> HunterParams.BOX_SPRING_SYNTH
            TrapFamily.NET_TRAP -> HunterParams.NET_SPRING_SYNTH
            TrapFamily.DEADFALL -> HunterParams.DEADFALL_SPRING_SYNTH
        }

    private fun netColour(trap: ActiveTrap): NetTrapColour =
        trap.netColour ?: error("Net trap registered without a colour: ${trap.coords}")

    private fun frontCoords(trap: ActiveTrap): CoordGrid = frontCoords(trap.coords, trap.angle)

    private fun frontCoords(coords: CoordGrid, angle: LocAngle): CoordGrid =
        when (angle) {
            LocAngle.West -> coords.translateX(-1)
            LocAngle.North -> coords.translateZ(1)
            LocAngle.East -> coords.translateX(1)
            LocAngle.South -> coords.translateZ(-1)
        }

    private fun clearNetTrapFrontLoc(trap: ActiveTrap) {
        if (trap.family != TrapFamily.NET_TRAP) {
            return
        }
        val netLoc = TrapLocs.saplingArmed(netColour(trap).setSuffix)
        val current = locRepo.findExact(frontCoords(trap), trap.shape) ?: return
        if (current.id != netLoc.asRSCM(RSCMType.LOC) || current.angle != trap.angle) return
        locRepo.add(frontCoords(trap), netLoc, Int.MAX_VALUE, trap.angle, trap.shape)
        locRepo.del(locInfo(frontCoords(trap), netLoc, trap.angle, trap.shape), Int.MAX_VALUE)
    }

    fun trapAt(coords: CoordGrid): ActiveTrap? {
        traps[coords]?.let { return it }
        return traps.values.firstOrNull {
            it.family == TrapFamily.NET_TRAP && frontCoords(it) == coords
        }
    }

    // Reserve both net-trap tiles before consuming materials.
    fun canLayNetTrap(coords: CoordGrid, angle: LocAngle, shape: LocShape): Boolean {
        val front = frontCoords(coords, angle)
        return !isTrapTileReserved(coords) && !isTrapTileReserved(front) && locRepo.findExact(front, shape) == null
    }

    fun ownedTrapAt(player: Player, coords: CoordGrid, vararg states: TrapState): ActiveTrap? {
        val trap = trapAt(coords) ?: return null
        if (trap.owner != player.uid) {
            return null
        }
        return trap.takeIf { it.state in states }
    }

    fun maxTraps(
        player: Player,
        family: TrapFamily? = null,
        netColour: NetTrapColour? = null,
    ): Int {
        val wildernessBonusEligible =
            when (family) {
                TrapFamily.NET_TRAP -> netColour == NetTrapColour.BLACK
                TrapFamily.BOX_TRAP -> true
                else -> false
            }
        return HunterParams.maxConcurrentTraps(
            level = player.hunterLvl,
            inWilderness = player.coords.isInWilderness(areaChecker),
            wildernessBonusEligible = wildernessBonusEligible,
        )
    }

    fun ownedTrapCount(player: Player): Int =
        traps.values.count { it.owner == player.uid && it.family != TrapFamily.DEADFALL } +
            player.activePitfallCount +
            player.activeMagicBoxCount

    fun ownedDeadfallCount(player: Player): Int =
        traps.values.count { it.owner == player.uid && it.family == TrapFamily.DEADFALL }

    fun beginLay(
        player: Player,
        family: TrapFamily,
        coords: CoordGrid,
        angle: LocAngle,
        shape: LocShape,
        netColour: NetTrapColour? = null,
        materials: List<String> = HunterParams.consumedTools(family),
    ): ActiveTrap {
        check(!isTrapTileReserved(coords)) { "Trap tile is already reserved: $coords" }
        check(family != TrapFamily.NET_TRAP || canLayNetTrap(coords, angle, shape)) { "Net trap front tile is occupied: $coords" }
        val trap = ActiveTrap(coords, family, player.uid, angle, shape, netColour)
        trap.layMaterials = materials
        trap.layDeadline = mapClock.cycle + HunterParams.LAY_TICKS + 2
        traps[coords] = trap

        val setting =
            when (family) {
                TrapFamily.NET_TRAP -> TrapLocs.saplingSetting(netColour(trap).setSuffix)
                TrapFamily.DEADFALL -> TrapLocs.DEADFALL_SETTING
                else -> null
            }
        if (setting != null) {
            swap(trap, setting, Int.MAX_VALUE)
        }
        return trap
    }

    fun finishLay(player: Player, trap: ActiveTrap): Boolean {
        if (traps[trap.coords] !== trap) return false
        if (trap.owner != player.uid || trap.state != TrapState.LAYING) {
            return false
        }
        val current = locRepo.findExact(trap.coords, trap.shape)
        val expectedId = trap.locName.takeIf { it.isNotEmpty() }?.asRSCM(RSCMType.LOC)
        if (current?.id != expectedId || (current != null && current.angle != trap.angle)) return false
        if (trap.family == TrapFamily.NET_TRAP && locRepo.findExact(frontCoords(trap), trap.shape) != null) return false
        arm(trap)
        return true
    }

    fun cancelLay(trap: ActiveTrap) {
        if (traps[trap.coords] !== trap || trap.state != TrapState.LAYING) return
        val owner = findOwner(trap.owner)
        for (material in trap.layMaterials) {
            objRepo.add(material, trap.coords, owner?.lootDropDuration ?: constants.lootdrop_duration, receiver = owner)
        }
        trap.layMaterials = emptyList()
        trap.equipment = emptyList()
        release(trap)
    }

    private fun arm(trap: ActiveTrap) {
        releaseApproach(trap)
        trap.bait = null
        trap.smoked = false
        val owner = findOwner(trap.owner)
        if (owner != null && HunterTrapEnhancements.canSmoke(trap) && owner.inv.count("obj.anti_odour_salt") > 0) {
            trap.smoked = owner.invDel(owner.inv, "obj.anti_odour_salt").success
        }
        trap.layMaterials = emptyList()
        trap.prey = null
        trap.state = TrapState.SET
        trap.armedCycle = mapClock.cycle

        val loc =
            when (trap.family) {
                TrapFamily.BIRD_SNARE -> TrapLocs.SNARE_SET
                TrapFamily.BOX_TRAP -> TrapLocs.BOX_SET
                TrapFamily.NET_TRAP -> TrapLocs.saplingBent(netColour(trap).setSuffix)
                TrapFamily.DEADFALL -> TrapLocs.DEADFALL_SET
            }
        swap(trap, loc, HunterParams.SET_LIFETIME_TICKS) {
            expire(trap, HunterMessages.TRAP_COLLAPSED, playEscapeSound = false)
        }

        if (trap.family == TrapFamily.NET_TRAP) {
            // Only the tree loc's callback owns trap expiry; a second callback would duplicate cleanup.
            val netLoc = TrapLocs.saplingArmed(netColour(trap).setSuffix)
            locRepo.add(
                frontCoords(trap),
                netLoc,
                HunterParams.SET_LIFETIME_TICKS,
                trap.angle,
                trap.shape,
            )
        }
    }

    private fun unregister(trap: ActiveTrap) {
        releaseApproach(trap)
        if (traps[trap.coords] === trap) {
            traps.remove(trap.coords)
        }
    }

    private fun findOwner(uid: PlayerUid): Player? = playerList.firstOrNull { it.uid == uid }

    fun onPlayerLogout(player: Player) {
        for (trap in traps.values.toList()) {
            if (trap.owner != player.uid) {
                continue
            }
            if (trap.state == TrapState.LAYING) {
                cancelLay(trap)
            } else {
                dropEquipment(trap)
                release(trap)
            }
        }
    }
}
