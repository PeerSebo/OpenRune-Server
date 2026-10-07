@file:OptIn(org.rsmod.annotations.InternalApi::class)

package org.rsmod.content.skills.hunter

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.config.constants
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.invtx.invDel
import org.rsmod.api.npc.isValidTarget
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.hunterLvl
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.player.PlayerRepository
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.hunter.configs.CatchRate
import org.rsmod.content.skills.hunter.configs.HunterCatchRates
import org.rsmod.content.skills.hunter.configs.HunterParams
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag
import org.rsmod.routefinder.loc.LocLayerConstants

@Singleton
internal class MagicBoxService @Inject constructor(
    private val manager: HunterTrapManager,
    private val clock: MapClock,
    private val locs: LocRepository,
    private val npcs: NpcRepository,
    private val objects: ObjRepository,
    private val players: PlayerRepository,
    private val collision: CollisionFlagMap,
    private val random: GameRandom,
    private val xpMods: XpModifiers,
    private val events: EventBus,
) {
    enum class State { SETTING, ARMED, TRAPPING, FULL, FAILED }
    class Trap(val owner: Player, val coords: CoordGrid) {
        var state = State.SETTING
        var loc: LocInfo? = null
        var due = 0
        var expires = 0
        var baited = false
    }
    private val traps = mutableMapOf<CoordGrid, Trap>()
    private val ray = org.rsmod.api.route.RayCastValidator(collision)
    private val impIds by lazy { listOf("npc.imp", "npc.godwars_ancient_imp").map { it.asRSCM(RSCMType.NPC) }.toSet() }

    fun begin(player: Player): Trap? {
        if (player.hunterLvl < 71) { player.mes("You need a Hunter level of 71 to activate a magic box."); return null }
        if (inPuroPuro(player.coords)) { player.mes("You can't set up a trap here."); return null }
        val cap = HunterParams.maxConcurrentTraps(player.hunterLvl, false, false)
        if (manager.ownedTrapCount(player) >= cap) { player.mes("You have already set your maximum number of traps."); return null }
        if (player.inv.count(EMPTY) == 0) return null
        if (locs.findExact(player.coords, LocShape.CentrepieceStraight) != null ||
            collision[player.coords.x, player.coords.z, player.coords.level] and (CollisionFlag.BLOCK_WALK or CollisionFlag.GROUND_DECOR or CollisionFlag.LOC) != 0 ||
            !manager.reserveExternalTrap(player.coords)) {
            player.mes("There is not enough space to set a trap here.")
            return null
        }
        val trap = Trap(player, player.coords).apply { expires = clock.cycle + HunterParams.LAY_TICKS + 2 }
        traps[trap.coords] = trap
        sync(player)
        return trap
    }

    fun finish(trap: Trap): Boolean {
        if (!current(trap) || trap.state != State.SETTING) return false
        val player = trap.owner
        if (player.coords != trap.coords || player.hunterLvl < 71 || player.inv.count(EMPTY) == 0 ||
            locs.findExact(trap.coords, LocShape.CentrepieceStraight) != null) { cancel(trap); return false }
        if (!swap(trap, ARMED_LOC)) { cancel(trap); return false }
        if (!player.invDel(player.inv, EMPTY).success) { remove(trap); return false }
        trap.state = State.ARMED
        trap.due = clock.cycle + HunterParams.ATTEMPT_INTERVAL_TICKS
        trap.expires = clock.cycle + HunterParams.SET_LIFETIME_TICKS
        player.mes("You activate the magic box and place it on the ground.")
        return true
    }

    fun cancel(trap: Trap) { if (current(trap) && trap.state == State.SETTING) remove(trap) }

    fun owned(player: Player, loc: BoundLocInfo): Trap? = traps[loc.coords]?.takeIf {
        it.owner === player && it.loc?.id == loc.id && adjacent(player.coords, loc.coords) && locs.findExact(loc.coords, LocShape.CentrepieceStraight)?.id == loc.id
    }

    fun retrieve(player: Player, loc: BoundLocInfo): Boolean {
        val trap = owned(player, loc) ?: return false
        if (trap.state == State.SETTING || trap.state == State.TRAPPING) return false
        val full = trap.state == State.FULL
        remove(trap) // Claim before awarding items/XP to reject repeated clicks.
        player.invAddOrDrop(objects, if (full) FULL else EMPTY)
        if (full) {
            player.statAdvance("stat.hunter", 450.0 * xpMods.get(player, "stat.hunter"))
            events.publish(HunterCatchEvent(player, "npc.imp"))
        }
        player.mes(if (full) "You retrieve the imp in its magic box." else "You deactivate and pick up the magic box.")
        return true
    }

    fun bait(player: Player, loc: BoundLocInfo, item: String): Boolean {
        val trap = owned(player, loc) ?: return false
        if (item !in BEADS) {
            player.mes(if (item == "obj.torch_lit") "Imps steal so many human items, they're unlikely to be worried by human smells." else "You can bait this trap with a coloured bead.")
            return false
        }
        if (trap.state != State.ARMED || trap.baited) { player.mes("You cannot add bait to this trap right now."); return false }
        if (!player.invDel(player.inv, item).success) return false
        trap.baited = true
        player.mes("You bait the magic box with a bead.")
        return true
    }

    fun tick() {
        for (trap in traps.values.toList()) {
            if (!trap.owner.isSlotAssigned || trap.owner.statMap.getCurrentLevel("stat.hitpoints") <= 0) { collapse(trap); continue }
            if (trap.loc != null && locs.findExact(trap.coords, LocShape.CentrepieceStraight)?.id != trap.loc?.id) { collapse(trap); continue }
            if (clock.cycle >= trap.expires) { collapse(trap); continue }
            if (trap.state == State.TRAPPING && clock.cycle >= trap.due) {
                if (!swap(trap, FULL_LOC)) { collapse(trap); continue }
                trap.state = State.FULL
                trap.expires = clock.cycle + HunterParams.CAUGHT_LIFETIME_TICKS
            } else if (trap.state == State.ARMED && clock.cycle >= trap.due) {
                trap.due = clock.cycle + HunterParams.ATTEMPT_INTERVAL_TICKS
                attempt(trap)
            }
        }
    }

    private fun attempt(trap: Trap) {
        if (players.findAll(trap.coords).any()) return
        val imp = npcs.findAll(ZoneKey.from(trap.coords), 1).firstOrNull {
            it.isValidTarget() && it.visType.id in impIds && it.coords.level == trap.coords.level && it.coords.chebyshevDistance(trap.coords) <= 1 &&
                ray.hasLineOfWalk(it.coords, trap.coords, srcWidth = it.size, srcLength = it.size)
        } ?: return
        val base = HunterCatchRates.chance(RATE, trap.owner.hunterLvl)
        val chance = HunterGuildOutfit.catchChance(trap.owner, base) / 256.0 + if (trap.baited) .03 else 0.0
        val caught = trap.owner.hunterLvl >= 71 && random.randomDouble() < chance.coerceAtMost(1.0)
        val newLoc = if (caught) TRAPPING_LOC else FAILED_LOC
        if (!swap(trap, newLoc)) { collapse(trap); return }
        trap.baited = false
        if (caught) {
            npcs.despawn(imp, 50)
            trap.state = State.TRAPPING
            trap.due = clock.cycle + HunterParams.TRANSITION_TICKS
            trap.expires = trap.due + HunterParams.CAUGHT_LIFETIME_TICKS
        } else {
            trap.state = State.FAILED
            trap.expires = clock.cycle + HunterParams.FAILED_LIFETIME_TICKS
        }
    }

    fun cleanup(player: Player) { traps.values.filter { it.owner === player }.toList().forEach(::collapse); player.activeMagicBoxCount = 0 }

    private fun collapse(trap: Trap) {
        val consumed = trap.state != State.SETTING
        if (!current(trap)) return
        remove(trap)
        if (consumed) objects.add(EMPTY, trap.coords, trap.owner.lootDropDuration ?: constants.lootdrop_duration, receiver = trap.owner)
    }

    private fun swap(trap: Trap, symbol: String): Boolean {
        val shape = LocShape.CentrepieceStraight
        val loc = LocInfo(LocLayerConstants.of(shape.id), trap.coords, LocEntity(symbol.asRSCM(RSCMType.LOC), shape.id, 0))
        if (!locs.add(loc, Int.MAX_VALUE)) return false
        trap.loc = loc
        return true
    }
    private fun remove(trap: Trap) {
        if (!current(trap)) return
        trap.loc?.let { expected ->
            if (locs.findExact(trap.coords, LocShape.CentrepieceStraight)?.id == expected.id) locs.del(expected, Int.MAX_VALUE)
        }
        traps.remove(trap.coords)
        manager.releaseExternalTrap(trap.coords)
        sync(trap.owner)
    }
    private fun current(trap: Trap) = traps[trap.coords] === trap
    private fun sync(player: Player) { player.activeMagicBoxCount = traps.values.count { it.owner === player } }

    companion object {
        const val EMPTY = "obj.magic_imp_box"
        const val FULL = "obj.magic_imp_box_full"
        const val HALF = "obj.magic_imp_box_half"
        const val ARMED_LOC = "loc.hunting_imptrap_empty"
        const val FAILED_LOC = "loc.hunting_imptrap_failed"
        const val TRAPPING_LOC = "loc.hunting_imptrap_trapping"
        const val FULL_LOC = "loc.hunting_imptrap_full"
        val BEADS = setOf("obj.red_bead", "obj.yellow_bead", "obj.black_bead", "obj.white_bead")
        val RATE = CatchRate(1, 198, roundToNearest = true)
        fun inPuroPuro(coords: CoordGrid) = coords.x in 2560..2623 && coords.z in 4288..4351
        private fun adjacent(a: CoordGrid, b: CoordGrid) = a.level == b.level && a.chebyshevDistance(b) <= 1
    }
}
