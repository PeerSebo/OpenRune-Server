package org.rsmod.content.raids.theatreofblood

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.HitmarkTypeGroup
import dev.openrune.types.MoveRestrict
import jakarta.inject.Inject
import jakarta.inject.Provider
import jakarta.inject.Singleton
import org.rsmod.api.bosses.runtime.bossProjectile
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.npc.heal
import org.rsmod.api.player.clearInteractionRoute
import org.rsmod.api.route.BoundValidator
import org.rsmod.api.route.RayCastValidator
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.game.interact.InteractionNpc
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.map.CoordGrid

@Singleton
internal class TheatreNylocas @Inject constructor(
    private val managers: Provider<TheatreManager>,
    private val rays: RayCastValidator,
    private val bounds: BoundValidator,
) {
    private val manager get() = managers.get()
    private val rows by lazy { TheatreData.nylocasWaves.toSortedMap().values.flatten() }

    fun start(run: TheatreRun) {
        run.controller.vars[WAVE] = 0
        run.controller.vars[NEXT_WAVE] = TheatreData.rules.nylocasFirstWaveTicks
        run.controller.vars[BOSS_ACTIVE] = 0
        run.controller.vars[BOSS_SPAWN] = 0
        TheatreData.rules.nylocasPillars.forEachIndexed { index, source ->
            val pillar = manager.spawn(
                run,
                "npc.tob_nylocas_support",
                source,
                TheatreData.rules.nylocasPillarHealth,
            )
            pillar.vars[PILLAR] = index + 1
            pillar.movementLocked = true
            pillar.ignoreCombatInteractions = true
        }
    }

    fun tick(run: TheatreRun) {
        if (!manager.active(run)) return
        if (run.controller.vars[BOSS_ACTIVE] != 0) {
            tickBoss(run)
            return
        }
        for (npc in smallNylocas(run).toList()) {
            if (!manager.active(run)) return
            tickSmall(run, npc)
        }
        if (!manager.active(run)) return
        val wave = run.controller.vars[WAVE]
        val active = smallNylocas(run).size
        val pillars = pillars(run)
        if (pillars.isEmpty()) {
            manager.fail(run)
            return
        }
        if (TheatreCombatPatterns.nyloBossReady(wave, active, pillars.size)) {
            if (run.controller.vars[BOSS_SPAWN] == 0) {
                val rules = TheatreData.rules
                val cycle = run.tick + Math.floorMod(rules.nylocasFirstWaveTicks - run.tick, rules.nylocasWaveTicks)
                run.controller.vars[BOSS_SPAWN] = cycle +
                    (rules.nylocasBossDelayCycles - 1) * rules.nylocasWaveTicks + rules.nylocasBossSpawnTicks
            }
            if (run.tick >= run.controller.vars[BOSS_SPAWN]) startBoss(run)
            return
        }
        if (wave >= TheatreData.nylocasWaves.size || run.tick < run.controller.vars[NEXT_WAVE]) return
        val rules = TheatreData.rules
        if (Math.floorMod(run.tick - rules.nylocasFirstWaveTicks, rules.nylocasWaveTicks) != 0) return
        run.controller.vars[NEXT_WAVE] = run.tick + rules.nylocasWaveTicks
        if (!TheatreCombatPatterns.nyloWaveReady(
                wave + 1,
                active,
                run.tick,
                run.tick,
            )) return
        for (row in checkNotNull(TheatreData.nylocasWaves[wave + 1])) {
            spawnSmall(run, row)
        }
        run.controller.vars[WAVE] = wave + 1
    }

    fun beforeImpact(run: TheatreRun, npc: Npc, hit: Hit, damage: Int): Int {
        if (!manager.active(run) || npc.vars[DYING] != 0 || npc.hitpoints <= 0) return 0
        if (npc.vars[PILLAR] != 0) return 0
        if (npc === run.boss && run.controller.vars[BOSS_ACTIVE] != 2) return 0
        if (!hit.isFromPlayer) return damage
        val player = hit.resolvePlayerSource(manager.deps.playerList) ?: return 0
        val member = manager.memberIndex(run, player)
        if (member !in 0..4 || player !in manager.combatants(run)) return 0
        val incoming = style(hit.type) ?: return damage
        if (npc === run.boss) {
            val current = TheatreAttackStyle.entries[npc.vars[STYLE]]
            val result = TheatreMechanics.nyloHit(current, incoming, damage)
            if (result.healingToBoss > 0) {
                npc.heal(result.healingToBoss, showHitsplat = true,
                    hitmarkGroup = HitmarkTypeGroup("hitmark.theatre_heal"))
                manager.damage(run, player, result.reflectedDamage, source = npc)
            }
            return result.damageToBoss
        }
        return TheatreCombatPatterns.nyloSmallImpactDamage(
            damage,
            TheatreCombatPatterns.nyloMemberLocked(npc.vars[LOCKED], member),
        )
    }

    fun atLaunch(run: TheatreRun, npc: Npc, hit: HitBuilder) {
        if (npc.vars[DYING] != 0 || npc.hitpoints <= 0) {
            hit.damage = 0
            return
        }
        if (npc === run.boss || !hit.isFromPlayer) return
        if (npc.vars[PILLAR] != 0) {
            hit.damage = 0
            return
        }
        val incoming = style(hit.type) ?: return
        val sourceUid = hit.sourceUid ?: return
        val player = PlayerUid(sourceUid).resolve(manager.deps.playerList) ?: return
        val member = manager.memberIndex(run, player)
        if (member !in 0..4 || player !in manager.combatants(run)) {
            hit.damage = 0
            return
        }
        val current = TheatreAttackStyle.entries[npc.vars[STYLE]]
        if (current != incoming) {
            npc.vars[LOCKED] = TheatreCombatPatterns.nyloWrongStyleMask(npc.vars[LOCKED], member)
        }
        hit.damage = TheatreCombatPatterns.nyloSmallDamage(
            current,
            incoming,
            hit.damage,
            TheatreCombatPatterns.nyloMemberLocked(npc.vars[LOCKED], member),
        )
    }

    fun killed(run: TheatreRun, npc: Npc) {
        if (npc === run.boss) {
            manager.roomCleared(run)
        } else dying(run, npc)
    }

    fun dying(run: TheatreRun, npc: Npc, natural: Boolean = false) {
        if (!manager.active(run) || npc !in run.adds || !npc.isSlotAssigned || npc.vars[DYING] != 0) return
        if (npc.vars[PILLAR] != 0) {
            collapsePillar(run, npc)
            return
        }
        npc.vars[DYING] = if (natural) 2 else 1
        npc.hitpoints = 0
        npc.hideAllOps()
        npc.resetMovement()
        npc.movementLocked = true
        val style = TheatreAttackStyle.entries[npc.vars[STYLE]]
        val suffix = if (natural) "_detonate" else ""
        npc.anim("seq.top_spider_${style.name.lowercase()}_death$suffix")
        val rules = TheatreData.rules
        val duration = when {
            npc.vars[BIG] == 0 -> rules.nylocasSmallDeathTicks
            natural -> rules.nylocasBigDetonateTicks
            else -> rules.nylocasBigDeathTicks
        }
        manager.schedule(run, duration) {
            if (manager.run(npc) !== run || !npc.isSlotAssigned) return@schedule
            val source = manager.source(run, npc.coords)
            val big = npc.vars[BIG] == 1
            if (natural) {
                val maximum = if (big) rules.nylocasBigBlastMax else rules.nylocasSmallBlastMax
                for (player in manager.combatants(run).filter { npc.distanceTo(it) <= 1 }) {
                    manager.damage(run, player, manager.deps.random.of(maximum + 1), source = npc)
                }
            }
            if (!big) manager.gfx(run, source,
                "spotanim.tob_nylocas_death_${style.name.lowercase()}_${if (natural) "detonate" else "standard"}")
            manager.remove(run, npc)
            if (big) repeat(2) { child ->
                val childStyle = manager.deps.random.pick(TheatreAttackStyle.entries)
                spawnSmall(run, childStyle, source.translate(child, 0))
            }
        }
    }

    private fun spawnSmall(run: TheatreRun, row: TheatreNylocasSpawn) {
        val rules = TheatreData.rules
        val position = if (row.big) 0 else row.position
        val source = when (row.lane) {
            "EAST" -> rules.nylocasEast.translate(0, position)
            "WEST" -> rules.nylocasWest.translate(0, position)
            "SOUTH" -> rules.nylocasSouth.translate(position, 0)
            else -> error("Unknown Nylocas lane: ${row.lane}")
        }
        val npc = manager.spawn(
            run,
            smallType(TheatreAttackStyle.valueOf(row.styles.first()), row.big, aggressive = false),
            source,
        )
        scaleHealth(run, npc)
        initializeSmall(run, npc, TheatreAttackStyle.valueOf(row.styles.first()), row.big)
        npc.vars[AGGRESSIVE] = if (row.aggressive) 1 else 0
        npc.vars[SPAWN_ROW] = rows.indexOf(row) + 1
        npc.vars[NEXT_STYLE] = run.tick + rules.nylocasStyleTicks
        npc.vars[STAGING] = manager.coord(run, when (row.lane) {
                "EAST" -> source.translate(-rules.nylocasEntryTiles - (npc.size - 1), 0)
                "WEST" -> source.translate(rules.nylocasEntryTiles, 0)
                else -> source.translate(0, rules.nylocasEntryTiles)
            }).packed
        npc.moveRestrict = MoveRestrict.BlockedNormal
        chooseTarget(run, npc)
    }

    private fun spawnSmall(run: TheatreRun, style: TheatreAttackStyle, source: CoordGrid) {
        val npc = manager.spawn(
            run,
            smallType(style, big = false, aggressive = false),
            source,
        )
        scaleHealth(run, npc)
        initializeSmall(run, npc, style, big = false)
        chooseTarget(run, npc)
    }

    private fun initializeSmall(
        run: TheatreRun,
        npc: Npc,
        style: TheatreAttackStyle,
        big: Boolean,
    ) {
        npc.vars[STYLE] = style.ordinal
        npc.vars[BIG] = if (big) 1 else 0
        npc.vars[BORN] = run.tick
        npc.vars[ATTACK] = run.tick
        npc.ignoreCombatInteractions = true
        npc.moveRestrict = MoveRestrict.PassThru
    }

    private fun scaleHealth(run: TheatreRun, npc: Npc) {
        val health = if (npc.size == 2) TheatreData.rules.nylocasBigHealth[run.teamSize - 1]
            else TheatreData.rules.nylocasSmallHealth[run.teamSize - 1]
        npc.baseHitpointsLvl = health
        npc.hitpoints = health
    }

    private fun chooseTarget(run: TheatreRun, npc: Npc) {
        if (npc.vars[AGGRESSIVE] != 0) {
            val target = manager.deps.random.pickOrNull(manager.combatants(run)) ?: return
            npc.vars[TARGET] = manager.memberIndex(run, target) + 1
        } else {
            val target = pillars(run).minByOrNull { npc.distanceTo(it) } ?: return
            npc.vars[PILLAR] = 0
            npc.vars[TARGET_PILLAR] = target.vars[PILLAR]
        }
    }

    private fun tickSmall(run: TheatreRun, npc: Npc) {
        if (!npc.isSlotAssigned || npc.hitpoints <= 0 || npc.vars[DYING] != 0) return
        val age = run.tick - npc.vars[BORN]
        if (age >= TheatreData.rules.nylocasLifetimeTicks) {
            dying(run, npc, natural = true)
            return
        }
        val row = rows.getOrNull(npc.vars[SPAWN_ROW] - 1)
        if (row != null && run.tick >= npc.vars[NEXT_STYLE]) {
            val step = (age / TheatreData.rules.nylocasStyleTicks)
                .coerceAtMost(row.styles.lastIndex)
            val style = TheatreAttackStyle.valueOf(row.styles[step])
            if (style.ordinal != npc.vars[STYLE]) {
                changeStyle(npc, style, smallType(style, row.big, row.aggressive && npc.vars[STAGING] == 0))
            }
            npc.vars[NEXT_STYLE] = run.tick + TheatreData.rules.nylocasStyleTicks
        }
        val staging = npc.vars[STAGING]
        if (staging != 0) {
            val destination = CoordGrid(staging)
            if (npc.coords != destination) {
                if (npc.routeDestination.isEmpty()) npc.walk(destination)
                return
            }
            npc.vars[STAGING] = 0
            npc.moveRestrict = MoveRestrict.PassThru
            npc.abortRoute()
            if (npc.vars[AGGRESSIVE] != 0) {
                val style = TheatreAttackStyle.entries[npc.vars[STYLE]]
                changeStyle(npc, style, smallType(style, npc.vars[BIG] == 1, aggressive = true))
            }
        }
        if (npc.vars[AGGRESSIVE] != 0) {
            var target = run.members.getOrNull(npc.vars[TARGET] - 1)
            if (target == null || target !in manager.combatants(run)) {
                chooseTarget(run, npc)
                target = run.members.getOrNull(npc.vars[TARGET] - 1) ?: return
            }
            attackPlayer(run, npc, target, boss = false)
        } else {
            var target = pillars(run).firstOrNull { it.vars[PILLAR] == npc.vars[TARGET_PILLAR] }
            if (target == null) {
                chooseTarget(run, npc)
                target = pillars(run).firstOrNull { it.vars[PILLAR] == npc.vars[TARGET_PILLAR] }
                    ?: return
            }
            if (!bounds.touches(npc.avatar, target.avatar)) {
                walkAdjacent(npc, target.coords, target.size)
            } else if (run.tick >= npc.vars[ATTACK]) {
                npc.abortRoute()
                npc.faceNpc(target)
                val style = TheatreAttackStyle.entries[npc.vars[STYLE]]
                npc.anim(if (style == TheatreAttackStyle.MELEE) attackAnimation(style)
                    else "seq.top_spider_${style.name.lowercase()}_meleeattack")
                val damage = if (npc.vars[BIG] == 1) TheatreData.rules.nylocasBigPillarDamage
                    else TheatreData.rules.nylocasSmallPillarDamage
                target.hitpoints = (target.hitpoints - damage).coerceAtLeast(0)
                npc.vars[ATTACK] = run.tick + TheatreData.rules.nylocasSmallAttackTicks
                if (target.hitpoints == 0) collapsePillar(run, target)
            }
        }
    }

    private fun collapsePillar(run: TheatreRun, npc: Npc) {
        if (npc !in run.adds) return
        val index = npc.vars[PILLAR] - 1
        val source = TheatreData.rules.nylocasPillars[index].translate(0, 0, 1)
        val angle = LocAngle[listOf(0, 1, 3, 2)[index]]
        manager.deps.locRepo?.add(
            manager.coord(run, source),
            "loc.tob_nylocas_support_collapsing",
            Int.MAX_VALUE,
            angle,
            LocShape.CentrepieceStraight,
        )
        manager.schedule(run, TheatreData.rules.nylocasPillarCollapseTicks) {
            manager.deps.locRepo?.add(
                manager.coord(run, source),
                "loc.tob_nylocas_support_dead",
                Int.MAX_VALUE,
                angle,
                LocShape.CentrepieceStraight,
            )
        }
        for (player in manager.combatants(run)) {
            manager.damage(
                run,
                player,
                manager.deps.random.of(TheatreData.rules.nylocasPillarCollapseMax + 1),
                source = npc,
            )
        }
        manager.remove(run, npc)
        if (pillars(run).isEmpty()) manager.fail(run)
    }

    private fun startBoss(run: TheatreRun) {
        run.controller.vars[BOSS_ACTIVE] = 1
        val boss = manager.spawn(
            run,
            "npc.nylocas_boss_spawning",
            run.roomrow.center,
            TheatreMechanics.normalHitpoints(run.roomrow.health, run.teamSize),
        )
        run.boss = boss
        boss.vars[STYLE] = TheatreAttackStyle.MELEE.ordinal
        boss.ignoreCombatInteractions = true
        boss.anim("seq.top_spider_melee_spawn_noloop")
        manager.schedule(run, TheatreData.rules.nylocasBossIntroTicks) {
            if (run.boss !== boss) return@schedule
            changeStyle(boss, TheatreAttackStyle.MELEE, "npc.nylocas_boss_melee")
            boss.resetAnim()
            boss.moveRestrict = MoveRestrict.PassThru
            boss.ignoreCombatInteractions = true
            run.controller.vars[BOSS_ACTIVE] = 2
            boss.vars[NEXT_STYLE] = run.tick + TheatreData.rules.nylocasBossStyleTicks
            boss.vars[ATTACK] = run.tick + run.roomrow.attackTicks
        }
    }

    private fun tickBoss(run: TheatreRun) {
        if (run.controller.vars[BOSS_ACTIVE] != 2) return
        val boss = run.boss ?: return
        if (boss.hitpoints <= 0 || !boss.isSlotAssigned) return
        if (run.tick >= boss.vars[NEXT_STYLE]) {
            val previous = TheatreAttackStyle.entries[boss.vars[STYLE]]
            val next = TheatreMechanics.nextNyloStyle(previous, manager.deps.random.of(2))
            changeStyle(boss, next, "npc.nylocas_boss_${next.name.lowercase()}")
            boss.abortRoute()
            boss.resetAnim()
            boss.vars[NEXT_STYLE] = run.tick + TheatreData.rules.nylocasBossStyleTicks
            boss.vars[ATTACK] = run.tick + TheatreData.rules.nylocasTransformAttackTicks
            for (player in manager.combatants(run)) {
                if ((player.interaction as? InteractionNpc)?.target === boss) {
                    player.clearInteractionRoute()
                }
            }
        }
        val target = manager.combatants(run).minByOrNull { boss.distanceTo(it) } ?: return
        attackPlayer(run, boss, target, boss = true)
    }

    private fun attackPlayer(run: TheatreRun, npc: Npc, player: Player, boss: Boolean) {
        val style = TheatreAttackStyle.entries[npc.vars[STYLE]]
        val range = if (style == TheatreAttackStyle.MELEE) 1
            else TheatreData.rules.nylocasRangedRange
        val inRange = if (style == TheatreAttackStyle.MELEE) bounds.touches(npc.avatar, player.avatar)
            else npc.distanceTo(player) <= range &&
                rays.hasLineOfSight(npc.coords, player.coords, npc.size, npc.size)
        if (!inRange) {
            walkAdjacent(npc, player.coords, player.size)
            return
        }
        npc.abortRoute()
        if (run.tick < npc.vars[ATTACK]) return
        npc.facePlayer(player)
        npc.anim(attackAnimation(style))
        val accurate = when (style) {
            TheatreAttackStyle.MELEE -> manager.deps.accuracy.rollMeleeAccuracy(
                npc,
                player,
                MeleeAttackType.Stab,
                manager.deps.random,
            )
            TheatreAttackStyle.RANGED -> manager.deps.accuracy.rollRangedAccuracy(
                npc,
                player,
                manager.deps.random,
            )
            TheatreAttackStyle.MAGIC -> manager.deps.accuracy.rollMagicAccuracy(
                npc,
                player,
                manager.deps.random,
            )
        }
        val maximum = when {
            boss -> TheatreData.rules.nylocasBossMaxHit
            npc.vars[BIG] == 1 -> TheatreData.rules.nylocasBigMaxHit
            else -> TheatreData.rules.nylocasSmallMaxHit
        }
        val damage = if (accurate) manager.deps.random.of(maximum + 1) else 0
        if (style == TheatreAttackStyle.RANGED) {
            val graphic = when {
                boss -> "spotanim.tob_nylocas_rangedprojectile_size2"
                npc.vars[BIG] == 1 -> "spotanim.tob_nylocas_rangedprojectile_sizemid"
                else -> "spotanim.tob_nylocas_rangedprojectile_size1"
            }
            manager.deps.bossProjectile(graphic.asRSCM(RSCMType.SPOTANIM),
                npc.coords.translate(npc.size / 2, npc.size / 2), player.coords,
                60, 20, 0, TheatreData.rules.nylocasProjectileTicks * 30, 16, homing = player)
        }
        val impact = {
            if (manager.run(npc) === run && npc.isSlotAssigned && npc.hitpoints > 0 && npc.vars[DYING] == 0) {
                manager.damage(run, player, damage, hitType(style), npc,
                    prayerPenetration = if (boss && style != TheatreAttackStyle.MELEE) 25 else 0)
            }
        }
        if (style == TheatreAttackStyle.MELEE) impact()
        else manager.schedule(run, TheatreData.rules.nylocasProjectileTicks, impact)
        npc.vars[ATTACK] = run.tick + if (boss) run.roomrow.attackTicks
            else TheatreData.rules.nylocasSmallAttackTicks
    }

    private fun changeStyle(npc: Npc, style: TheatreAttackStyle, symbol: String) {
        val type = checkNotNull(ServerCacheManager.getNpc(symbol.asRSCM(RSCMType.NPC)))
        npc.transmog(type, Int.MAX_VALUE)
        npc.vars[STYLE] = style.ordinal
    }

    private fun walkAdjacent(npc: Npc, target: CoordGrid, size: Int) {
        val candidates = buildList {
            for (offset in 1 - npc.size until size) {
                add(CoordGrid(target.x - npc.size, target.z + offset, target.level))
                add(CoordGrid(target.x + size, target.z + offset, target.level))
                add(CoordGrid(target.x + offset, target.z - npc.size, target.level))
                add(CoordGrid(target.x + offset, target.z + size, target.level))
            }
        }
        val destination = candidates.filter { point ->
            (0 until npc.size).all { x -> (0 until npc.size).all { z ->
                !manager.deps.collision.isWalkBlocked(point.translate(x, z))
            } }
        }.minByOrNull { npc.coords.chebyshevDistance(it) } ?: return
        if (npc.routeDestination.peekLast() != destination) npc.walk(destination)
    }

    private fun smallNylocas(run: TheatreRun): List<Npc> = run.adds.filter {
        it !== run.boss && it.vars[PILLAR] == 0 && it.isSlotAssigned
    }

    private fun pillars(run: TheatreRun): List<Npc> = run.adds.filter {
        it.vars[PILLAR] != 0 && it.hitpoints > 0 && it.isSlotAssigned
    }

    private fun smallType(style: TheatreAttackStyle, big: Boolean, aggressive: Boolean): String =
        "npc.tob_nylocas_${if (big) "big_" else ""}" +
            "${if (aggressive) "fighting" else "incoming"}_${style.name.lowercase()}"

    private fun attackAnimation(style: TheatreAttackStyle): String =
        "seq.top_spider_${style.name.lowercase()}_attack"

    private fun style(type: HitType): TheatreAttackStyle? = when (type) {
        HitType.Melee -> TheatreAttackStyle.MELEE
        HitType.Ranged -> TheatreAttackStyle.RANGED
        HitType.Magic -> TheatreAttackStyle.MAGIC
        HitType.Typeless -> null
    }

    private fun hitType(style: TheatreAttackStyle): HitType = when (style) {
        TheatreAttackStyle.MELEE -> HitType.Melee
        TheatreAttackStyle.RANGED -> HitType.Ranged
        TheatreAttackStyle.MAGIC -> HitType.Magic
    }

    private companion object {
        const val WAVE = "varn.theatre_nylo_wave"
        const val NEXT_WAVE = "varn.theatre_nylo_next_wave_tick"
        const val STYLE = "varn.theatre_nylo_style"
        const val NEXT_STYLE = "varn.theatre_nylo_next_style_tick"
        const val BORN = "varn.theatre_nylo_born_tick"
        const val BIG = "varn.theatre_nylo_big"
        const val AGGRESSIVE = "varn.theatre_nylo_aggressive"
        const val SPAWN_ROW = "varn.theatre_nylo_spawn_row"
        const val LOCKED = "varn.theatre_nylo_locked_members"
        const val TARGET = "varn.theatre_nylo_target_member"
        const val PILLAR = "varn.theatre_nylo_pillar_index"
        const val TARGET_PILLAR = "varn.theatre_target_pillar"
        const val BOSS_ACTIVE = "varn.theatre_nylo_boss_active"
        const val ATTACK = "varn.theatre_nylo_boss_attack_tick"
        const val DYING = "varn.theatre_nylo_dying"
        const val STAGING = "varn.theatre_nylo_staging"
        const val BOSS_SPAWN = "varn.theatre_nylo_boss_spawn_tick"
    }
}
