package org.rsmod.content.raids.theatreofblood.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType
import org.rsmod.map.CoordGrid

internal fun theatreRules() = dbTable("dbtable.theatre_rules", serverOnly = true) {
    column("exit", 0, VarType.COORDGRID)
    column("max_players", 1, VarType.INT)
    column("regions", 2, VarType.INT)
    column("verzik_phase2_health", 3, VarType.INT)
    column("verzik_phase3_health", 4, VarType.INT)
    column("recovery_fee", 5, VarType.INT)
    column("nylocas_pillars", 6, VarType.COORDGRID)
    column("nylocas_east", 7, VarType.COORDGRID)
    column("nylocas_west", 8, VarType.COORDGRID)
    column("nylocas_south", 9, VarType.COORDGRID)
    column("nylocas_lifetime_ticks", 10, VarType.INT)
    column("nylocas_pillar_health", 11, VarType.INT)
    column("nylocas_wave_ticks", 12, VarType.INT)
    column("nylocas_style_ticks", 13, VarType.INT)
    column("nylocas_small_max_hit", 14, VarType.INT)
    column("nylocas_big_max_hit", 15, VarType.INT)
    column("nylocas_boss_max_hit", 16, VarType.INT)
    column("nylocas_small_blast_max", 17, VarType.INT)
    column("nylocas_big_blast_max", 18, VarType.INT)
    column("nylocas_pillar_collapse_max", 19, VarType.INT)
    column("nylocas_boss_style_ticks", 20, VarType.INT)
    column("nylocas_small_attack_ticks", 21, VarType.INT)
    column("nylocas_small_pillar_damage", 22, VarType.INT)
    column("nylocas_big_pillar_damage", 23, VarType.INT)
    column("nylocas_boss_intro_ticks", 24, VarType.INT)
    column("nylocas_pillar_collapse_ticks", 25, VarType.INT)
    column("nylocas_ranged_range", 26, VarType.INT)
    column("unique_numerator", 27, VarType.INT)
    column("unique_denominator", 28, VarType.INT)
    column("death_retained_numerator", 29, VarType.INT)
    column("death_retained_denominator", 30, VarType.INT)
    column("supply_points", 31, VarType.INT)
    column("death_animation_ticks", 32, VarType.INT)
    column("treasury_entry", 33, VarType.COORDGRID)
    column("reward_chests", 34, VarType.COORDGRID)
    column("dawnbringer_tiles", 35, VarType.COORDGRID)
    column("owned_npcs", 36, VarType.NPC)
    column("lobby_entry", 37, VarType.COORDGRID)
    column("exit_orator", 38, VarType.NPC)
    column("nylocas_first_wave_ticks", 39, VarType.INT)
    column("nylocas_boss_delay_cycles", 40, VarType.INT)
    column("nylocas_boss_spawn_ticks", 41, VarType.INT)
    column("nylocas_entry_tiles", 42, VarType.INT)
    column("nylocas_transform_attack_ticks", 43, VarType.INT)
    column("nylocas_small_death_ticks", 44, VarType.INT)
    column("nylocas_big_death_ticks", 45, VarType.INT)
    column("nylocas_big_detonate_ticks", 46, VarType.INT)
    column("nylocas_small_health", 47, VarType.INT)
    column("nylocas_big_health", 48, VarType.INT)
    column("nylocas_projectile_ticks", 49, VarType.INT)
    column("entrance_footsteps", 50, VarType.SYNTH)
    column("entrance_footstep_delay_cycles", 51, VarType.INT)
    row("dbrow.theatre_rules") {
        column(0, CoordGrid(3658, 3219, 0).packed)
        column(1, 5)
        column(2, *(49..51).flatMap { x -> (66..69).map { z -> (x shl 8) or z } }.toTypedArray())
        column(3, 3500)
        column(4, 3500)
        column(5, 100000)
        column(6, *listOf(
            CoordGrid(3289, 4242, 0), CoordGrid(3289, 4253, 0),
            CoordGrid(3300, 4242, 0), CoordGrid(3300, 4253, 0),
        ).map { it.packed }.toTypedArray())
        column(7, CoordGrid(3304, 4248, 0).packed)
        column(8, CoordGrid(3287, 4248, 0).packed)
        column(9, CoordGrid(3295, 4240, 0).packed)
        column(10, 52)
        column(11, 130)
        column(12, 4)
        column(13, 5)
        column(14, 17)
        column(15, 24)
        column(16, 70)
        column(17, 18)
        column(18, 21)
        column(19, 50)
        column(20, 10)
        column(21, 4)
        column(22, 1)
        column(23, 2)
        column(24, 2)
        column(25, 2)
        column(26, 8)
        column(27, 10)
        column(28, 91)
        column(29, 3)
        column(30, 4)
        column(31, 8)
        columnRSCM(36,
            "npc.tob_maiden_100",
            "npc.tob_maiden_70",
            "npc.tob_maiden_50",
            "npc.tob_maiden_30",
            "npc.tob_maiden_dying_a",
            "npc.tob_maiden_dying_b",
            "npc.tob_bloat",
            "npc.nylocas_boss_melee",
            "npc.nylocas_boss_magic",
            "npc.nylocas_boss_ranged",
            "npc.tob_sotetseg_combat",
            "npc.tob_xarpus_static",
            "npc.tob_xarpus_feeding",
            "npc.tob_xarpus_combat",
            "npc.verzik_initial",
            "npc.verzik_phase1",
            "npc.verzik_phase1_to2_transition",
            "npc.verzik_phase2",
            "npc.verzik_phase2_to3_transition",
            "npc.verzik_phase3",
            "npc.maiden_elemental",
            "npc.maiden_blood_slug",
            "npc.verzik_nylocas_melee",
            "npc.verzik_nylocas_ranged",
            "npc.verzik_nylocas_magic",
            "npc.tob_verzik_phase2_armourednylocas",
            "npc.tob_verzik_phase2_bloodnylocas",
            "npc.tob_verzik_creeper",
            "npc.tob_nylocas_incoming_melee",
            "npc.tob_nylocas_big_incoming_melee",
            "npc.tob_nylocas_incoming_ranged",
            "npc.tob_nylocas_big_incoming_ranged",
            "npc.tob_nylocas_incoming_magic",
            "npc.tob_nylocas_big_incoming_magic",
            "npc.tob_nylocas_fighting_melee",
            "npc.tob_nylocas_big_fighting_melee",
            "npc.tob_nylocas_fighting_ranged",
            "npc.tob_nylocas_big_fighting_ranged",
            "npc.tob_nylocas_fighting_magic",
            "npc.tob_nylocas_big_fighting_magic",
            "npc.tob_nylocas_support",
            "npc.verzik_pillar_npc",
        )
        column(32, 3)
        column(37, CoordGrid(3219, 4460, 0).packed)
        columnRSCM(38, "npc.theatre_exit_orator")
        column(39, 6)
        column(40, 5)
        column(41, 2)
        column(42, 3)
        column(43, 3)
        column(44, 1)
        column(45, 5)
        column(46, 3)
        column(47, 8, 8, 8, 9, 11)
        column(48, 16, 16, 16, 19, 22)
        column(49, 2)
        columnRSCM(50, "synth.HUMAN_FOOTSTEP_01", "synth.HUMAN_FOOTSTEP_02", "synth.HUMAN_FOOTSTEP_03")
        column(51, 20)
        column(33, CoordGrid(3235, 4312, 0).packed)
        column(34, *listOf(
            CoordGrid(3232, 4315, 0), CoordGrid(3236, 4315, 0), CoordGrid(3240, 4315, 0),
            CoordGrid(3227, 4322, 0), CoordGrid(3239, 4322, 0),
        ).map { it.packed }.toTypedArray())
        column(35, *listOf(
            CoordGrid(3163, 4304, 0), CoordGrid(3165, 4304, 0), CoordGrid(3167, 4304, 0),
            CoordGrid(3169, 4304, 0), CoordGrid(3171, 4304, 0),
        ).map { it.packed }.toTypedArray())
    }
}

internal fun theatreRooms() = dbTable("dbtable.theatre_rooms", serverOnly = true) {
    column("order", 0, VarType.INT)
    column("name", 1, VarType.STRING)
    column("npc", 2, VarType.NPC)
    column("center", 3, VarType.COORDGRID)
    column("entry", 4, VarType.COORDGRID)
    column("exit", 5, VarType.COORDGRID)
    column("bounds", 6, VarType.INT)
    column("health", 7, VarType.INT)
    column("attack_ticks", 8, VarType.INT)
    column("animation", 9, VarType.SEQ)
    column("projectile", 10, VarType.SPOTANIM)
    column("sound", 11, VarType.SYNTH)
    column("ambience_music", 12, VarType.DBROW)
    column("combat_music", 13, VarType.DBROW)
    column("arrival", 14, VarType.COORDGRID)
    column("orator", 15, VarType.COORDGRID)
    column("barriers", 16, VarType.COORDGRID)
    val rooms = listOf(
        Room("maiden", "The Maiden of Sugadinti", "tob_maiden_100", CoordGrid(3162, 4444, 0), CoordGrid(3183, 4447, 0), CoordGrid(3188, 4447, 0), listOf(3158, 4438, 3184, 4456, 0), 3500, 10, "maiden_attack_blood", "maiden_shadow_proj", "TOB_MAIDEN_SHADOW_ATTACK"),
        Room("bloat", "Pestilent Bloat", "tob_bloat", CoordGrid(3291, 4440, 0), CoordGrid(3288, 4448, 0), CoordGrid(3306, 4448, 0), listOf(3288, 4440, 3303, 4456, 0), 2000, 1, "tob_bloat_flies_large", "tob_bloat_flies_large", "TOB_BLOAT_FLIES_ATTACK_01"),
        Room("nylocas", "Nylocas Vasilias", "nylocas_boss_melee", CoordGrid(3294, 4246, 0), CoordGrid(3295, 4252, 0), CoordGrid(3295, 4257, 1), listOf(3283, 4232, 3309, 4254, 0), 2500, 4, "top_spider_melee_attack", "tob_nylocas_rangedprojectile_size2", "TOB_NYLOCAS_MELEE_ATTACK_02"),
        Room("sotetseg", "Sotetseg", "tob_sotetseg_combat", CoordGrid(3278, 4322, 0), CoordGrid(3280, 4310, 0), CoordGrid(3280, 4305, 0), listOf(3274, 4308, 3293, 4337, 0), 4000, 4, "tob_sotetseg_attack_ranged", "tob_sotetseg_maging", "TOB_SOTETSEG_ATTACK_RANGED"),
        Room("xarpus", "Xarpus", "tob_xarpus_combat", CoordGrid(3169, 4385, 1), CoordGrid(3170, 4381, 1), CoordGrid(3170, 4398, 1), listOf(3164, 4380, 3177, 4394, 1), 5000, 4, "tob_xarpus_attack_ranged", "tob_xarpus_acidspit", "TOB_XARPUS_ATTACK_RANGED_PROJECTILE_05"),
        Room("verzik", "Verzik Vitur", "verzik_phase1", CoordGrid(3166, 4315, 0), CoordGrid(3168, 4304, 0), CoordGrid(3168, 4303, 0), listOf(3155, 4303, 3182, 4328, 0), 2000, 7, "verzik_phase1_attack_magic", "verzik_phase1_projectile", "TOB_VERZIK_HUMAN_MAGIC_ATTACK"),
    )
    rooms.forEachIndexed { index, room ->
        row("dbrow.theatre_room_${room.key}") {
            column(0, index)
            column(1, room.name)
            columnRSCM(2, "npc.${room.npc}")
            column(3, room.center.packed)
            column(4, room.entry.packed)
            column(5, room.exit.packed)
            column(6, *room.bounds.toTypedArray())
            column(7, room.health)
            column(8, room.attackTicks)
            columnRSCM(9, "seq.${room.animation}")
            columnRSCM(10, "spotanim.${room.projectile}")
            columnRSCM(11, "synth.${room.sound}")
            val musicKey = if (room.key == "bloat") "pestilent_bloat" else room.key
            columnRSCM(12, "dbrow.music_${musicKey}_ambience")
            columnRSCM(13, "dbrow.music_${musicKey}_combat")
            val arrival = listOf(
                CoordGrid(3186, 4447, 0), CoordGrid(3322, 4448, 0),
                CoordGrid(3295, 4257, 1), CoordGrid(3280, 4305, 0),
                CoordGrid(3170, 4398, 1), CoordGrid(3168, 4304, 0),
            )[index]
            val orator = listOf(
                CoordGrid(3192, 4447, 0), CoordGrid(3318, 4449, 0),
                CoordGrid(3298, 4257, 1), CoordGrid(3282, 4304, 0),
                CoordGrid(3172, 4397, 1), CoordGrid(3160, 4304, 0),
            )[index]
            val barriers = listOf(
                (4445..4448).map { CoordGrid(3185, it, 0) },
                listOf(3287, 3304).flatMap { x -> (4446..4449).map { CoordGrid(x, it, 0) } },
                (3295..3296).map { CoordGrid(it, 4255, 1) },
                (3278..3281).map { CoordGrid(it, 4307, 0) },
                listOf(4379, 4395).flatMap { z -> (3169..3171).map { CoordGrid(it, z, 1) } },
                (3167..3169).map { CoordGrid(it, 4302, 0) },
            )[index]
            column(14, arrival.packed)
            column(15, orator.packed)
            column(16, *barriers.map { it.packed }.toTypedArray())
        }
    }
}

internal fun theatreSupplies() = dbTable("dbtable.theatre_supplies", serverOnly = true) {
    column("item", 0, VarType.OBJ)
    column("cost", 1, VarType.INT)
    val supplies = listOf(
        "4dosestamina" to 1, "4doseprayerrestore" to 2, "4dosepotionofsaradomin" to 3,
        "4dose2restore" to 3, "potato_mushroom+onion" to 1, "shark" to 1,
        "seaturtle" to 2, "mantaray" to 2,
    )
    supplies.forEachIndexed { index, (item, cost) ->
        row("dbrow.theatre_supply_$index") {
            columnRSCM(0, "obj.$item")
            column(1, cost)
        }
    }
}

internal fun theatreRewards() = dbTable("dbtable.theatre_rewards", serverOnly = true) {
    column("obj", 0, VarType.OBJ)
    column("weight", 1, VarType.INT)
    column("min", 2, VarType.INT)
    column("max", 3, VarType.INT)
    column("unique", 4, VarType.INT)
    val common = listOf(
        Reward("cert_vial_blood", 2, 45, 60),
        Reward("deathrune", 1, 500, 600), Reward("bloodrune", 1, 500, 600),
        Reward("swamp_tar", 1, 500, 600), Reward("cert_coal", 1, 500, 600),
        Reward("cert_gold_ore", 1, 300, 360), Reward("cert_molten_glass", 1, 200, 240),
        Reward("cert_adamantite_ore", 1, 130, 156), Reward("cert_runite_ore", 1, 60, 72),
        Reward("cert_wine_of_zamorak", 1, 50, 60), Reward("cert_cactus_potato", 1, 50, 60),
        Reward("cert_unidentified_cadantine", 1, 50, 60), Reward("cert_unidentified_avantoe", 1, 40, 48),
        Reward("cert_unidentified_toadflax", 1, 37, 44), Reward("cert_unidentified_kwuarm", 1, 36, 43),
        Reward("cert_unidentified_irit", 1, 34, 40), Reward("cert_unidentified_ranarr", 1, 30, 36),
        Reward("cert_unidentified_snapdragon", 1, 27, 32), Reward("cert_unidentified_lantadyme", 1, 26, 31),
        Reward("cert_unidentified_dwarf_weed", 1, 24, 28), Reward("cert_unidentified_torstol", 1, 20, 24),
        Reward("cert_battlestaff", 1, 15, 18), Reward("cert_rune_battleaxe", 1, 4, 4),
        Reward("cert_rune_platebody", 1, 4, 4), Reward("cert_rune_chainbody", 1, 4, 4),
        Reward("palm_tree_seed", 1, 3, 3), Reward("yew_seed", 1, 3, 3),
        Reward("magic_tree_seed", 1, 3, 3), Reward("mahogany_seed", 1, 10, 12),
    )
    val uniques = listOf(
        Reward("infernal_defender_hilt", 8, 1, 1), Reward("ghrazi_rapier", 2, 1, 1),
        Reward("sanguinesti_staff_uncharged", 2, 1, 1), Reward("justiciar_faceguard", 2, 1, 1),
        Reward("justiciar_chestguard", 2, 1, 1), Reward("justiciar_leg_guards", 2, 1, 1),
        Reward("scythe_of_vitur_uncharged", 1, 1, 1),
    )
    (common + uniques).forEachIndexed { index, reward ->
        row("dbrow.theatre_reward_$index") {
            columnRSCM(0, "obj.${reward.item}")
            column(1, reward.weight)
            column(2, reward.min)
            column(3, reward.max)
            column(4, if (index >= common.size) 1 else 0)
        }
    }
}

private data class Room(
    val key: String,
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
)

private data class Reward(val item: String, val weight: Int, val min: Int, val max: Int)
