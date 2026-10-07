package org.rsmod.content.raids.theatreofblood

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.HitmarkTypeGroup
import dev.openrune.types.MoveRestrict
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.max
import org.rsmod.api.bosses.runtime.bossProjectile
import org.rsmod.api.mechanics.stun.Stun
import org.rsmod.api.npc.heal
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.route.StepFactory
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.map.CoordGrid

@Singleton
internal class TheatreEncounters @Inject constructor(
    private val manager: TheatreManager,
    private val steps: StepFactory,
    private val bloat: TheatreBloat,
) {
    private val deps get() = manager.deps
    private val rules get() = TheatreData.mechanics
    private val bloodPools = IdentityHashMap<TheatreRun, MutableMap<CoordGrid, Int>>()
    private val bloodSplats = IdentityHashMap<TheatreRun, MutableMap<CoordGrid, Int>>()
    private val floors = IdentityHashMap<TheatreRun, MutableMap<CoordGrid, LocInfo>>()
    private val mazes = IdentityHashMap<TheatreRun, List<CoordGrid>>()

    fun start(run: TheatreRun) {
        clear(run)
        val row = run.roomrow
        val boss = manager.spawn(run, row.npc, row.center, TheatreMechanics.normalHitpoints(row.health, run.teamSize))
        run.boss = boss
        boss.vars["varn.theatre_next_attack"] = row.attackTicks
        when (run.room) {
            0 -> boss.movementLocked = true
            1 -> bloat.start(run)
            4 -> {
                transmog(boss, "npc.tob_xarpus_feeding")
                boss.anim("seq.tob_xarpus_absorb")
            }
            5 -> {
                for (point in rules.verzikPillars) {
                    val pillar = manager.spawn(run, "npc.verzik_pillar_npc", point, rules.verzikPillarHealth)
                    pillar.hideAllOps()
                }
                boss.anim("seq.verzik_phase1_idle")
            }
        }
    }

    fun tick(run: TheatreRun) {
        when (run.room) {
            0 -> maiden(run)
            1 -> bloat.tick(run)
            3 -> sotetseg(run)
            4 -> xarpus(run)
            5 -> verzik(run)
        }
    }

    fun beforeImpact(run: TheatreRun, npc: Npc, hit: Hit, amount: Int): Int {
        if (!manager.active(run)) return 0
        if (hit.isFromPlayer && hit.resolvePlayerSource(deps.playerList) !in manager.combatants(run)) return 0
        if (run.room == 0 && npc !== run.boss && npc.vars["varn.theatre_phase"] == 2) return 0
        if (npc !== run.boss) return amount
        val attacker = if (hit.isFromPlayer) hit.resolvePlayerSource(deps.playerList) else null
        var damage = when (run.room) {
            1 -> TheatreMechanics.bloatIncomingDamage(amount, run.phase == 0)
            3 -> if (run.phase == 1) 0 else amount
            4 -> when (run.phase) {
                0 -> 0
                2 -> {
                    val center = manager.coord(run, run.roomrow.center)
                    val quadrant = TheatreQuadrant.entries[npc.vars["varn.theatre_style"]]
                    if (attacker != null && TheatreMechanics.xarpusRetaliates(
                            quadrant, TheatreTile(center.x + npc.size / 2, center.z + npc.size / 2),
                            TheatreTile(attacker.coords.x, attacker.coords.z),
                        )) {
                        manager.damage(run, attacker, deps.random.of(rules.xarpusRetaliationMin, rules.xarpusRetaliationMax))
                        0
                    } else amount
                }
                else -> amount
            }
            5 -> {
                if (run.phase == 0) {
                    val dawnbringer = hit.righthandType()?.id == "obj.verzik_special_weapon".asRSCM(RSCMType.OBJ)
                    if (dawnbringer) amount else TheatreMechanics.verzikFirstPhaseDamage(style(hit.type), amount)
                } else amount
            }
            else -> amount
        }
        if (run.room == 5 && run.phase < 2 && damage >= npc.hitpoints && npc.hitpoints > 0) {
            transitionVerzik(run)
            damage = 0
        }
        return damage
    }

    fun killed(run: TheatreRun, npc: Npc) {
        if (!manager.active(run)) return
        if (npc === run.boss) {
            manager.roomCleared(run)
        } else {
            if (run.room == 0 && npc.isType("npc.maiden_blood_slug")) maidenBloodDeath(run, npc)
            else manager.remove(run, npc)
        }
    }

    fun clear(run: TheatreRun) {
        bloat.clear(run)
        mazes.remove(run)
        bloodPools.remove(run)
        bloodSplats.remove(run)
        for (loc in floors.remove(run)?.values.orEmpty()) deps.locRepo?.del(loc, Int.MAX_VALUE)
    }

    private fun maiden(run: TheatreRun) {
        val boss = checkNotNull(run.boss)
        var mask = boss.vars["varn.theatre_thresholds"]
        for (threshold in TheatreMechanics.maidenThresholds(boss.hitpoints.coerceAtLeast(0), boss.baseHitpointsLvl, mask)) {
            mask = TheatreMechanics.markMaidenThreshold(mask, threshold)
            boss.vars["varn.theatre_thresholds"] = mask
            transmog(boss, "npc.tob_maiden_$threshold")
            boss.anim("seq.maiden_attack_special")
            for (index in 0 until run.teamSize * 2) {
                val point = rules.maidenHealerSpawns[index]
                val healer = manager.spawn(run, "npc.maiden_elemental", point, TheatreMechanics.normalHitpoints(rules.maidenHealerHealth, run.teamSize))
                healer.moveRestrict = MoveRestrict.PassThru
                healer.vars["varn.theatre_phase"] = 1
                val destination = if (point.z > run.roomrow.center.z + boss.size / 2)
                    rules.maidenHealerNorthDestination else rules.maidenHealerDestination
                healer.vars["varn.theatre_healer_destination"] = destination.packed
                routeMaidenHealer(run, healer)
            }
        }
        for (add in run.adds.toList()) {
            if (add === boss || !add.isSlotAssigned || add.hitpoints <= 0) continue
            when {
                add.isType("npc.maiden_elemental") -> maidenHealer(run, boss, add)
                add.isType("npc.maiden_blood_slug") -> maidenBloodSpawn(run, add)
            }
        }
        bloodTick(run)
        val target = manager.combatants(run).minByOrNull { distance(it.coords, boss.coords) } ?: return
        boss.facePathingEntitySquare(target)
        if (!ready(run)) return
        val attacks = ++boss.vars["varn.theatre_attacks"]
        boss.anim(run.roomrow.animation)
        if (attacks % rules.maidenBloodAttackCadence == 0) {
            sound(run, rules.maidenBloodAttackSound)
            val tiles = maidenBloodTargets(run, target.coords)
            for (tile in tiles) {
                val projectile = deps.bossProjectile("spotanim.maiden_blood_proj".asRSCM(RSCMType.SPOTANIM),
                    boss.coords.translate(boss.size / 2, boss.size / 2), tile, 60, 0, 0, 60, 16)
                manager.schedule(run, projectile.serverCycles) {
                    deps.worldRepo.spotanimMap(SpotanimType("spotanim.maiden_lingering_blood".asRSCM(RSCMType.SPOTANIM)), tile)
                    val sequence = checkNotNull(ServerCacheManager.getAnim("seq.tob_blood_splat".asRSCM(RSCMType.SEQ)))
                    bloodSplats.getOrPut(run) { mutableMapOf() }[tile] = run.tick + sequence.tickDuration
                }
            }
        } else {
            sound(run, run.roomrow.sound)
            projectile(boss, target, run.roomrow.projectile)
            manager.schedule(run, rules.maidenProjectileDelayTicks) {
                val protected = target.vars["varbit.prayer_protectfrommagic"] != 0
                manager.damage(run, target, deps.random.of(0, TheatreMechanics.maidenMagicDamage(boss.vars["varn.theatre_hp_boost"], protected)))
            }
        }
        if (attacks % rules.maidenBloodSpawnCadence == 0) {
            val slug = manager.spawn(run, "npc.maiden_blood_slug", randomTile(run), rules.maidenBloodSpawnHealth)
            slug.moveRestrict = MoveRestrict.PassThru
            slug.vars["varn.theatre_birth_tick"] = run.tick
        }
    }

    private fun maidenBloodTargets(run: TheatreRun, target: CoordGrid): List<CoordGrid> {
        val tiles = manager.combatants(run).map { it.coords }.toMutableList()
        val range = rules.maidenBloodScatterRange
        val candidates = buildList {
            for (dx in -range..range) for (dz in -range..range) {
                val tile = target.translate(dx, dz)
                if (tile !in tiles && run.roomrow.contains(manager.source(run, tile)) &&
                    !deps.collision.isWalkBlocked(tile)) add(tile)
            }
        }.toMutableList()
        repeat(rules.maidenBloodExtraSplats.coerceAtMost(candidates.size)) {
            tiles += candidates.removeAt(deps.random.of(candidates.size))
        }
        return tiles
    }

    private fun maidenHealer(run: TheatreRun, boss: Npc, healer: Npc) {
        if (healer.vars["varn.theatre_phase"] != 1) return
        val destination = manager.coord(run, maidenHealerDestination(healer))
        if (healer.coords != destination) {
            if (healer.routeDestination.isEmpty()) routeMaidenHealer(run, healer)
            return
        }
        boss.heal(TheatreMechanics.maidenHealing(healer.hitpoints), showHitsplat = true,
            hitmarkGroup = HitmarkTypeGroup("hitmark.theatre_heal"))
        boss.vars["varn.theatre_hp_boost"]++
        healer.vars["varn.theatre_phase"] = 2
        healer.movementLocked = true
        healer.abortRoute()
        healer.hideAllOps()
        healer.anim("seq.elemental_death")
        manager.schedule(run, rules.maidenHealerBurstTicks) { manager.remove(run, healer) }
    }

    private fun routeMaidenHealer(run: TheatreRun, healer: Npc) {
        val source = manager.source(run, healer.coords)
        val approach = CoordGrid(minOf(source.x, run.roomrow.entry.x), source.z, source.level)
        healer.walk(listOf(
            manager.coord(run, approach),
            manager.coord(run, maidenHealerDestination(healer)),
        ).filter { it != healer.coords })
    }

    private fun maidenBloodSpawn(run: TheatreRun, spawn: Npc) {
        if (spawn.vars["varn.theatre_phase"] == 2) return
        if (run.tick % rules.maidenBloodTrailTicks == 0) blood(run, spawn.coords)
        if (run.tick - spawn.vars["varn.theatre_birth_tick"] >= rules.maidenBloodSpawnLifetimeTicks) {
            maidenBloodDeath(run, spawn)
            return
        }
        if (spawn.routeDestination.isEmpty() || spawn.coords == spawn.routeDestination.peekLast() ||
            run.tick >= spawn.vars["varn.theatre_walk_until"]) {
            wanderBloodSpawn(run, spawn)
            spawn.vars["varn.theatre_walk_until"] = run.tick + rules.maidenBloodWanderTicks
        }
    }

    private fun maidenHealerDestination(healer: Npc): CoordGrid =
        healer.vars["varn.theatre_healer_destination"].let {
            if (it == 0) rules.maidenHealerDestination else CoordGrid(it)
        }

    private fun maidenBloodDeath(run: TheatreRun, spawn: Npc) {
        if (spawn.vars["varn.theatre_phase"] == 2) return
        spawn.vars["varn.theatre_phase"] = 2
        spawn.movementLocked = true
        spawn.abortRoute()
        spawn.hideAllOps()
        val death = "seq.tob_blood_spawn_death"
        spawn.anim(death)
        val sequence = checkNotNull(ServerCacheManager.getAnim(death.asRSCM(RSCMType.SEQ)))
        manager.schedule(run, sequence.tickDuration) { manager.remove(run, spawn) }
    }

    private fun wanderBloodSpawn(run: TheatreRun, spawn: Npc) {
        val collision = checkNotNull(spawn.collisionStrategy)
        repeat(20) {
            val destination = manager.coord(run, randomTile(run))
            if (destination != spawn.coords) {
                val path = steps.createPath(spawn.coords, destination, spawn.size,
                    collision = collision)
                if (path.isNotEmpty() && path.last() == destination) {
                    spawn.walk(path)
                    return
                }
            }
        }
        val neighbours = buildList {
            for (dx in -1..1) for (dz in -1..1) {
                if (dx == 0 && dz == 0) continue
                val tile = spawn.coords.translate(dx, dz)
                if (run.roomrow.contains(manager.source(run, tile)) &&
                    steps.validated(spawn.coords, tile, spawn.size, collision = collision) == tile) add(tile)
            }
        }
        if (neighbours.isNotEmpty()) spawn.walk(neighbours[deps.random.of(neighbours.size)])
    }

    private fun blood(run: TheatreRun, actual: CoordGrid) {
        bloodPools.getOrPut(run) { mutableMapOf() }[actual] = run.tick + rules.maidenBloodLifetimeTicks
        floor(run, manager.source(run, actual), "loc.tob_maiden_blood")
    }

    private fun bloodTick(run: TheatreRun) {
        val pools = bloodPools[run].orEmpty()
        val splats = bloodSplats[run]
        for ((point, expires) in pools.toMap()) {
            if (run.tick >= expires) {
                bloodPools[run]?.remove(point)
                removeFloor(run, point)
            }
        }
        splats?.entries?.removeIf { run.tick >= it.value }
        if (run.tick % rules.maidenBloodDamageTicks != 0) return
        for (player in manager.combatants(run)) {
            if (player.coords in pools || splats?.containsKey(player.coords) == true) {
                manager.damage(run, player, deps.random.of(rules.maidenBloodDamageMin, rules.maidenBloodDamageMax))
            }
        }
    }

    fun impact(run: TheatreRun, npc: Npc, hit: Hit) {
        if (run.room == 1) bloat.impact(run, npc, hit)
    }

    private fun sotetseg(run: TheatreRun) {
        val boss = checkNotNull(run.boss)
        if (run.phase == 1) {
            mazeTick(run)
            return
        }
        val thresholds = TheatreCombatPatterns.sotetsegMazeThresholds(boss.hitpoints.coerceAtLeast(0), boss.baseHitpointsLvl, boss.vars["varn.theatre_thresholds"])
        if (thresholds.isNotEmpty()) {
            val threshold = thresholds.first()
            boss.vars["varn.theatre_thresholds"] = boss.vars["varn.theatre_thresholds"] or if (threshold == 666) 1 else 2
            startMaze(run)
            return
        }
        if (!ready(run)) return
        val target = manager.combatants(run).minByOrNull { distance(it.coords, boss.coords) } ?: return
        val attack = ++boss.vars["varn.theatre_attacks"]
        if (attack % rules.sotetsegSharedBallAttacks == 0) {
            val tile = target.coords
            projectile(boss, target, "spotanim.tob_sotetseg_sharedattack", travel = 150)
            boss.anim("seq.tob_sotetseg_shared_projectile")
            manager.schedule(run, rules.sotetsegSharedBallDelayTicks) {
                val soakers = manager.combatants(run).filter { distance(it.coords, tile) <= 1 }
                if (soakers.isNotEmpty()) {
                    val total = TheatreMechanics.normalHitpoints(rules.sotetsegSharedBallDamage, run.teamSize)
                    val damage = TheatreMechanics.sharedBallDamage(total, soakers.size, 0)
                    soakers.forEach { manager.damage(run, it, damage) }
                }
            }
        } else {
            boss.anim(run.roomrow.animation)
            val hitType = if (attack % 2 == 0) HitType.Magic else HitType.Ranged
            val symbol = if (hitType == HitType.Magic) "spotanim.tob_sotetseg_maging" else "spotanim.tob_sotetseg_ranging"
            val chain = listOf(target) + manager.combatants(run).filter { it !== target }.take(2)
            for ((index, player) in chain.withIndex()) {
                projectile(boss, player, symbol)
                manager.schedule(run, rules.sotetsegProjectileDelayTicks + index) { manager.damage(run, player, deps.random.of(0, rules.sotetsegMaxHit), hitType) }
            }
        }
        sound(run, run.roomrow.sound)
    }

    private fun startMaze(run: TheatreRun) {
        run.phase = 1
        val boss = checkNotNull(run.boss)
        boss.anim("seq.tob_sotetseg_shadow_portal")
        val bounds = rules.sotetsegMazeBounds
        val entry = rules.sotetsegMazeEntry
        val exit = rules.sotetsegMazeExit
        val bendZ = deps.random.of(bounds[1] + 3, bounds[3] - 3)
        val laneX = deps.random.of(bounds[0], bounds[2])
        val points = buildList {
            var current = entry
            add(current)
            fun walkTo(destination: CoordGrid) {
                while (current != destination) {
                    current = current.translate((destination.x - current.x).coerceIn(-1, 1),
                        if (current.x == destination.x) (destination.z - current.z).coerceIn(-1, 1) else 0)
                    add(current)
                }
            }
            walkTo(CoordGrid(entry.x, bendZ, entry.level))
            walkTo(CoordGrid(laneX, bendZ, entry.level))
            walkTo(CoordGrid(laneX, exit.z, entry.level))
            walkTo(exit)
        }
        mazes[run] = points.map { manager.coord(run, it) }
        for (point in points) floor(run, point, "loc.tob_sotetseg_lighttile")
        for (player in manager.combatants(run)) {
            player.clearInteraction()
            player.abortRoute()
            PathingEntityCommon.telejump(player, deps.collision, manager.coord(run, points.first()))
            player.mes("Follow the illuminated path through the maze.")
        }
        boss.vars["varn.theatre_sleep_until"] = run.tick + rules.sotetsegMazeTimeoutTicks
    }

    private fun mazeTick(run: TheatreRun) {
        val path = mazes[run] ?: return
        if (manager.combatants(run).all { distance(it.coords, path.last()) <= 1 }) {
            endMaze(run)
            return
        }
        if (run.tick % rules.sotetsegMazeDamageTicks == 0) {
            for (player in manager.combatants(run)) if (player.coords !in path) manager.damage(run, player, deps.random.of(rules.sotetsegMazeMin, rules.sotetsegMazeMax))
        }
        if (run.tick >= checkNotNull(run.boss).vars["varn.theatre_sleep_until"]) {
            manager.combatants(run).forEach { manager.damage(run, it, rules.sotetsegMazeTimeoutDamage) }
            endMaze(run)
        }
    }

    private fun endMaze(run: TheatreRun) {
        clear(run)
        run.phase = 0
        for (player in manager.combatants(run)) {
            PathingEntityCommon.telejump(player, deps.collision, manager.coord(run, run.roomrow.entry))
        }
        run.boss?.vars?.set("varn.theatre_next_attack", run.tick + run.roomrow.attackTicks)
    }

    private fun xarpus(run: TheatreRun) {
        val boss = checkNotNull(run.boss)
        if (run.phase == 0) {
            if (run.tick % rules.xarpusExhumeIntervalTicks == 1) {
                val source = randomTile(run)
                val actual = manager.coord(run, source)
                floor(run, source, "loc.tob_xarpus_exhumed")
                sound(run, "synth.TOB_XARPUS_EXHUMES_OPEN")
                manager.schedule(run, rules.xarpusExhumeDurationTicks) {
                    if (manager.combatants(run).none { it.coords == actual }) {
                        boss.hitpoints = (boss.hitpoints + rules.xarpusExhumeHeal).coerceAtMost(boss.baseHitpointsLvl)
                    }
                    removeFloor(run, actual)
                }
            }
            if (run.tick >= rules.xarpusExhumePhaseTicks) {
                run.phase = 1
                transmog(boss, "npc.tob_xarpus_combat")
                boss.anim("seq.tob_xarpus_fly_up")
                boss.vars["varn.theatre_hp_boost"] = boss.hitpoints
            }
            return
        }
        if (run.phase == 1 && TheatreCombatPatterns.xarpusStareReady(boss.hitpoints, boss.vars["varn.theatre_hp_boost"])) {
            run.phase = 2
            boss.say("SCREECH!")
        }
        if (run.phase == 2) {
            if (run.tick % rules.xarpusStareTicks == 0) {
                boss.vars["varn.theatre_style"] = deps.random.of(4)
                val target = manager.combatants(run).firstOrNull { player ->
                    val center = boss.coords.translate(boss.size / 2, boss.size / 2)
                    TheatreMechanics.xarpusQuadrant(TheatreTile(center.x, center.z), TheatreTile(player.coords.x, player.coords.z))?.ordinal == boss.vars["varn.theatre_style"]
                }
                if (target != null) boss.faceSquare(target.coords)
            }
        } else if (ready(run)) {
            val target = randomPlayer(run) ?: return
            val tile = target.coords
            boss.anim(run.roomrow.animation)
            sound(run, run.roomrow.sound)
            projectile(boss, target, run.roomrow.projectile)
            manager.schedule(run, rules.xarpusPoisonDelayTicks) { floor(run, manager.source(run, tile), "loc.tob_xarpus_acidpool") }
        }
        if (run.tick % rules.xarpusPoisonDamageTicks == 0) {
            for (player in manager.combatants(run)) if (floors[run]?.containsKey(player.coords) == true) manager.damage(run, player, deps.random.of(rules.xarpusPoisonMin, rules.xarpusPoisonMax))
        }
    }

    private fun verzik(run: TheatreRun) {
        val boss = checkNotNull(run.boss)
        verzikAdds(run)
        if (!ready(run)) return
        val target = randomPlayer(run) ?: return
        when (run.phase) {
            0 -> {
                boss.anim("seq.verzik_phase1_attack_magic")
                sound(run, "synth.TOB_VERZIK_HUMAN_MAGIC_ATTACK")
                for (player in manager.combatants(run)) {
                    projectile(boss, player, "spotanim.verzik_phase1_projectile")
                    manager.schedule(run, rules.verzikProjectileDelayTicks) {
                        val hidden = run.adds.filter { it.isType("npc.verzik_pillar_npc") }.any { between(boss.coords, it.coords, player.coords) }
                        if (!hidden) manager.damage(run, player, deps.random.of(0, rules.verzikPhase1MaxHit), HitType.Magic, prayerPenetration = 50)
                    }
                }
                if (++boss.vars["varn.theatre_attacks"] % rules.verzikPillarCollapseAttacks == 0) {
                    val pillar = run.adds.firstOrNull { it.isType("npc.verzik_pillar_npc") }
                    if (pillar != null) {
                        pillar.anim("seq.verzik_pillar_collapse")
                        manager.schedule(run, rules.verzikPillarCollapseDelayTicks) { manager.remove(run, pillar) }
                    }
                }
            }
            1 -> {
                boss.anim("seq.verzik_phase2_attack_magic")
                sound(run, "synth.TOB_VERZIK_VAMPIRE_MAGIC_ATTACK")
                for (player in manager.combatants(run)) {
                    val tile = player.coords
                    projectile(boss, player, "spotanim.verzik_phase2_ranged")
                    manager.schedule(run, rules.verzikProjectileDelayTicks) {
                        if (player.coords == tile) manager.damage(run, player, deps.random.of(0, rules.verzikPhase2MaxHit), HitType.Ranged, prayerPenetration = 50)
                    }
                }
                val attacks = ++boss.vars["varn.theatre_attacks"]
                if (attacks % rules.verzikPhase2NylocasAttacks == 0) for (player in manager.combatants(run)) spawnVerzikAdd(run, player)
                if (TheatreMechanics.verzikBloodPhase(boss.hitpoints, boss.baseHitpointsLvl) && attacks % rules.verzikBloodNylocasAttacks == 0) {
                    for (player in manager.combatants(run)) {
                        val add = manager.spawn(run, "npc.tob_verzik_phase2_bloodnylocas", manager.source(run, player.coords).translate(2, 0), rules.verzikBloodNylocasHealth)
                        add.moveRestrict = MoveRestrict.Normal
                        add.walk(boss.coords.translate(3, 0))
                    }
                }
                for (player in manager.combatants(run).filter { distance(it.coords, boss.coords) <= boss.size }) knockback(run, player, boss)
            }
            2 -> verzikSpider(run, target)
        }
    }

    private fun verzikSpider(run: TheatreRun, target: Player) {
        val boss = checkNotNull(run.boss)
        val enraged = TheatreMechanics.verzikEnraged(boss.hitpoints, boss.baseHitpointsLvl)
        boss.vars["varn.theatre_next_attack"] = run.tick + TheatreCombatPatterns.verzikAttackTicks(enraged)
        val attacks = boss.vars["varn.theatre_attacks"]
        val specialIndex = boss.vars["varn.theatre_wave"]
        val special = TheatreCombatPatterns.verzikSpecial(attacks, specialIndex)
        if (special != null) {
            boss.vars["varn.theatre_attacks"] = 0
            boss.vars["varn.theatre_wave"] = specialIndex + 1
            when (special) {
                VerzikSpecial.NYLOCAS -> manager.combatants(run).forEach { spawnVerzikAdd(run, it) }
                VerzikSpecial.WEBS -> {
                    boss.anim("seq.verzik_phase3_attack_webspin")
                    for (player in manager.combatants(run)) {
                        val tile = player.coords
                        projectile(boss, player, "spotanim.verzik_p3_web_proj")
                        manager.schedule(run, rules.verzikProjectileDelayTicks) { floor(run, manager.source(run, tile), "loc.tob_nylocas_death_web") }
                    }
                }
                VerzikSpecial.POWER_BLAST -> {
                    boss.anim("seq.verzik_phase3_attack_powerblast")
                    val safe = manager.combatants(run).map { player ->
                        val source = manager.source(run, player.coords).translate(deps.random.of(-2, 2), deps.random.of(-2, 2))
                        manager.gfx(run, source, "spotanim.verzik_powerblast_safezone")
                        manager.coord(run, source)
                    }
                    manager.schedule(run, rules.verzikPowerBlastDelayTicks) {
                        for (player in manager.combatants(run)) if (safe.none { distance(player.coords, it) <= 1 }) manager.damage(run, player, rules.verzikPowerBlastDamage)
                    }
                }
                VerzikSpecial.GREEN_BALL -> {
                    projectile(boss, target, "spotanim.verzik_acidbomb_projanim", travel = 120)
                    manager.schedule(run, rules.verzikGreenBallDelayTicks) {
                        val receiver = manager.combatants(run).firstOrNull { it !== target && distance(it.coords, target.coords) <= 1 } ?: target
                        manager.damage(run, receiver, TheatreCombatPatterns.verzikGreenBallDamage(receiver.statBase("stat.hitpoints")))
                    }
                }
            }
        } else {
            boss.vars["varn.theatre_attacks"] = attacks + 1
            val close = distance(boss.coords, target.coords) <= boss.size
            val type = if (close) HitType.Melee else if (deps.random.of(2) == 0) HitType.Ranged else HitType.Magic
            val seq = when (type) {
                HitType.Melee -> "seq.verzik_phase3_attack_melee"
                HitType.Ranged -> "seq.verzik_phase3_attack_ranged"
                else -> "seq.verzik_phase3_attack_magic"
            }
            boss.anim(seq)
            if (!close) {
                boss.walk(target.coords)
                projectile(boss, target, if (type == HitType.Ranged) "spotanim.verzik_phase3_rangeproj" else "spotanim.verzik_phase3_mageproj")
            }
            manager.schedule(run, rules.verzikProjectileDelayTicks) { manager.damage(run, target, deps.random.of(0, if (close) rules.verzikPhase3MeleeMaxHit else rules.verzikPhase3RangedMagicMaxHit), type) }
        }
    }

    private fun verzikAdds(run: TheatreRun) {
        val boss = checkNotNull(run.boss)
        for (add in run.adds.toList().filter { it.isType("npc.tob_verzik_phase2_bloodnylocas") && it.hitpoints > 0 }) {
            if (distance(add.coords, boss.coords) <= 4) {
                boss.hitpoints = (boss.hitpoints + add.hitpoints).coerceAtMost(boss.baseHitpointsLvl)
                manager.remove(run, add)
            }
        }
        if (run.phase != 2) return
        if (TheatreMechanics.verzikEnraged(boss.hitpoints, boss.baseHitpointsLvl)) {
            for (player in manager.combatants(run)) {
                val index = manager.memberIndex(run, player)
                val existing = run.adds.any { it.isType("npc.tob_verzik_creeper") && it.vars["varn.theatre_target_member"] == index }
                if (existing || run.tick < player.vars["varp.theatre_tornado_next"]) continue
                val tornado = manager.spawn(run, "npc.tob_verzik_creeper", run.roomrow.entry, 1)
                tornado.vars["varn.theatre_target_member"] = index
                tornado.moveRestrict = MoveRestrict.Normal
                tornado.hideAllOps()
            }
        }
        for (tornado in run.adds.toList().filter { it.isType("npc.tob_verzik_creeper") }) {
            val victim = run.members.getOrNull(tornado.vars["varn.theatre_target_member"])
            if (victim == null || victim !in manager.combatants(run)) {
                manager.remove(run, tornado)
                continue
            }
            tornado.walk(victim.coords)
            if (distance(tornado.coords, victim.coords) <= 1) {
                val damage = TheatreCombatPatterns.verzikTornadoDamage(victim.hitpoints)
                manager.damage(run, victim, damage)
                boss.hitpoints = (boss.hitpoints + TheatreCombatPatterns.verzikTornadoHealing(damage)).coerceAtMost(boss.baseHitpointsLvl)
                manager.remove(run, tornado)
                TheatreManager.set(victim, "varp.theatre_tornado_next", run.tick + rules.verzikTornadoRespawnTicks)
            }
        }
    }

    private fun spawnVerzikAdd(run: TheatreRun, player: Player) {
        val symbol = listOf("npc.verzik_nylocas_melee", "npc.verzik_nylocas_ranged", "npc.verzik_nylocas_magic")[deps.random.of(3)]
        val add = manager.spawn(run, symbol, manager.source(run, player.coords).translate(3, 0), rules.verzikNylocasHealth)
        add.moveRestrict = MoveRestrict.Normal
        add.walk(player.coords)
        manager.schedule(run, rules.verzikNylocasLifetimeTicks) {
            if (!add.isSlotAssigned || add.hitpoints <= 0) return@schedule
            for (nearby in manager.combatants(run).filter { distance(it.coords, add.coords) <= rules.verzikNylocasBlastRange }) manager.damage(run, nearby, deps.random.of(rules.verzikNylocasBlastMin, rules.verzikNylocasBlastMax))
            manager.remove(run, add)
        }
    }

    private fun transitionVerzik(run: TheatreRun) {
        val boss = checkNotNull(run.boss)
        if (run.phase == 0) manager.endDawnbringerPhase(run)
        run.phase++
        val hp = if (run.phase == 1) TheatreData.rules.verzikPhase2Health else TheatreData.rules.verzikPhase3Health
        val type = if (run.phase == 1) "npc.verzik_phase2" else "npc.verzik_phase3"
        transmog(boss, type)
        if (run.phase == 2) boss.moveRestrict = MoveRestrict.Normal
        boss.baseHitpointsLvl = TheatreMechanics.normalHitpoints(hp, run.teamSize)
        boss.hitpoints = boss.baseHitpointsLvl
        boss.vars["varn.theatre_attacks"] = 0
        boss.vars["varn.theatre_next_attack"] = run.tick + rules.verzikTransitionTicks
        boss.anim(if (run.phase == 1) "seq.verzik_phase2_spawn" else "seq.verzik_phase3_spawn")
    }

    private fun knockback(run: TheatreRun, player: Player, boss: Npc) {
        val from = player.coords
        val dx = if (from.x < boss.coords.x + boss.size / 2) -1 else 1
        val destination = from.translate(dx * rules.verzikKnockbackTiles, 0)
        if (deps.collision.isWalkBlocked(destination)) return
        PathingEntityCommon.exactMove(player, from, destination, 0, rules.verzikKnockbackCycles, player.faceAngle, deps.collision)
        player.anim("seq.human_stunned")
        Stun.apply(player, rules.verzikKnockbackStunTicks)
        manager.damage(run, player, deps.random.of(rules.verzikKnockbackMin, rules.verzikKnockbackMax))
    }

    private fun floor(run: TheatreRun, source: CoordGrid, symbol: String) {
        val coord = manager.coord(run, source)
        if (floors[run]?.containsKey(coord) == true) return
        val shape = if (symbol == "loc.tob_sotetseg_lighttile") LocShape.GroundDecor else LocShape.CentrepieceStraight
        val loc = deps.locRepo?.add(coord, symbol, Int.MAX_VALUE, LocAngle.South, shape) ?: return
        floors.getOrPut(run) { mutableMapOf() }[coord] = loc
    }

    private fun removeFloor(run: TheatreRun, actual: CoordGrid) {
        floors[run]?.remove(actual)?.let { deps.locRepo?.del(it, Int.MAX_VALUE) }
    }

    private fun ready(run: TheatreRun): Boolean {
        val boss = run.boss ?: return false
        if (run.tick < boss.vars["varn.theatre_next_attack"]) return false
        boss.vars["varn.theatre_next_attack"] = run.tick + run.roomrow.attackTicks
        return true
    }

    private fun randomTile(run: TheatreRun): CoordGrid {
        val bounds = run.roomrow.bounds
        repeat(20) {
            val candidate = CoordGrid(deps.random.of(bounds[0] + 1, bounds[2] - 1), deps.random.of(bounds[1] + 1, bounds[3] - 1), bounds[4])
            if (!deps.collision.isWalkBlocked(manager.coord(run, candidate))) return candidate
        }
        return run.roomrow.entry
    }

    private fun randomPlayer(run: TheatreRun): Player? = manager.combatants(run).let { players ->
        if (players.isEmpty()) null else players[deps.random.of(players.size)]
    }

    private fun projectile(npc: Npc, player: Player, symbol: String, travel: Int = 60) {
        deps.bossProjectile(symbol.asRSCM(RSCMType.SPOTANIM), npc.coords.translate(npc.size / 2, npc.size / 2), player.coords, 60, 20, 0, travel, 16, homing = player)
    }

    private fun sound(run: TheatreRun, symbol: String) = deps.worldRepo.soundArea(checkNotNull(run.boss), symbol, radius = 15)

    private fun transmog(npc: Npc, symbol: String) {
        val type = checkNotNull(ServerCacheManager.getNpc(symbol.asRSCM(RSCMType.NPC)))
        npc.transmog(type, Int.MAX_VALUE)
    }

    private fun between(a: CoordGrid, pillar: CoordGrid, player: CoordGrid): Boolean {
        val dx = player.x - a.x
        val dz = player.z - a.z
        val cross = abs((pillar.x - a.x) * dz - (pillar.z - a.z) * dx)
        return cross <= max(abs(dx), abs(dz)) * 2 &&
            (pillar.x - a.x) * dx + (pillar.z - a.z) * dz in 1 until dx * dx + dz * dz
    }

    private fun distance(a: CoordGrid, b: CoordGrid): Int = max(abs(a.x - b.x), abs(a.z - b.z))

    private fun style(type: HitType) = when (type) {
        HitType.Melee -> TheatreAttackStyle.MELEE
        HitType.Ranged -> TheatreAttackStyle.RANGED
        else -> TheatreAttackStyle.MAGIC
    }
}
