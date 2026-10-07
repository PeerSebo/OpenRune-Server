@file:OptIn(org.rsmod.annotations.InternalApi::class)

package org.rsmod.content.skills.hunter

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.invtx.invDel
import org.rsmod.api.npc.isValidTarget
import org.rsmod.api.player.hit.modifier.StandardPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.spam
import org.rsmod.api.player.stat.hunterLvl
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.RayCastValidator
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.hunter.configs.HunterParams
import org.rsmod.content.skills.hunter.configs.PitfallPrey
import org.rsmod.content.skills.hunter.configs.PitfallSpot
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

@Singleton
internal class PitfallService @Inject constructor(
    private val manager: HunterTrapManager,
    private val clock: MapClock,
    private val npcs: NpcRepository,
    private val objects: ObjRepository,
    private val world: WorldRepository,
    private val collision: CollisionFlagMap,
    private val ray: RayCastValidator,
    private val random: GameRandom,
    private val xpMods: XpModifiers,
    private val hitModifier: StandardPlayerHitModifier,
    private val events: EventBus,
) {
    internal enum class State { SETTING, ARMED, CATCHING, FULL }
    internal class Trap(val owner: Player, val spot: PitfallSpot, val loc: BoundLocInfo, val log: String, val origin: CoordGrid) {
        var state = State.SETTING
        var due = 0
        var fullState = 3
    }
    internal class Chase(val owner: Player, val npc: Npc, val uid: NpcUid, val prey: PitfallPrey) {
        var lastPit: Int? = null
        var nextAttack = 0
        var jumpingUntil = 0
    }

    private val traps = mutableMapOf<Player, MutableMap<Int, Trap>>()
    private val chases = mutableMapOf<Player, Chase>()
    private val claimedPrey = mutableMapOf<NpcUid, Player>()
    private val spotsById by lazy { PitfallSpot.ALL.associateBy { it.loc.asRSCM(RSCMType.LOC) } }
    private val preyById by lazy { PitfallPrey.entries.associateBy { it.npc.asRSCM(RSCMType.NPC) } }

    fun beginSet(player: Player, loc: BoundLocInfo): Trap? {
        val spot = spotsById[loc.id] ?: return null
        if (!adjacent(player.coords, loc)) return null
        if (traps[player]?.containsKey(spot.number) == true || player.vars[spot.varbit] != 0) { player.mes("You already have a trap in this pit."); return null }
        if (player.hunterLvl < spot.prey.level) { player.mes("You need a Hunter level of ${spot.prey.level} to set this pitfall trap."); return null }
        val cap = HunterParams.maxConcurrentTraps(player.hunterLvl, inWilderness = false, wildernessBonusEligible = false)
        if (manager.ownedTrapCount(player) >= cap) { player.mes("You have already set your maximum number of traps."); return null }
        val log = materials(player) ?: return null
        val trap = Trap(player, spot, loc, log, player.coords)
        traps.getOrPut(player) { mutableMapOf() }[spot.number] = trap
        syncCount(player)
        return trap
    }

    fun finishSet(trap: Trap): Boolean {
        val player = trap.owner
        if (!current(trap) || trap.state != State.SETTING) return false
        if (player.coords != trap.origin || player.hunterLvl < trap.spot.prey.level || !hasKnife(player) || !player.invDel(player.inv, trap.log).success) {
            cancelSet(trap)
            return false
        }
        trap.state = State.ARMED
        setState(trap, 1)
        player.spam("You cover the pit with logs and set a spiked trap.")
        return true
    }

    fun cancelSet(trap: Trap) { if (current(trap) && trap.state == State.SETTING) remove(trap) }

    fun tease(player: Player, npc: Npc): Boolean {
        val prey = preyById[npc.visType.id] ?: return false
        if (!npc.isValidTarget() || player.coords.level != npc.coords.level || distance(player.coords, npc.coords) > 2 || !ray.hasLineOfSight(player.coords, npc.coords, destWidth = npc.size, destLength = npc.size)) return false
        if (player.hunterLvl < prey.level) { player.mes("You need a Hunter level of ${prey.level} to hunt this creature."); return false }
        if (player.inv.count(TEASING_STICK) == 0 && player.worn.count(HUNTER_SPEAR) == 0) { player.mes("You need a teasing stick in your inventory or an equipped hunter's spear."); return false }
        val owner = claimedPrey[npc.uid]
        if (owner != null && owner !== player) { player.mes("Someone else is already teasing that creature."); return false }
        if (chases[player]?.npc !== npc) {
            releaseChase(player)
            chases[player] = Chase(player, npc, npc.uid, prey).apply { nextAttack = clock.cycle + 2 }
        }
        claimedPrey[npc.uid] = player
        npc.resetMode()
        npc.facePlayer(player)
        npc.walk(player.coords)
        player.faceNpc(npc)
        player.anim("seq.hunting_teasing_animal")
        world.soundArea(player.coords, "synth.HUNTING_TEASE_FELINE")
        player.spam("You tease the ${npc.visType.name.lowercase()}. It turns to chase you.")
        return true
    }

    fun crossing(player: Player, loc: BoundLocInfo): CoordGrid? {
        val trap = trap(player, loc) ?: run { player.mes("You need to set this pitfall trap first."); return null }
        if (trap.state != State.ARMED) return null
        val destination = oppositeSide(loc, player.coords) ?: return null
        if (!canStand(destination)) { player.mes("There is no clear space on the other side of the pit."); return null }
        return destination
    }

    fun crossed(player: Player, loc: BoundLocInfo, start: CoordGrid, end: CoordGrid) {
        val trap = trap(player, loc) ?: return
        val chase = chases[player] ?: return
        if (trap.state != State.ARMED || chase.prey != trap.spot.prey || chase.lastPit == trap.spot.number || !valid(chase)) return
        if (chase.jumpingUntil > clock.cycle || chase.npc.coords.level != start.level || distance(chase.npc.coords, start) > 3) return
        if (!sameSide(start, chase.npc.coords, loc)) return
        chase.lastPit = trap.spot.number
        chase.jumpingUntil = clock.cycle + CROSSING_TICKS
        val npc = chase.npc
        npc.resetMovement()
        val caught = random.of(100) < HunterGuildOutfit.catchChance(player, chase.prey.catchPercent(player.hunterLvl, player.worn.count(HUNTER_SPEAR) > 0), 100)
        if (caught) {
            trap.state = State.CATCHING
            trap.due = clock.cycle + CROSSING_TICKS
            val direction = crossingDirection(start, end)
            trap.fullState = if (((direction / 512 - loc.angleId) and 3) >= 2) 4 else 3
            setState(trap, 2)
            world.soundArea(loc.coords, "synth.HUNTING_PITFALL_COLLAPSE")
            npcs.despawn(npc, chase.prey.respawnTicks)
            claimedPrey.remove(chase.uid, player)
            chases.remove(player)
            player.spam("The creature falls into your pitfall trap!")
        } else {
            // Shift south/west roots so the prey's entire 2x2 footprint clears the pit.
            val npcEnd = end.translate(if (end.x < start.x) 1 - npc.size else 0, if (end.z < start.z) 1 - npc.size else 0)
            if ((0 until npc.size).any { x -> (0 until npc.size).any { z -> !canStand(npcEnd.translate(x, z)) } }) {
                chase.jumpingUntil = 0
                player.spam("The creature cannot find room to land on the other side.")
                return
            }
            npc.anim("seq.hunter_bigcat_leap_npc")
            PathingEntityCommon.exactMove(npc, npc.coords, npcEnd, 0, CROSSING_TICKS * 30, crossingDirection(start, end), collision)
            player.spam("The creature leaps over your trap. You will need to lure it over a different pit.")
        }
    }

    fun dismantle(player: Player, loc: BoundLocInfo): Boolean {
        val trap = trap(player, loc) ?: return false
        if (!adjacent(player.coords, loc) || trap.state == State.CATCHING || trap.state == State.SETTING) return false
        val caught = trap.state == State.FULL
        remove(trap)
        if (caught) {
            val prey = trap.spot.prey
            player.invAddOrDrop(objects, "obj.big_bones")
            player.invAddOrDrop(objects, prey.meat)
            val fur = if (prey.damagedFur != null && random.of(100) >= prey.perfectFurPercent(player.hunterLvl)) prey.damagedFur else prey.fur
            player.invAddOrDrop(objects, fur)
            prey.antler?.let { player.invAddOrDrop(objects, it) }
            player.statAdvance("stat.hunter", prey.xp * xpMods.get(player, "stat.hunter"))
            events.publish(HunterCatchEvent(player, prey.npc))
            player.spam("You dismantle the pitfall trap and collect your catch.")
        } else player.spam("You dismantle the pitfall trap.")
        return true
    }

    fun tick() {
        for ((player, owned) in traps.toList()) {
            if (!player.isSlotAssigned || player.statMap.getCurrentLevel("stat.hitpoints") <= 0) { cleanup(player); continue }
            for (trap in owned.values.toList()) {
                if (player.coords.level != trap.loc.level || distance(player.coords, trap.loc.coords) > 64) { remove(trap); continue }
                if (trap.state == State.CATCHING && clock.cycle >= trap.due) { trap.state = State.FULL; setState(trap, trap.fullState) }
            }
        }
        for ((player, chase) in chases.toList()) {
            if (!player.isSlotAssigned || !valid(chase) || player.statMap.getCurrentLevel("stat.hitpoints") <= 0 || player.coords.level != chase.npc.coords.level || distance(player.coords, chase.npc.coords) > 18 || distance(chase.npc.coords, chase.npc.spawnCoords) > 24) {
                releaseChase(player)
                continue
            }
            if (clock.cycle < chase.jumpingUntil) continue
            val npc = chase.npc
            if (chase.jumpingUntil != 0) { npc.resetAnim(); chase.jumpingUntil = 0 }
            npc.facePlayer(player)
            val inMeleeReach = player.coords.x in npc.coords.x - 1..npc.coords.x + npc.size && player.coords.z in npc.coords.z - 1..npc.coords.z + npc.size
            if (!inMeleeReach || !ray.hasLineOfWalk(npc.coords, player.coords, srcWidth = npc.size, srcLength = npc.size)) npc.walk(player.coords)
            else {
                npc.resetMovement()
                if (clock.cycle >= chase.nextAttack) {
                    chase.nextAttack = clock.cycle + 4
                    npc.anim(if (chase.prey.guaranteed) "seq.unicorn_rework_attack" else "seq.hunter_big_cat_bite")
                    player.queueHit(npc, delay = 1, type = HitType.Melee, damage = random.of(chase.prey.maxHit + 1), modifier = hitModifier)
                }
            }
        }
    }

    fun cleanup(player: Player) {
        traps.remove(player)
        player.activePitfallCount = 0
        for (spot in PitfallSpot.ALL) if (player.vars[spot.varbit] != 0) VarPlayerIntMapSetter.set(player, spot.varbit, 0)
        releaseChase(player)
    }

    private fun releaseChase(player: Player) {
        val chase = chases.remove(player) ?: return
        claimedPrey.remove(chase.uid, player)
        if (valid(chase)) { chase.npc.resetMovement(); chase.npc.resetAnim(); chase.npc.defaultMode() }
    }
    private fun valid(chase: Chase): Boolean = chase.npc.uid == chase.uid && chase.npc.isValidTarget() && chase.npc.visType.id == chase.prey.npc.asRSCM(RSCMType.NPC)
    internal fun trap(player: Player, loc: BoundLocInfo): Trap? = spotsById[loc.id]?.let { traps[player]?.get(it.number) }?.takeIf { it.loc.coords == loc.coords }
    internal fun chase(player: Player): Chase? = chases[player]
    private fun current(trap: Trap): Boolean = traps[trap.owner]?.get(trap.spot.number) === trap
    private fun remove(trap: Trap) {
        if (!current(trap)) return
        traps[trap.owner]?.remove(trap.spot.number)
        if (traps[trap.owner]?.isEmpty() == true) traps.remove(trap.owner)
        setState(trap, 0)
        syncCount(trap.owner)
    }
    private fun syncCount(player: Player) { player.activePitfallCount = traps[player]?.size ?: 0 }
    private fun setState(trap: Trap, state: Int) = VarPlayerIntMapSetter.set(trap.owner, trap.spot.varbit, state)
    private fun hasKnife(player: Player): Boolean = HunterParams.KNIVES.any { player.inv.count(it) > 0 }
    private fun materials(player: Player): String? {
        if (!hasKnife(player)) { player.mes("You need a knife to build a pitfall trap."); return null }
        return HunterParams.DEADFALL_LOGS.firstOrNull { player.inv.count(it) > 0 }.also { if (it == null) player.mes("You need suitable logs to build a pitfall trap.") }
    }
    private fun canStand(coords: CoordGrid): Boolean = collision[coords.x, coords.z, coords.level] and (CollisionFlag.LOC or CollisionFlag.BLOCK_WALK or CollisionFlag.GROUND_DECOR) == 0

    companion object {
        const val TEASING_STICK = "obj.hunting_teasing_stick"
        const val HUNTER_SPEAR = "obj.hg_hunter_spear"
        const val CROSSING_TICKS = 2

        fun distance(a: CoordGrid, b: CoordGrid): Int = maxOf(kotlin.math.abs(a.x - b.x), kotlin.math.abs(a.z - b.z))
        fun adjacent(from: CoordGrid, loc: BoundLocInfo): Boolean = from.level == loc.level && from.x in loc.x - 1..loc.x + loc.adjustedWidth && from.z in loc.z - 1..loc.z + loc.adjustedLength
        fun oppositeSide(loc: BoundLocInfo, from: CoordGrid): CoordGrid? {
            if (from.level != loc.level) return null
            val east = loc.x + loc.adjustedWidth
            val north = loc.z + loc.adjustedLength
            return when {
                from.z == loc.z - 1 && from.x in loc.x until east -> CoordGrid(from.x, north, from.level)
                from.z == north && from.x in loc.x until east -> CoordGrid(from.x, loc.z - 1, from.level)
                from.x == loc.x - 1 && from.z in loc.z until north -> CoordGrid(east, from.z, from.level)
                from.x == east && from.z in loc.z until north -> CoordGrid(loc.x - 1, from.z, from.level)
                else -> null
            }
        }
        private fun sameSide(player: CoordGrid, npc: CoordGrid, loc: BoundLocInfo): Boolean = when {
            player.z < loc.z -> npc.z < loc.z
            player.z >= loc.z + loc.adjustedLength -> npc.z >= loc.z + loc.adjustedLength
            player.x < loc.x -> npc.x < loc.x
            else -> npc.x >= loc.x + loc.adjustedWidth
        }
        fun crossingDirection(start: CoordGrid, end: CoordGrid): Int = when { end.z > start.z -> 1024; end.z < start.z -> 0; end.x > start.x -> 1536; else -> 512 }
    }
}
