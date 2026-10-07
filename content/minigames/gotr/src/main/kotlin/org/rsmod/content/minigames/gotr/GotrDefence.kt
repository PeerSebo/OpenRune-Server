package org.rsmod.content.minigames.gotr

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.output.mes
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.route.RayCastValidator
import org.rsmod.api.route.RouteFactory
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.headbar.Headbar
import org.rsmod.game.hit.Hitmark
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.loc.LocLayerConstants

@Singleton
public class GotrDefence @Inject constructor(
    private val locs: LocRepository,
    private val npcs: NpcRepository,
    private val clock: MapClock,
    private val random: GameRandom,
    private val routes: RouteFactory,
    private val ray: RayCastValidator,
    private val players: PlayerList,
    private val collision: CollisionFlagMap,
) {
    private val barriers = SITES.associateWith { Barrier(it) }
    private val creatures = mutableListOf<Fighter>()
    private val guardians = mutableListOf<Fighter>()
    private val fullBarrierBudgets = mutableMapOf<String, GotrDefenceRules.FullBarrierBudget>()
    private var nextWave = GotrDefenceRules.FIRST_ATTACK_TICK
    private var announcedAttack = false
    private var announcedPreparation = false
    private var intensified = false
    private var running = false

    public fun guardianCount(): Int = guardians.count { it.hp > 0 }
    public fun guardianLimit(): Int = GotrDefenceRules.GUARDIAN_LIMIT
    public fun creatureCount(): Int = creatures.count { it.hp > 0 }

    public fun beginRound() {
        clearFighters(creatures)
        nextWave = GotrDefenceRules.FIRST_ATTACK_TICK
        announcedAttack = false
        announcedPreparation = false
        intensified = false
        running = true
        fullBarrierBudgets.clear()
        greatGuardian()?.anim("seq.portal_guardian_wakeup")
        for (barrier in barriers.values) if (barrier.tile == null) replaceTile(barrier, "loc.gotr_cell_tile_inactive")
    }

    public fun stopCreatures() {
        running = false
        for (fighter in creatures + guardians) die(fighter)
        creatures.clear()
        guardians.clear()
        greatGuardian()?.anim("seq.portal_guardian_surge")
    }

    public fun endRound() {
        running = false
        clearFighters(creatures)
        clearFighters(guardians)
        for (barrier in barriers.values) {
            barrier.visual?.let { locs.del(it, Int.MAX_VALUE) }
            removeNpc(barrier.carrier)
            barrier.visual = null
            barrier.carrier = null
            barrier.tier = 0
            barrier.hp = 0
            barrier.broken = false
            replaceTile(barrier, "loc.gotr_cell_tile_inactive")
        }
        greatGuardian()?.anim("seq.portal_guardian_sleep")
    }

    public fun tick(elapsedActiveTicks: Int, powerPercent: Int, memberCount: Int, broadcast: (String) -> Unit): Int {
        if (!running) return 0
        if (!announcedPreparation && elapsedActiveTicks >= 1) {
            announcedPreparation = true
            broadcast("Creatures from the Abyss will attack in 120 seconds.")
        }
        if (!announcedAttack && elapsedActiveTicks >= GotrDefenceRules.FIRST_ATTACK_TICK) {
            announcedAttack = true
            broadcast("<col=ef1020>Creatures from the Abyss begin their attack!</col>")
        }
        if (memberCount <= 0) {
            clearFighters(creatures)
            return 0
        }
        if (!intensified && powerPercent >= 60) {
            intensified = true
            broadcast("<col=ef1020>The rift burns intensely... More creatures from the Abyss are emerging!</col>")
            for (barrier in barriers.values.filter { it.tier > 0 }) damageBarrier(barrier, minOf(50, (barrier.hp - 1).coerceAtLeast(0)))
        }
        if (elapsedActiveTicks >= nextWave) {
            spawnWave(memberCount)
            nextWave = GotrDefenceRules.nextWaveTick(elapsedActiveTicks)
        }
        if (elapsedActiveTicks == 8 || elapsedActiveTicks % 20 == 0) greatGuardian()?.anim("seq.portal_guardian_siphon")
        for (guardian in guardians.toList()) {
            if (guardian.hp <= 0) continue
            val target = creatures.filter { it.hp > 0 }.minByOrNull { distance(guardian.npc, it.npc) }
            if (target != null) fight(guardian, target)
            else walkTowards(guardian, CoordGrid(if (guardian.catalytic) 3607 else 3622, 9514, 0))
        }
        var powerDamage = 0
        val great = greatGuardian()
        for (creature in creatures.toList()) {
            if (creature.hp <= 0) continue
            val retaliation = creature.retaliation?.takeIf { it.hp > 0 && it in guardians }
            if (retaliation != null) {
                fight(creature, retaliation)
                continue
            }
            val greatDistance = great?.let { distance(creature.npc, it) } ?: Int.MAX_VALUE
            val clearToGreat = great != null && (inRange(creature, great) || clearApproach(creature.npc, great))
            creature.rebuiltBarrier = creature.rebuiltBarrier?.takeIf { it.tier > 0 }
            // Blocked sight to a rear wall must not divert creatures from the nearer Great Guardian.
            val barrier = creature.rebuiltBarrier ?: barriers.values.asSequence()
                .filter { it.tier > 0 && !clearToGreat }
                .map { it to GotrDefenceRules.rectangleDistance(creature.npc.coords.x, creature.npc.coords.z, creature.npc.size, it.site.wall.x, it.site.wall.z, it.site.size) }
                .filter { (_, distance) -> distance < greatDistance }
                .minByOrNull { (_, distance) -> distance }?.first
            if (barrier != null) {
                creature.attackingGreatGuardian = false
                val carrier = barrier.carrier ?: continue
                if (inRange(creature, carrier)) {
                    if (clock.cycle >= creature.nextAttack) {
                        attackAnimation(creature, carrier)
                        damageBarrier(barrier, rollDamage(creature))
                    }
                } else walkTowards(creature, carrier)
            } else if (great != null) {
                if (inRange(creature, great)) {
                    if (clock.cycle >= creature.nextAttack) {
                        attackAnimation(creature, great)
                        creature.attackingGreatGuardian = true
                        powerDamage += rollDamage(creature)
                    }
                } else walkTowards(creature, great)
            }
        }
        creatures.removeAll { it.hp <= 0 }
        guardians.removeAll { it.hp <= 0 }
        return powerDamage
    }

    internal fun isCellTile(coords: CoordGrid): Boolean = barriers.keys.any { it.tile == coords }
    internal fun needsRepair(coords: CoordGrid): Boolean = barriers.values.firstOrNull { it.site.tile == coords }?.broken == true

    internal fun fullBarrierReward(name: String, energyEach: Int, xp: Double): Pair<Int, Double> =
        fullBarrierBudgets.getOrPut(name) { GotrDefenceRules.FullBarrierBudget() }.claim(energyEach, xp)

    internal fun repair(coords: CoordGrid): Boolean {
        val barrier = barriers.values.firstOrNull { it.site.tile == coords && it.broken } ?: return false
        barrier.broken = false
        replaceTile(barrier, "loc.gotr_cell_tile_inactive")
        return true
    }

    internal fun placeCell(coords: CoordGrid, tier: Int): GotrDefenceRules.CellUse? {
        val barrier = barriers.values.firstOrNull { it.site.tile == coords } ?: return null
        if (barrier.broken) return null
        val use = GotrDefenceRules.useCell(barrier.tier, barrier.hp, tier)
        val rebuilt = barrier.tier == 0
        val changed = barrier.tier != use.tier
        barrier.tier = use.tier
        barrier.hp = use.hitpoints
        if (changed) {
            barrier.visual?.let { locs.del(it, Int.MAX_VALUE) }
            val suffix = if (barrier.site.size == 3) "_3x3" else ""
            barrier.visual = locs.add(barrier.site.wall, "loc.gotr_barrier_tier" + use.tier + suffix, Int.MAX_VALUE, LocAngle[barrier.site.wallAngle], LocShape[barrier.site.wallShape])
            val npcType = "npc.gotr_barrier_npc_tier" + use.tier + if (barrier.site.size == 3) "_large" else ""
            val previous = barrier.carrier
            if (previous != null) {
                previous.transmog(checkNotNull(ServerCacheManager.getNpc(npcType.asRSCM(RSCMType.NPC))), Int.MAX_VALUE)
            } else barrier.carrier = spawnNpc(npcType, barrier.site.wall)
            replaceTile(barrier, "loc.gotr_cell_tile_tier" + use.tier)
            pushPlayersOff(barrier.site)
            if (rebuilt) {
                for (creature in creatures.filter { it.attackingGreatGuardian }) {
                    creature.rebuiltBarrier = barrier
                    creature.attackingGreatGuardian = false
                    creature.nextRoute = 0
                    creature.npc.abortRoute()
                }
            }
        }
        showHealth(barrier.carrier, barrier.hp, GotrDefenceRules.barrierHitpoints[barrier.tier - 1], large = true)
        return use
    }

    internal fun assemble(tier: Int, catalytic: Boolean): Boolean {
        if (guardianCount() >= guardianLimit()) return false
        val symbols = if (catalytic) CATALYTIC_GUARDIANS else ELEMENTAL_GUARDIANS
        val start = CoordGrid(if (catalytic) 3609 else 3620, 9491, 0)
        val npc = spawnNpc(symbols[tier - 1], start)
        val fighter = Fighter(npc, GotrDefenceRules.guardianHitpoints[tier - 1], tier * 2, 4, if (catalytic) 6 else 1, catalytic, null)
        npc.baseHitpointsLvl = fighter.hp
        npc.hitpoints = fighter.hp
        npc.anim("seq.rift_guardian_spawn")
        fighter.nextAttack = clock.cycle + 4
        guardians += fighter
        walkTowards(fighter, CoordGrid(if (catalytic) 3606 else 3623, 9514, 0))
        return true
    }

    private fun spawnWave(memberCount: Int) {
        // Party-size spawn scaling is provisional.
        val count = (memberCount + 3).coerceIn(4, 28)
        if (creatures.size >= count * 4) return
        repeat(count) { index ->
            val roll = random.of(100)
            val species = when {
                roll < if (intensified) 25 else 8 -> Species.GUARDIAN
                roll < if (intensified) 50 else 20 -> Species.WALKER
                else -> Species.LEECH
            }
            val npc = spawnNpc(species.symbol, CoordGrid(3611 + index % 9, 9518 + index / 9, 0))
            npc.baseHitpointsLvl = species.hp
            npc.hitpoints = species.hp
            creatures += Fighter(npc, species.hp, species.maxHit, species.attackTicks, 1, false, species)
        }
    }

    private fun fight(attacker: Fighter, target: Fighter) {
        attacker.attackingGreatGuardian = false
        if (!inRange(attacker, target.npc)) {
            walkTowards(attacker, target.npc)
            return
        }
        if (clock.cycle < attacker.nextAttack) return
        attackAnimation(attacker, target.npc)
        val damage = rollDamage(attacker).coerceAtMost(target.hp)
        target.hp -= damage
        if (attacker.species == null) target.retaliation = attacker
        showDamage(target.npc, damage)
        showHealth(target.npc, target.hp, target.npc.baseHitpointsLvl)
        if (target.hp <= 0) die(target)
        else if (damage > 0) target.npc.anim(target.species?.defend ?: "seq.rift_guardian_defend_melee")
    }

    private fun rollDamage(attacker: Fighter): Int = random.of(attacker.maxHit + 1)

    private fun attackAnimation(fighter: Fighter, target: Npc) {
        fighter.npc.abortRoute()
        fighter.npc.faceNpc(target)
        fighter.npc.anim(fighter.species?.attack ?: if (fighter.catalytic) "seq.rift_guardian_attack_ranged" else "seq.rift_guardian_attack_melee")
        fighter.nextAttack = clock.cycle + fighter.attackTicks
    }

    private fun damageBarrier(barrier: Barrier, damage: Int) {
        barrier.hp = (barrier.hp - damage).coerceAtLeast(0)
        barrier.carrier?.let { showDamage(it, damage) }
        showHealth(barrier.carrier, barrier.hp, GotrDefenceRules.barrierHitpoints[barrier.tier - 1], large = true)
        if (barrier.hp > 0) return
        barrier.visual?.let { locs.del(it, Int.MAX_VALUE) }
        removeNpc(barrier.carrier)
        barrier.visual = null
        barrier.carrier = null
        barrier.tier = 0
        barrier.broken = true
        replaceTile(barrier, "loc.gotr_cell_tile_broken_repairable")
    }

    private fun inRange(fighter: Fighter, target: Npc): Boolean = distance(fighter.npc, target) <= fighter.range &&
        (fighter.range == 1 || ray.hasLineOfSight(fighter.npc.coords, target.coords))

    private fun distance(a: Npc, b: Npc): Int = GotrDefenceRules.rectangleDistance(a.coords.x, a.coords.z, a.size, b.coords.x, b.coords.z, b.size)

    private fun clearApproach(source: Npc, target: Npc): Boolean {
        // Test attack reach outside the footprint; its decorative interior need not be walkable.
        val x = when {
            source.coords.x + source.size <= target.coords.x -> target.coords.x - source.size
            source.coords.x >= target.coords.x + target.size -> target.coords.x + target.size
            else -> source.coords.x
        }
        val z = when {
            source.coords.z + source.size <= target.coords.z -> target.coords.z - source.size
            source.coords.z >= target.coords.z + target.size -> target.coords.z + target.size
            else -> source.coords.z
        }
        return ray.hasLineOfWalk(source.coords, CoordGrid(x, z, source.coords.level),
            srcWidth = source.size, srcLength = source.size, destWidth = source.size, destLength = source.size)
    }

    private fun walkTowards(fighter: Fighter, target: Npc) {
        if (clock.cycle < fighter.nextRoute) return
        fighter.nextRoute = clock.cycle + 4
        val route = routes.create(fighter.npc.avatar, target.avatar)
        fighter.npc.walk(route.map { CoordGrid(it.x, it.z, it.level) })
    }

    private fun walkTowards(fighter: Fighter, target: CoordGrid) {
        if (clock.cycle < fighter.nextRoute || fighter.npc.coords == target) return
        fighter.nextRoute = clock.cycle + 4
        val route = routes.create(fighter.npc.avatar, target)
        fighter.npc.walk(route.map { CoordGrid(it.x, it.z, it.level) })
    }

    private fun die(fighter: Fighter) {
        fighter.hp = 0
        fighter.npc.abortRoute()
        fighter.npc.anim(fighter.species?.death ?: "seq.rift_guardian_death")
        // Retain the body for its death sequence; generic death handlers do not own it.
        fighter.npc.lifecycleDelCycle = clock.cycle + 4
    }

    private fun spawnNpc(symbol: String, coords: CoordGrid): Npc {
        val npc = Npc(symbol, coords)
        npc.mode = NpcMode.None
        npc.ignoreCombatInteractions = true
        npc.moveRestrict = MoveRestrict.PassThru
        npcs.add(npc, Int.MAX_VALUE)
        npc.respawns = false
        return npc
    }

    private fun greatGuardian(): Npc? = npcs.findAll(CoordGrid(3613, 9501, 0)).firstOrNull { it.type.id == "npc.gotr_great_guardian".asRSCM(RSCMType.NPC) }

    private fun clearFighters(fighters: MutableList<Fighter>) {
        for (fighter in fighters) removeNpc(fighter.npc)
        fighters.clear()
    }

    private fun removeNpc(npc: Npc?) { if (npc?.isSlotAssigned == true) npcs.del(npc, Int.MAX_VALUE) }

    private fun replaceTile(barrier: Barrier, symbol: String) {
        val current = barrier.tile ?: locs.findExact(barrier.site.tile, LocShape.GroundDecor)
        val replacement = LocInfo(LocLayerConstants.of(LocShape.GroundDecor.id), barrier.site.tile, LocEntity(symbol.asRSCM(RSCMType.LOC), LocShape.GroundDecor.id, barrier.site.tileAngle))
        if (locs.add(replacement, Int.MAX_VALUE)) barrier.tile = replacement else barrier.tile = current
    }

    private fun pushPlayersOff(site: Site) {
        for (player in players) {
            val c = player.coords
            if (c.level != 0 || c.x !in site.wall.x until site.wall.x + site.size || c.z !in site.wall.z until site.wall.z + site.size) continue
            val choices = listOf(CoordGrid(site.wall.x - 1, c.z, 0), CoordGrid(site.wall.x + site.size, c.z, 0), CoordGrid(c.x, site.wall.z - 1, 0), CoordGrid(c.x, site.wall.z + site.size, 0))
            val destination = choices.minBy { it.chebyshevDistance(c) }
            PathingEntityCommon.telejump(player, collision, destination)
            player.mes("The force of the new barrier pushes you away.")
        }
    }

    private fun showDamage(npc: Npc, damage: Int) {
        val type = (if (damage == 0) "hitmark.zero_damage_tint" else "hitmark.regular_damage_tint").asRSCM(RSCMType.HITMARK)
        npc.showHitmark(Hitmark.fromNoSource(type, type, type, damage, 0))
    }

    private fun showHealth(npc: Npc?, hp: Int, max: Int, large: Boolean = false) {
        if (npc == null) return
        // Controller HP is authoritative; generic death scripts must not race deletion.
        npc.hitpoints = hp.coerceAtLeast(1)
        val type = checkNotNull(ServerCacheManager.getHealthBar((if (large) "headbar.health_50" else "headbar.health_30").asRSCM(RSCMType.HEADBAR)))
        val fill = (hp * type.segments / max.coerceAtLeast(1)).coerceIn(0, type.segments)
        npc.showHeadbar(Headbar.fromNoSource(type.id, type.id, fill, fill, 0, 0))
    }

    private data class Fighter(val npc: Npc, var hp: Int, val maxHit: Int, val attackTicks: Int, val range: Int, val catalytic: Boolean, val species: Species?, var nextAttack: Int = 0, var nextRoute: Int = 0, var retaliation: Fighter? = null, var attackingGreatGuardian: Boolean = false, var rebuiltBarrier: Barrier? = null)
    private data class Site(val tile: CoordGrid, val tileAngle: Int, val wall: CoordGrid, val size: Int, val wallShape: Int, val wallAngle: Int)
    private data class Barrier(val site: Site, var tier: Int = 0, var hp: Int = 0, var broken: Boolean = false, var tile: LocInfo? = null, var visual: LocInfo? = null, var carrier: Npc? = null)
    private enum class Species(val symbol: String, val hp: Int, val maxHit: Int, val attackTicks: Int, val stem: String) {
        LEECH("npc.gotr_abyssal_leech", 5, 2, 2, "leech"),
        WALKER("npc.gotr_abyssal_walker", 30, 6, 4, "walker"),
        GUARDIAN("npc.gotr_abyssal_pyramid", 50, 5, 4, "pyramid");
        val attack get() = "seq.gotr_abyssal_" + stem + "_attack"
        val defend get() = "seq.gotr_abyssal_" + stem + "_defend"
        val death get() = "seq.gotr_abyssal_" + stem + "_death"
    }

    public companion object {
        private val ELEMENTAL_GUARDIANS = listOf("npc.gotr_guardian_air", "npc.gotr_guardian_water", "npc.gotr_guardian_earth", "npc.gotr_guardian_fire")
        private val CATALYTIC_GUARDIANS = listOf("npc.gotr_guardian_mind", "npc.gotr_guardian_chaos", "npc.gotr_guardian_death", "npc.gotr_guardian_blood")

        private val SITES = listOf(
            Site(CoordGrid(3608, 9503, 0), 3, CoordGrid(3605, 9502, 0), 3, 10, 1),
            Site(CoordGrid(3609, 9507, 0), 3, CoordGrid(3607, 9507, 0), 2, 11, 1),
            Site(CoordGrid(3611, 9509, 0), 0, CoordGrid(3610, 9510, 0), 2, 11, 1),
            Site(CoordGrid(3615, 9510, 0), 0, CoordGrid(3614, 9511, 0), 3, 10, 0),
            Site(CoordGrid(3619, 9509, 0), 0, CoordGrid(3619, 9510, 0), 2, 11, 2),
            Site(CoordGrid(3621, 9507, 0), 1, CoordGrid(3622, 9507, 0), 2, 11, 2),
            Site(CoordGrid(3622, 9503, 0), 1, CoordGrid(3623, 9502, 0), 3, 10, 3),
        )
        public val CELL_TILES: List<CoordGrid> = SITES.map { it.tile }
    }
}
