package org.rsmod.content.raids.theatreofblood

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.dbcol.DbColumnCodec.CoordGridCodec
import dev.openrune.types.dbcol.DbColumnCodec.DbRowIdCodec
import dev.openrune.types.dbcol.DbColumnCodec.IntCodec
import dev.openrune.types.dbcol.DbColumnCodec.SpotCodec
import dev.openrune.types.dbcol.DbColumnCodec.StringCodec
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.NpcIdCodec
import dev.openrune.types.dbcol.ObjIdCodec
import dev.openrune.types.dbcol.SeqIdCodec
import dev.openrune.types.dbcol.SynthIdCodec
import dev.openrune.types.dbcol.column
import dev.openrune.types.dbcol.multiColumn
import org.rsmod.map.CoordGrid

internal object TheatreData {
    val mechanics by lazy { theatreEncounterRules() }

    val rules by lazy {
        val row = DbHelper.table("dbtable.theatre_rules").single()
        TheatreRules(
            RSCM.getRSCM("dbrow.theatre_rules"),
            row.column("dbcol.theatre_rules:exit", CoordGridCodec),
            row.column("dbcol.theatre_rules:max_players", IntCodec),
            row.multiColumn("dbcol.theatre_rules:regions", IntCodec),
            row.column("dbcol.theatre_rules:verzik_phase2_health", IntCodec),
            row.column("dbcol.theatre_rules:verzik_phase3_health", IntCodec),
            row.column("dbcol.theatre_rules:recovery_fee", IntCodec),
            row.multiColumn("dbcol.theatre_rules:nylocas_pillars", CoordGridCodec),
            row.column("dbcol.theatre_rules:nylocas_east", CoordGridCodec),
            row.column("dbcol.theatre_rules:nylocas_west", CoordGridCodec),
            row.column("dbcol.theatre_rules:nylocas_south", CoordGridCodec),
            row.column("dbcol.theatre_rules:nylocas_lifetime_ticks", IntCodec),
            row.column("dbcol.theatre_rules:nylocas_pillar_health", IntCodec),
            row.column("dbcol.theatre_rules:nylocas_wave_ticks", IntCodec),
            row.column("dbcol.theatre_rules:nylocas_style_ticks", IntCodec),
            row.column("dbcol.theatre_rules:nylocas_small_max_hit", IntCodec),
            row.column("dbcol.theatre_rules:nylocas_big_max_hit", IntCodec),
            row.column("dbcol.theatre_rules:nylocas_boss_max_hit", IntCodec),
            row.column("dbcol.theatre_rules:nylocas_small_blast_max", IntCodec),
            row.column("dbcol.theatre_rules:nylocas_big_blast_max", IntCodec),
            row.column("dbcol.theatre_rules:nylocas_pillar_collapse_max", IntCodec),
            row.column("dbcol.theatre_rules:nylocas_boss_style_ticks", IntCodec),
            row.column("dbcol.theatre_rules:nylocas_small_attack_ticks", IntCodec),
            row.column("dbcol.theatre_rules:nylocas_small_pillar_damage", IntCodec),
            row.column("dbcol.theatre_rules:nylocas_big_pillar_damage", IntCodec),
            row.column("dbcol.theatre_rules:nylocas_boss_intro_ticks", IntCodec),
            row.column("dbcol.theatre_rules:nylocas_pillar_collapse_ticks", IntCodec),
            row.column("dbcol.theatre_rules:nylocas_ranged_range", IntCodec),
            row.column("dbcol.theatre_rules:unique_numerator", IntCodec),
            row.column("dbcol.theatre_rules:unique_denominator", IntCodec),
            row.column("dbcol.theatre_rules:death_retained_numerator", IntCodec),
            row.column("dbcol.theatre_rules:death_retained_denominator", IntCodec),
            row.column("dbcol.theatre_rules:supply_points", IntCodec),
            row.column("dbcol.theatre_rules:death_animation_ticks", IntCodec),
            row.column("dbcol.theatre_rules:treasury_entry", CoordGridCodec),
            row.multiColumn("dbcol.theatre_rules:reward_chests", CoordGridCodec),
            row.multiColumn("dbcol.theatre_rules:dawnbringer_tiles", CoordGridCodec),
            row.multiColumn("dbcol.theatre_rules:owned_npcs", NpcIdCodec).map {
                RSCM.getReverseMapping(RSCMType.NPC, it)
            },
            row.column("dbcol.theatre_rules:lobby_entry", CoordGridCodec),
            RSCM.getReverseMapping(RSCMType.NPC, row.column("dbcol.theatre_rules:exit_orator", NpcIdCodec)),
            row.column("dbcol.theatre_rules:nylocas_first_wave_ticks", IntCodec),
            row.column("dbcol.theatre_rules:nylocas_boss_delay_cycles", IntCodec),
            row.column("dbcol.theatre_rules:nylocas_boss_spawn_ticks", IntCodec),
            row.column("dbcol.theatre_rules:nylocas_entry_tiles", IntCodec),
            row.column("dbcol.theatre_rules:nylocas_transform_attack_ticks", IntCodec),
            row.column("dbcol.theatre_rules:nylocas_small_death_ticks", IntCodec),
            row.column("dbcol.theatre_rules:nylocas_big_death_ticks", IntCodec),
            row.column("dbcol.theatre_rules:nylocas_big_detonate_ticks", IntCodec),
            row.multiColumn("dbcol.theatre_rules:nylocas_small_health", IntCodec),
            row.multiColumn("dbcol.theatre_rules:nylocas_big_health", IntCodec),
            row.column("dbcol.theatre_rules:nylocas_projectile_ticks", IntCodec),
            row.multiColumn("dbcol.theatre_rules:entrance_footsteps", SynthIdCodec),
            row.column("dbcol.theatre_rules:entrance_footstep_delay_cycles", IntCodec),
        ).also {
            require(it.maxPlayers in 1..5 && it.regions.size == 12)
            require(it.regions.distinct().size == it.regions.size && it.recoveryFee >= 0)
            require(it.verzikPhase2Health > 0 && it.verzikPhase3Health > 0)
            require(it.nylocasPillars.size == 4 && it.nylocasLifetimeTicks > 0)
            require(it.nylocasPillarHealth > 0)
            require(it.nylocasWaveTicks > 0 && it.nylocasStyleTicks > 0)
            require(it.nylocasFirstWaveTicks > 0 && it.nylocasBossDelayCycles > 0)
            require(it.nylocasBossSpawnTicks > 0 && it.nylocasEntryTiles > 0)
            require(it.nylocasTransformAttackTicks > 0 && it.nylocasSmallDeathTicks > 0)
            require(it.nylocasBigDeathTicks > 0 && it.nylocasBigDetonateTicks > 0)
            require(it.nylocasProjectileTicks > 0)
            require(it.entranceFootsteps.isNotEmpty() && it.entranceFootstepDelayCycles > 0)
            require(it.nylocasSmallHealth.size == it.maxPlayers && it.nylocasSmallHealth.all { hp -> hp > 0 })
            require(it.nylocasBigHealth.size == it.maxPlayers && it.nylocasBigHealth.all { hp -> hp > 0 })
            require(it.uniqueNumerator in 1..it.uniqueDenominator)
            require(it.deathRetainedDenominator > 0)
            require(it.deathRetainedNumerator in 0..it.deathRetainedDenominator)
            require(it.supplyPoints >= 0)
            require(it.deathAnimationTicks > 0)
            require(it.rewardChests.size == it.maxPlayers && it.dawnbringerTiles.size == it.maxPlayers)
            require(it.ownedNpcs.size == 42 && it.ownedNpcs.distinct().size == it.ownedNpcs.size)
        }
    }

    val roomsList by lazy {
        DbHelper.table("dbtable.theatre_rooms").sortedBy {
            it.column("dbcol.theatre_rooms:order", IntCodec)
        }.map { row ->
            TheatreRoom(
                row.column("dbcol.theatre_rooms:order", IntCodec),
                row.column("dbcol.theatre_rooms:name", StringCodec),
                RSCM.getReverseMapping(RSCMType.NPC, row.column("dbcol.theatre_rooms:npc", NpcIdCodec)),
                row.column("dbcol.theatre_rooms:center", CoordGridCodec),
                row.column("dbcol.theatre_rooms:entry", CoordGridCodec),
                row.column("dbcol.theatre_rooms:exit", CoordGridCodec),
                row.multiColumn("dbcol.theatre_rooms:bounds", IntCodec),
                row.column("dbcol.theatre_rooms:health", IntCodec),
                row.column("dbcol.theatre_rooms:attack_ticks", IntCodec),
                RSCM.getReverseMapping(RSCMType.SEQ, row.column("dbcol.theatre_rooms:animation", SeqIdCodec)),
                RSCM.getReverseMapping(RSCMType.SPOTANIM, row.column("dbcol.theatre_rooms:projectile", SpotCodec).id),
                RSCM.getReverseMapping(RSCMType.SYNTH, row.column("dbcol.theatre_rooms:sound", SynthIdCodec)),
                RSCM.getReverseMapping(RSCMType.DBROW, row.column("dbcol.theatre_rooms:ambience_music", DbRowIdCodec)),
                RSCM.getReverseMapping(RSCMType.DBROW, row.column("dbcol.theatre_rooms:combat_music", DbRowIdCodec)),
                row.column("dbcol.theatre_rooms:arrival", CoordGridCodec),
                row.column("dbcol.theatre_rooms:orator", CoordGridCodec),
                row.multiColumn("dbcol.theatre_rooms:barriers", CoordGridCodec),
            ).also {
                require(it.bounds.size == 5 && it.health > 0 && it.attackTicks > 0)
                require(it.contains(it.center) && it.contains(it.entry))
                require(it.index == 5 || (!it.contains(it.arrival) && !it.contains(it.orator)))
                require(it.barriers.isNotEmpty() && it.barriers.distinct().size == it.barriers.size)
            }
        }.also { require(it.map(TheatreRoom::index) == (0..5).toList()) }
    }

    val suppliesList by lazy {
        DbHelper.table("dbtable.theatre_supplies").map { row ->
            TheatreSupply(
                RSCM.getReverseMapping(RSCMType.OBJ, row.column("dbcol.theatre_supplies:item", ObjIdCodec)),
                row.column("dbcol.theatre_supplies:cost", IntCodec),
            ).also { require(it.cost in 1..3) }
        }.also { require(it.size == 8 && it.distinctBy(TheatreSupply::item).size == it.size) }
    }

    val rewardsList by lazy {
        DbHelper.table("dbtable.theatre_rewards").map { row ->
            TheatreReward(
                RSCM.getReverseMapping(RSCMType.OBJ, row.column("dbcol.theatre_rewards:obj", ObjIdCodec)),
                row.column("dbcol.theatre_rewards:weight", IntCodec),
                row.column("dbcol.theatre_rewards:min", IntCodec),
                row.column("dbcol.theatre_rewards:max", IntCodec),
                row.column("dbcol.theatre_rewards:unique", IntCodec) != 0,
            ).also { require(it.weight > 0 && it.min > 0 && it.max >= it.min) }
        }.also {
            require(it.filter(TheatreReward::unique).sumOf(TheatreReward::weight) == 19)
            require(it.filterNot(TheatreReward::unique).sumOf(TheatreReward::weight) == 30)
            require(it.distinctBy(TheatreReward::obj).size == it.size)
        }
    }

    val nylocasWaves by lazy {
        DbHelper.table("dbtable.theatre_nylocas_waves").map { row ->
            TheatreNylocasSpawn(
                row.column("dbcol.theatre_nylocas_waves:wave", IntCodec),
                row.column("dbcol.theatre_nylocas_waves:lane", StringCodec),
                row.column("dbcol.theatre_nylocas_waves:position", IntCodec),
                row.column("dbcol.theatre_nylocas_waves:big", IntCodec) != 0,
                row.column("dbcol.theatre_nylocas_waves:aggressive", IntCodec) != 0,
                RSCM.getReverseMapping(RSCMType.NPC, row.column("dbcol.theatre_nylocas_waves:npc", NpcIdCodec)),
                row.multiColumn("dbcol.theatre_nylocas_waves:styles", StringCodec),
            ).also {
                require(it.wave in 1..31 && it.position in 0..1)
                require(it.lane in setOf("EAST", "SOUTH", "WEST"))
                require(it.styles.isNotEmpty() && it.styles.all { style -> style in setOf("MELEE", "RANGED", "MAGIC") })
            }
        }.also { require(it.size == 120) }.groupBy(TheatreNylocasSpawn::wave).also {
            require(it.keys.sorted() == (1..31).toList())
        }
    }
}

internal data class TheatreRules(
    val rowId: Int,
    val exit: CoordGrid,
    val maxPlayers: Int,
    val regions: List<Int>,
    val verzikPhase2Health: Int,
    val verzikPhase3Health: Int,
    val recoveryFee: Int,
    val nylocasPillars: List<CoordGrid>,
    val nylocasEast: CoordGrid,
    val nylocasWest: CoordGrid,
    val nylocasSouth: CoordGrid,
    val nylocasLifetimeTicks: Int,
    val nylocasPillarHealth: Int,
    val nylocasWaveTicks: Int,
    val nylocasStyleTicks: Int,
    val nylocasSmallMaxHit: Int,
    val nylocasBigMaxHit: Int,
    val nylocasBossMaxHit: Int,
    val nylocasSmallBlastMax: Int,
    val nylocasBigBlastMax: Int,
    val nylocasPillarCollapseMax: Int,
    val nylocasBossStyleTicks: Int,
    val nylocasSmallAttackTicks: Int,
    val nylocasSmallPillarDamage: Int,
    val nylocasBigPillarDamage: Int,
    val nylocasBossIntroTicks: Int,
    val nylocasPillarCollapseTicks: Int,
    val nylocasRangedRange: Int,
    val uniqueNumerator: Int,
    val uniqueDenominator: Int,
    val deathRetainedNumerator: Int,
    val deathRetainedDenominator: Int,
    val supplyPoints: Int,
    val deathAnimationTicks: Int,
    val treasuryEntry: CoordGrid,
    val rewardChests: List<CoordGrid>,
    val dawnbringerTiles: List<CoordGrid>,
    val ownedNpcs: List<String>,
    val lobbyEntry: CoordGrid,
    val exitOrator: String,
    val nylocasFirstWaveTicks: Int,
    val nylocasBossDelayCycles: Int,
    val nylocasBossSpawnTicks: Int,
    val nylocasEntryTiles: Int,
    val nylocasTransformAttackTicks: Int,
    val nylocasSmallDeathTicks: Int,
    val nylocasBigDeathTicks: Int,
    val nylocasBigDetonateTicks: Int,
    val nylocasSmallHealth: List<Int>,
    val nylocasBigHealth: List<Int>,
    val nylocasProjectileTicks: Int,
    val entranceFootsteps: List<Int>,
    val entranceFootstepDelayCycles: Int,
)

internal data class TheatreRoom(
    val index: Int,
    val name: String,
    val npc: String,
    val center: CoordGrid,
    val entry: CoordGrid,
    val exit: CoordGrid,
    val bounds: List<Int>,
    val health: Int,
    val attackTicks: Int,
    val animation: String,
    val projectile: String,
    val sound: String,
    val ambienceMusic: String,
    val combatMusic: String,
    val arrival: CoordGrid,
    val orator: CoordGrid,
    val barriers: List<CoordGrid>,
) {
    fun contains(coords: CoordGrid): Boolean = coords.level == bounds[4] &&
        coords.x in bounds[0]..bounds[2] && coords.z in bounds[1]..bounds[3]
}

internal data class TheatreSupply(val item: String, val cost: Int)

internal data class TheatreReward(
    val obj: String,
    val weight: Int,
    val min: Int,
    val max: Int,
    val unique: Boolean,
)

internal data class TheatreNylocasSpawn(
    val wave: Int,
    val lane: String,
    val position: Int,
    val big: Boolean,
    val aggressive: Boolean,
    val npc: String,
    val styles: List<String>,
)

private fun theatreEncounterRules(): TheatreEncounterRules {
    val row = DbHelper.table("dbtable.theatre_mechanics").single()
    return TheatreEncounterRules(
        maidenHealerSpawns = row.multiColumn("dbcol.theatre_mechanics:maiden_healer_spawns", CoordGridCodec),
        maidenHealerHealth = row.column("dbcol.theatre_mechanics:maiden_healer_health", IntCodec),
        maidenBloodSpawnHealth = row.column("dbcol.theatre_mechanics:maiden_blood_spawn_health", IntCodec),
        maidenBloodDamageMin = row.column("dbcol.theatre_mechanics:maiden_blood_damage_min", IntCodec),
        maidenBloodDamageMax = row.column("dbcol.theatre_mechanics:maiden_blood_damage_max", IntCodec),
        maidenBloodAttackCadence = row.column("dbcol.theatre_mechanics:maiden_blood_attack_cadence", IntCodec),
        maidenBloodSpawnCadence = row.column("dbcol.theatre_mechanics:maiden_blood_spawn_cadence", IntCodec),
        maidenProjectileDelayTicks = row.column("dbcol.theatre_mechanics:maiden_projectile_delay_ticks", IntCodec),
        maidenBloodDelayTicks = row.column("dbcol.theatre_mechanics:maiden_blood_delay_ticks", IntCodec),
        bloatRoute = row.multiColumn("dbcol.theatre_mechanics:bloat_route", CoordGridCodec),
        bloatPillarBounds = row.multiColumn("dbcol.theatre_mechanics:bloat_pillar_bounds", IntCodec),
        bloatSleepTicks = row.column("dbcol.theatre_mechanics:bloat_sleep_ticks", IntCodec),
        bloatFliesTicks = row.column("dbcol.theatre_mechanics:bloat_flies_ticks", IntCodec),
        bloatStompMin = row.column("dbcol.theatre_mechanics:bloat_stomp_min", IntCodec),
        bloatStompMax = row.column("dbcol.theatre_mechanics:bloat_stomp_max", IntCodec),
        bloatStompRange = row.column("dbcol.theatre_mechanics:bloat_stomp_range", IntCodec),
        bloatFleshTicks = row.column("dbcol.theatre_mechanics:bloat_flesh_ticks", IntCodec),
        bloatFleshDelayTicks = row.column("dbcol.theatre_mechanics:bloat_flesh_delay_ticks", IntCodec),
        bloatFleshMin = row.column("dbcol.theatre_mechanics:bloat_flesh_min", IntCodec),
        bloatFleshMax = row.column("dbcol.theatre_mechanics:bloat_flesh_max", IntCodec),
        bloatFleshStunTicks = row.column("dbcol.theatre_mechanics:bloat_flesh_stun_ticks", IntCodec),
        sotetsegMazeBounds = row.multiColumn("dbcol.theatre_mechanics:sotetseg_maze_bounds", IntCodec),
        sotetsegMazeEntry = row.column("dbcol.theatre_mechanics:sotetseg_maze_entry", CoordGridCodec),
        sotetsegMazeExit = row.column("dbcol.theatre_mechanics:sotetseg_maze_exit", CoordGridCodec),
        sotetsegMazeTimeoutTicks = row.column("dbcol.theatre_mechanics:sotetseg_maze_timeout_ticks", IntCodec),
        sotetsegMazeDamageTicks = row.column("dbcol.theatre_mechanics:sotetseg_maze_damage_ticks", IntCodec),
        sotetsegMazeMin = row.column("dbcol.theatre_mechanics:sotetseg_maze_min", IntCodec),
        sotetsegMazeMax = row.column("dbcol.theatre_mechanics:sotetseg_maze_max", IntCodec),
        sotetsegMazeTimeoutDamage = row.column("dbcol.theatre_mechanics:sotetseg_maze_timeout_damage", IntCodec),
        sotetsegSharedBallAttacks = row.column("dbcol.theatre_mechanics:sotetseg_shared_ball_attacks", IntCodec),
        sotetsegSharedBallDamage = row.column("dbcol.theatre_mechanics:sotetseg_shared_ball_damage", IntCodec),
        sotetsegSharedBallDelayTicks = row.column("dbcol.theatre_mechanics:sotetseg_shared_ball_delay_ticks", IntCodec),
        sotetsegProjectileDelayTicks = row.column("dbcol.theatre_mechanics:sotetseg_projectile_delay_ticks", IntCodec),
        sotetsegMaxHit = row.column("dbcol.theatre_mechanics:sotetseg_max_hit", IntCodec),
        xarpusExhumeIntervalTicks = row.column("dbcol.theatre_mechanics:xarpus_exhume_interval_ticks", IntCodec),
        xarpusExhumeDurationTicks = row.column("dbcol.theatre_mechanics:xarpus_exhume_duration_ticks", IntCodec),
        xarpusExhumePhaseTicks = row.column("dbcol.theatre_mechanics:xarpus_exhume_phase_ticks", IntCodec),
        xarpusExhumeHeal = row.column("dbcol.theatre_mechanics:xarpus_exhume_heal", IntCodec),
        xarpusStareTicks = row.column("dbcol.theatre_mechanics:xarpus_stare_ticks", IntCodec),
        xarpusPoisonMin = row.column("dbcol.theatre_mechanics:xarpus_poison_min", IntCodec),
        xarpusPoisonMax = row.column("dbcol.theatre_mechanics:xarpus_poison_max", IntCodec),
        xarpusRetaliationMin = row.column("dbcol.theatre_mechanics:xarpus_retaliation_min", IntCodec),
        xarpusRetaliationMax = row.column("dbcol.theatre_mechanics:xarpus_retaliation_max", IntCodec),
        xarpusPoisonDelayTicks = row.column("dbcol.theatre_mechanics:xarpus_poison_delay_ticks", IntCodec),
        xarpusPoisonDamageTicks = row.column("dbcol.theatre_mechanics:xarpus_poison_damage_ticks", IntCodec),
        verzikPillars = row.multiColumn("dbcol.theatre_mechanics:verzik_pillars", CoordGridCodec),
        verzikPillarHealth = row.column("dbcol.theatre_mechanics:verzik_pillar_health", IntCodec),
        verzikTransitionTicks = row.column("dbcol.theatre_mechanics:verzik_transition_ticks", IntCodec),
        verzikPhase1MaxHit = row.column("dbcol.theatre_mechanics:verzik_phase1_max_hit", IntCodec),
        verzikPhase2MaxHit = row.column("dbcol.theatre_mechanics:verzik_phase2_max_hit", IntCodec),
        verzikProjectileDelayTicks = row.column("dbcol.theatre_mechanics:verzik_projectile_delay_ticks", IntCodec),
        verzikPillarCollapseAttacks = row.column("dbcol.theatre_mechanics:verzik_pillar_collapse_attacks", IntCodec),
        verzikPillarCollapseDelayTicks = row.column("dbcol.theatre_mechanics:verzik_pillar_collapse_delay_ticks", IntCodec),
        verzikNylocasHealth = row.column("dbcol.theatre_mechanics:verzik_nylocas_health", IntCodec),
        verzikNylocasLifetimeTicks = row.column("dbcol.theatre_mechanics:verzik_nylocas_lifetime_ticks", IntCodec),
        verzikNylocasBlastMin = row.column("dbcol.theatre_mechanics:verzik_nylocas_blast_min", IntCodec),
        verzikNylocasBlastMax = row.column("dbcol.theatre_mechanics:verzik_nylocas_blast_max", IntCodec),
        verzikNylocasBlastRange = row.column("dbcol.theatre_mechanics:verzik_nylocas_blast_range", IntCodec),
        verzikBloodNylocasHealth = row.column("dbcol.theatre_mechanics:verzik_blood_nylocas_health", IntCodec),
        verzikBloodNylocasAttacks = row.column("dbcol.theatre_mechanics:verzik_blood_nylocas_attacks", IntCodec),
        verzikPhase2NylocasAttacks = row.column("dbcol.theatre_mechanics:verzik_phase2_nylocas_attacks", IntCodec),
        verzikKnockbackTiles = row.column("dbcol.theatre_mechanics:verzik_knockback_tiles", IntCodec),
        verzikKnockbackCycles = row.column("dbcol.theatre_mechanics:verzik_knockback_cycles", IntCodec),
        verzikKnockbackStunTicks = row.column("dbcol.theatre_mechanics:verzik_knockback_stun_ticks", IntCodec),
        verzikKnockbackMin = row.column("dbcol.theatre_mechanics:verzik_knockback_min", IntCodec),
        verzikKnockbackMax = row.column("dbcol.theatre_mechanics:verzik_knockback_max", IntCodec),
        verzikPowerBlastDelayTicks = row.column("dbcol.theatre_mechanics:verzik_power_blast_delay_ticks", IntCodec),
        verzikPowerBlastDamage = row.column("dbcol.theatre_mechanics:verzik_power_blast_damage", IntCodec),
        verzikGreenBallDelayTicks = row.column("dbcol.theatre_mechanics:verzik_green_ball_delay_ticks", IntCodec),
        verzikPhase3MeleeMaxHit = row.column("dbcol.theatre_mechanics:verzik_phase3_melee_max_hit", IntCodec),
        verzikPhase3RangedMagicMaxHit = row.column("dbcol.theatre_mechanics:verzik_phase3_ranged_magic_max_hit", IntCodec),
        maidenBloodLifetimeTicks = row.column("dbcol.theatre_mechanics:maiden_blood_lifetime_ticks", IntCodec),
        maidenBloodDamageTicks = row.column("dbcol.theatre_mechanics:maiden_blood_damage_ticks", IntCodec),
        maidenBloodTrailTicks = row.column("dbcol.theatre_mechanics:maiden_blood_trail_ticks", IntCodec),
        bloatRouteTicks = row.column("dbcol.theatre_mechanics:bloat_route_ticks", IntCodec),
        bloatFliesMin = row.column("dbcol.theatre_mechanics:bloat_flies_min", IntCodec),
        bloatFliesMax = row.column("dbcol.theatre_mechanics:bloat_flies_max", IntCodec),
        bloatFliesSpreadRange = row.column("dbcol.theatre_mechanics:bloat_flies_spread_range", IntCodec),
        verzikTornadoRespawnTicks = row.column("dbcol.theatre_mechanics:verzik_tornado_respawn_ticks", IntCodec),
        maidenBloodSpawnLifetimeTicks = row.column("dbcol.theatre_mechanics:maiden_blood_spawn_lifetime_ticks", IntCodec),
        maidenBloodWanderTicks = row.column("dbcol.theatre_mechanics:maiden_blood_wander_ticks", IntCodec),
        maidenHealerBurstTicks = row.column("dbcol.theatre_mechanics:maiden_healer_burst_ticks", IntCodec),
        maidenHealerDestination = row.column("dbcol.theatre_mechanics:maiden_healer_destination", CoordGridCodec),
        maidenBloodExtraSplats = row.column("dbcol.theatre_mechanics:maiden_blood_extra_splats", IntCodec),
        maidenBloodScatterRange = row.column("dbcol.theatre_mechanics:maiden_blood_scatter_range", IntCodec),
        maidenBloodAttackSound = RSCM.getReverseMapping(RSCMType.SYNTH,
            row.column("dbcol.theatre_mechanics:maiden_blood_attack_sound", SynthIdCodec)),
        maidenHealerNorthDestination = row.column("dbcol.theatre_mechanics:maiden_healer_north_destination", CoordGridCodec),
        bloatStompTicks = row.column("dbcol.theatre_mechanics:bloat_stomp_ticks", IntCodec),
        bloatFleshFastTicks = row.column("dbcol.theatre_mechanics:bloat_flesh_fast_ticks", IntCodec),
        bloatFleshCount = row.column("dbcol.theatre_mechanics:bloat_flesh_count", IntCodec),
        bloatTurnTicks = row.column("dbcol.theatre_mechanics:bloat_turn_ticks", IntCodec),
        bloatTurnChance = row.column("dbcol.theatre_mechanics:bloat_turn_chance", IntCodec),
        bloatMoveChangeTicks = row.column("dbcol.theatre_mechanics:bloat_move_change_ticks", IntCodec),
        bloatFirstWalkTicks = row.column("dbcol.theatre_mechanics:bloat_first_walk_ticks", IntCodec),
        bloatWalkTicks = row.column("dbcol.theatre_mechanics:bloat_walk_ticks", IntCodec),
        bloatWalkWindowTicks = row.column("dbcol.theatre_mechanics:bloat_walk_window_ticks", IntCodec),
        bloatSleepChance = row.column("dbcol.theatre_mechanics:bloat_sleep_chance", IntCodec),
        bloatDefenceRegenTicks = row.column("dbcol.theatre_mechanics:bloat_defence_regen_ticks", IntCodec),
        bloatFleshGraphics = row.multiColumn("dbcol.theatre_mechanics:bloat_flesh_graphics", SpotCodec)
            .map { RSCM.getReverseMapping(RSCMType.SPOTANIM, it.id) },
        bloatFleshSound = RSCM.getReverseMapping(RSCMType.SYNTH,
            row.column("dbcol.theatre_mechanics:bloat_flesh_sound", SynthIdCodec)),
    ).also {
        require(it.maidenHealerSpawns.size == 10 && it.bloatRoute.size == 4)
        require(it.bloatPillarBounds.size == 4 && it.sotetsegMazeBounds.size == 5)
        require(it.verzikPillars.isNotEmpty())
        require(it.maidenBloodSpawnLifetimeTicks > 0 && it.maidenBloodWanderTicks > 0)
        require(it.maidenHealerBurstTicks > 0)
        require(it.bloatStompTicks in 1 until it.bloatSleepTicks)
        require(it.bloatFleshTicks > 0 && it.bloatFleshFastTicks > 0 && it.bloatFleshCount > 0)
        require(it.bloatTurnTicks > 0 && it.bloatTurnChance > 0 && it.bloatSleepChance > 0)
        require(it.bloatMoveChangeTicks > 0 && it.bloatFirstWalkTicks >= it.bloatWalkTicks)
        require(it.bloatWalkTicks > 0 && it.bloatWalkWindowTicks >= 0 && it.bloatDefenceRegenTicks > 0)
        require(it.bloatFleshGraphics.isNotEmpty())
        require(it.maidenBloodTrailTicks > 0 && it.maidenBloodExtraSplats >= 0 && it.maidenBloodScatterRange > 0)
    }
}

internal data class TheatreEncounterRules(
    val maidenHealerSpawns: List<CoordGrid>,
    val maidenHealerHealth: Int,
    val maidenBloodSpawnHealth: Int,
    val maidenBloodDamageMin: Int,
    val maidenBloodDamageMax: Int,
    val maidenBloodAttackCadence: Int,
    val maidenBloodSpawnCadence: Int,
    val maidenProjectileDelayTicks: Int,
    val maidenBloodDelayTicks: Int,
    val bloatRoute: List<CoordGrid>,
    val bloatPillarBounds: List<Int>,
    val bloatSleepTicks: Int,
    val bloatFliesTicks: Int,
    val bloatStompMin: Int,
    val bloatStompMax: Int,
    val bloatStompRange: Int,
    val bloatFleshTicks: Int,
    val bloatFleshDelayTicks: Int,
    val bloatFleshMin: Int,
    val bloatFleshMax: Int,
    val bloatFleshStunTicks: Int,
    val sotetsegMazeBounds: List<Int>,
    val sotetsegMazeEntry: CoordGrid,
    val sotetsegMazeExit: CoordGrid,
    val sotetsegMazeTimeoutTicks: Int,
    val sotetsegMazeDamageTicks: Int,
    val sotetsegMazeMin: Int,
    val sotetsegMazeMax: Int,
    val sotetsegMazeTimeoutDamage: Int,
    val sotetsegSharedBallAttacks: Int,
    val sotetsegSharedBallDamage: Int,
    val sotetsegSharedBallDelayTicks: Int,
    val sotetsegProjectileDelayTicks: Int,
    val sotetsegMaxHit: Int,
    val xarpusExhumeIntervalTicks: Int,
    val xarpusExhumeDurationTicks: Int,
    val xarpusExhumePhaseTicks: Int,
    val xarpusExhumeHeal: Int,
    val xarpusStareTicks: Int,
    val xarpusPoisonMin: Int,
    val xarpusPoisonMax: Int,
    val xarpusRetaliationMin: Int,
    val xarpusRetaliationMax: Int,
    val xarpusPoisonDelayTicks: Int,
    val xarpusPoisonDamageTicks: Int,
    val verzikPillars: List<CoordGrid>,
    val verzikPillarHealth: Int,
    val verzikTransitionTicks: Int,
    val verzikPhase1MaxHit: Int,
    val verzikPhase2MaxHit: Int,
    val verzikProjectileDelayTicks: Int,
    val verzikPillarCollapseAttacks: Int,
    val verzikPillarCollapseDelayTicks: Int,
    val verzikNylocasHealth: Int,
    val verzikNylocasLifetimeTicks: Int,
    val verzikNylocasBlastMin: Int,
    val verzikNylocasBlastMax: Int,
    val verzikNylocasBlastRange: Int,
    val verzikBloodNylocasHealth: Int,
    val verzikBloodNylocasAttacks: Int,
    val verzikPhase2NylocasAttacks: Int,
    val verzikKnockbackTiles: Int,
    val verzikKnockbackCycles: Int,
    val verzikKnockbackStunTicks: Int,
    val verzikKnockbackMin: Int,
    val verzikKnockbackMax: Int,
    val verzikPowerBlastDelayTicks: Int,
    val verzikPowerBlastDamage: Int,
    val verzikGreenBallDelayTicks: Int,
    val verzikPhase3MeleeMaxHit: Int,
    val verzikPhase3RangedMagicMaxHit: Int,
    val maidenBloodLifetimeTicks: Int,
    val maidenBloodDamageTicks: Int,
    val maidenBloodTrailTicks: Int,
    val bloatRouteTicks: Int,
    val bloatFliesMin: Int,
    val bloatFliesMax: Int,
    val bloatFliesSpreadRange: Int,
    val verzikTornadoRespawnTicks: Int,
    val maidenBloodSpawnLifetimeTicks: Int,
    val maidenBloodWanderTicks: Int,
    val maidenHealerBurstTicks: Int,
    val maidenHealerDestination: CoordGrid,
    val maidenBloodExtraSplats: Int,
    val maidenBloodScatterRange: Int,
    val maidenBloodAttackSound: String,
    val maidenHealerNorthDestination: CoordGrid,
    val bloatStompTicks: Int,
    val bloatFleshFastTicks: Int,
    val bloatFleshCount: Int,
    val bloatTurnTicks: Int,
    val bloatTurnChance: Int,
    val bloatMoveChangeTicks: Int,
    val bloatFirstWalkTicks: Int,
    val bloatWalkTicks: Int,
    val bloatWalkWindowTicks: Int,
    val bloatSleepChance: Int,
    val bloatDefenceRegenTicks: Int,
    val bloatFleshGraphics: List<String>,
    val bloatFleshSound: String,
)
