package org.rsmod.content.raids.theatreofblood

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MoveRestrict
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.IdentityHashMap
import kotlin.math.abs
import org.rsmod.api.bosses.runtime.bossProjectile
import org.rsmod.api.mechanics.stun.Stun
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.route.RayCastValidator
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.Hit
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.map.CoordGrid

@Singleton
internal class TheatreBloat @Inject constructor(
    private val manager: TheatreManager,
    private val rays: RayCastValidator,
) {
    private val deps get() = manager.deps
    private val rules get() = TheatreData.mechanics
    private val fleshTiles = IdentityHashMap<TheatreRun, List<CoordGrid>>()
    private val previousFlesh = IdentityHashMap<TheatreRun, Set<CoordGrid>>()

    fun start(run: TheatreRun) {
        val boss = checkNotNull(run.boss)
        boss.moveRestrict = MoveRestrict.PassThru
        boss.vars["varn.theatre_style"] = deps.random.of(2)
        boss.vars["varn.theatre_target_pillar"] = if (boss.vars["varn.theatre_style"] == 0) 1 else 0
        boss.vars["varn.theatre_bloat_turn_remaining"] = rules.bloatTurnTicks
        boss.vars["varn.theatre_bloat_flesh_next"] = run.tick + rules.bloatFleshTicks
        fleshTiles[run] = buildList {
            val bounds = run.roomrow.bounds
            for (x in bounds[0]..bounds[2]) for (z in bounds[1]..bounds[3]) {
                val point = CoordGrid(x, z, bounds[4])
                if (!deps.collision.isWalkBlocked(manager.coord(run, point))) add(point)
            }
        }
        wake(run)
    }

    fun clear(run: TheatreRun) {
        fleshTiles.remove(run)
        previousFlesh.remove(run)
    }

    fun impact(run: TheatreRun, npc: Npc, hit: Hit) {
        if (!manager.active(run) || npc !== run.boss || npc.hitpoints <= 0 || !hit.isFromPlayer) return
        if (hit.resolvePlayerSource(deps.playerList) !in manager.combatants(run)) return
        if (run.phase == 1) npc.vars["varn.theatre_bloat_down_attacked"] = 1
        if (npc.hitpoints * 100L < npc.baseHitpointsLvl * 40L) {
            npc.vars["varn.theatre_bloat_run_toggle"] = npc.vars["varn.theatre_bloat_run_toggle"] xor 1
        }
    }

    fun tick(run: TheatreRun) {
        val boss = checkNotNull(run.boss)
        if (run.tick % rules.bloatDefenceRegenTicks == 0) {
            boss.defenceLvl = (boss.defenceLvl + 1).coerceAtMost(boss.type.defence)
        }
        if (run.phase == 1) {
            val downAt = boss.vars["varn.theatre_sleep_until"] - rules.bloatSleepTicks
            if (run.tick >= downAt + rules.bloatStompTicks && boss.vars["varn.theatre_bloat_stomped"] == 0) {
                boss.vars["varn.theatre_bloat_stomped"] = 1
                boss.defenceLvl = boss.type.defence
                for (player in manager.combatants(run).filter { visible(boss, it) }) {
                    manager.damage(run, player, deps.random.of(rules.bloatStompMin, rules.bloatStompMax))
                }
            }
            if (run.tick < boss.vars["varn.theatre_sleep_until"]) return
            wake(run)
        }
        speed(run)
        turn(run)
        if (canSleep(run)) {
            run.phase = 1
            boss.abortRoute()
            boss.movementLocked = true
            boss.anim("seq.tob_bloat_sleep")
            boss.vars["varn.theatre_sleep_until"] = run.tick + rules.bloatSleepTicks
            boss.vars["varn.theatre_bloat_stomped"] = 0
            return
        }
        walk(run)
        if (run.tick % rules.bloatFliesTicks == 0) flies(run)
        if (boss.hitpoints * 10L < boss.baseHitpointsLvl * 9L &&
            run.tick >= boss.vars["varn.theatre_bloat_flesh_next"]) {
            flesh(run)
            val interval = if (boss.hitpoints * 10L < boss.baseHitpointsLvl * 6L)
                rules.bloatFleshFastTicks else rules.bloatFleshTicks
            boss.vars["varn.theatre_bloat_flesh_next"] = run.tick + interval
        }
    }

    private fun wake(run: TheatreRun) {
        val boss = checkNotNull(run.boss)
        val duration = if (boss.vars["varn.theatre_bloat_down_attacked"] == 0)
            rules.bloatFirstWalkTicks else rules.bloatWalkTicks
        boss.vars["varn.theatre_bloat_down_attacked"] = 0
        boss.vars["varn.theatre_walk_until"] = run.tick + duration
        boss.vars["varn.theatre_bloat_walk_end"] = run.tick + duration + rules.bloatWalkWindowTicks
        boss.movementLocked = false
        boss.resetAnim()
        run.phase = 0
        speed(run)
        walk(run)
    }

    private fun canSleep(run: TheatreRun): Boolean {
        val boss = checkNotNull(run.boss)
        if (!TheatreMechanics.bloatCanSleep(run.tick, boss.vars["varn.theatre_walk_until"],
                boss.vars["varn.theatre_bloat_move_change"], rules.bloatMoveChangeTicks)) return false
        return run.tick >= boss.vars["varn.theatre_bloat_walk_end"] ||
            deps.random.of(rules.bloatSleepChance) == 0
    }

    private fun speed(run: TheatreRun) {
        val boss = checkNotNull(run.boss)
        val running = TheatreMechanics.bloatRuns(boss.hitpoints, boss.baseHitpointsLvl,
            boss.vars["varn.theatre_bloat_run_toggle"] != 0)
        val value = if (running) 1 else 0
        if (boss.hitpoints * 100L >= boss.baseHitpointsLvl * 40L) {
            boss.vars["varn.theatre_bloat_run_toggle"] = value
        }
        if (value != boss.vars["varn.theatre_bloat_running"]) {
            boss.vars["varn.theatre_bloat_running"] = value
            boss.vars["varn.theatre_bloat_move_change"] = run.tick
        }
        boss.defaultMoveSpeed = if (running) MoveSpeed.Run else MoveSpeed.Walk
        boss.moveSpeed = boss.defaultMoveSpeed
    }

    private fun turn(run: TheatreRun) {
        val boss = checkNotNull(run.boss)
        val remaining = boss.vars["varn.theatre_bloat_turn_remaining"]
        if (remaining > 0) boss.vars["varn.theatre_bloat_turn_remaining"] = remaining - 1
        if (boss.vars["varn.theatre_bloat_turn_remaining"] > 0 ||
            deps.random.of(rules.bloatTurnChance) != 0) return
        val direction = if (boss.vars["varn.theatre_style"] == 0) 1 else -1
        boss.vars["varn.theatre_target_pillar"] = Math.floorMod(
            boss.vars["varn.theatre_target_pillar"] - direction, rules.bloatRoute.size,
        )
        boss.vars["varn.theatre_style"] = boss.vars["varn.theatre_style"] xor 1
        boss.vars["varn.theatre_bloat_turn_remaining"] = rules.bloatTurnTicks
        boss.vars["varn.theatre_bloat_move_change"] = run.tick
        boss.abortRoute()
    }

    private fun walk(run: TheatreRun) {
        val boss = checkNotNull(run.boss)
        val ring = rules.bloatRoute
        var next = boss.vars["varn.theatre_target_pillar"]
        if (boss.coords == manager.coord(run, ring[next])) {
            next = Math.floorMod(next + if (boss.vars["varn.theatre_style"] == 0) 1 else -1, ring.size)
            boss.vars["varn.theatre_target_pillar"] = next
        }
        val target = manager.coord(run, ring[next])
        if (boss.routeDestination.isEmpty() || boss.routeDestination.peekLast() != target) boss.walk(target)
    }

    internal fun visible(boss: Npc, player: Player): Boolean {
        if (boss.coords.level != player.coords.level) return false
        val offsetX = player.coords.x - (boss.coords.x + boss.size / 2)
        val offsetZ = player.coords.z - (boss.coords.z + boss.size / 2)
        return (0 until boss.size).any { index ->
            val origin = if (abs(offsetX) > abs(offsetZ))
                boss.coords.translate(if (offsetX < 0) 0 else boss.size - 1, index)
            else boss.coords.translate(index, if (offsetZ < 0) 0 else boss.size - 1)
            rays.hasLineOfSight(origin, player.coords)
        }
    }

    private fun flies(run: TheatreRun) {
        val boss = checkNotNull(run.boss)
        val players = manager.combatants(run)
        val direct = players.filter { visible(boss, it) }
        for (player in direct) {
            fliesHit(run, player, boss.coords.translate(boss.size / 2, boss.size / 2), "spotanim.tob_bloat_flies_large")
        }
        for (player in players.filter { it !in direct }) {
            val source = direct.firstOrNull { rays.hasLineOfSight(it.coords, player.coords) } ?: continue
            fliesHit(run, player, source.coords, "spotanim.tob_bloat_flies_small")
        }
    }

    private fun fliesHit(run: TheatreRun, player: Player, source: CoordGrid, graphic: String) {
        player.soundSynth(run.roomrow.sound)
        deps.bossProjectile(graphic.asRSCM(RSCMType.SPOTANIM), source, player.coords,
            40, 20, 0, 30, 0, homing = player)
        val damage = TheatreMechanics.bloatFlyDamage(deps.random.of(rules.bloatFliesMin, rules.bloatFliesMax),
            player.vars["varbit.prayer_protectfrommissiles"] != 0)
        manager.damage(run, player, damage)
    }

    private fun flesh(run: TheatreRun) {
        val boss = checkNotNull(run.boss)
        val candidates = fleshTiles[run].orEmpty().filter { it !in previousFlesh[run].orEmpty() }.toMutableList()
        val points = buildSet {
            repeat(rules.bloatFleshCount.coerceAtMost(candidates.size)) {
                add(candidates.removeAt(deps.random.of(candidates.size)))
            }
        }
        previousFlesh[run] = points
        for (point in points) {
            manager.gfx(run, point, rules.bloatFleshGraphics[deps.random.of(rules.bloatFleshGraphics.size)])
            val actual = manager.coord(run, point)
            manager.schedule(run, rules.bloatFleshDelayTicks) {
                if (run.boss !== boss || boss.hitpoints <= 0) return@schedule
                manager.gfx(run, point, "spotanim.tob_bloat_blood_splat")
                deps.worldRepo.soundArea(actual, rules.bloatFleshSound, radius = 3)
                for (player in manager.combatants(run).filter { it.coords == actual }) {
                    manager.damage(run, player, deps.random.of(rules.bloatFleshMin, rules.bloatFleshMax))
                    Stun.apply(player, rules.bloatFleshStunTicks)
                    player.spotanim("spotanim.tob_bloat_stunned", height = 100)
                }
            }
        }
    }
}
