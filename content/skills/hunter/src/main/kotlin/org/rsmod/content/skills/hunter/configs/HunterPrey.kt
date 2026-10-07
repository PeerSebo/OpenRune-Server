package org.rsmod.content.skills.hunter.configs

data class HunterPrey(
    val npc: String,
    val family: TrapFamily,
    val levelReq: Int,
    val xp: Double,
    val locSuffix: String,
    val loot: List<String>,
    val rate: CatchRate,
    val lootCounts: Map<String, IntRange> = emptyMap(),
)

object HunterPreyTable {
    val ALL: List<HunterPrey> = buildList {
        add(
            HunterPrey(
                npc = "npc.hunting_bird_jungle",
                family = TrapFamily.BIRD_SNARE,
                levelReq = 1,
                xp = 34.0,
                locSuffix = "jungle",
                loot = listOf("obj.bones", "obj.spit_raw_bird_meat", "obj.hunting_jungle_feather"),
                lootCounts = mapOf("obj.hunting_jungle_feather" to 5..10),
                rate = CatchRate(low = 101, high = 420),
            )
        )
        add(
            HunterPrey(
                npc = "npc.hunting_bird_desert",
                family = TrapFamily.BIRD_SNARE,
                levelReq = 5,
                xp = 47.0,
                locSuffix = "desert",
                loot = listOf("obj.bones", "obj.spit_raw_bird_meat", "obj.hunting_desert_feather"),
                lootCounts = mapOf("obj.hunting_desert_feather" to 5..10),
                rate = HunterCatchRates.estimated(5),
            )
        )
        add(
            HunterPrey(
                npc = "npc.hunting_bird_woodland",
                family = TrapFamily.BIRD_SNARE,
                levelReq = 9,
                xp = 61.2,
                locSuffix = "woodland",
                loot = listOf("obj.bones", "obj.spit_raw_bird_meat", "obj.hunting_woodland_feather"),
                lootCounts = mapOf("obj.hunting_woodland_feather" to 5..10),
                rate = CatchRate(low = 87, high = 389),
            )
        )
        add(
            HunterPrey(
                npc = "npc.hunting_bird_polar",
                family = TrapFamily.BIRD_SNARE,
                levelReq = 11,
                xp = 64.5,
                locSuffix = "polar",
                loot = listOf("obj.bones", "obj.spit_raw_bird_meat", "obj.hunting_polar_feather"),
                lootCounts = mapOf("obj.hunting_polar_feather" to 5..10),
                rate = CatchRate(low = 83, high = 381),
            )
        )
        add(
            HunterPrey(
                npc = "npc.multicoloured_bird",
                family = TrapFamily.BIRD_SNARE,
                levelReq = 19,
                xp = 95.2,
                locSuffix = "coloured",
                loot = listOf("obj.bones", "obj.spit_raw_bird_meat", "obj.hunting_stripy_bird_feather"),
                lootCounts = mapOf("obj.hunting_stripy_bird_feather" to 5..10),
                rate = HunterCatchRates.estimated(19),
            )
        )

        add(
            HunterPrey(
                npc = "npc.varlamore_hunterjerboa01",
                family = TrapFamily.BOX_TRAP,
                levelReq = 39,
                xp = 137.0,
                locSuffix = "jerboa",
                loot = listOf("obj.hunting_jerboa_tail"),
                rate = HunterCatchRates.estimated(39),
            )
        )
        add(
            HunterPrey(
                npc = "npc.hunting_ferret",
                family = TrapFamily.BOX_TRAP,
                levelReq = 27,
                xp = 115.0,
                locSuffix = "ferret",
                loot = listOf("obj.hunting_ferret"),
                rate = HunterCatchRates.estimated(27),
            )
        )
        add(
            HunterPrey(
                npc = "npc.hunting_chinchompa",
                family = TrapFamily.BOX_TRAP,
                levelReq = 53,
                xp = 198.4,
                locSuffix = "chinchompa",
                loot = listOf("obj.chinchompa_captured"),
                rate = CatchRate(low = 6, high = 268),
            )
        )
        add(
            HunterPrey(
                npc = "npc.hunting_chinchompa_big",
                family = TrapFamily.BOX_TRAP,
                levelReq = 63,
                xp = 265.0,
                locSuffix = "chinchompa_big",
                loot = listOf("obj.chinchompa_big_captured"),
                rate = CatchRate(low = -78, high = 228),
            )
        )
        add(
            HunterPrey(
                npc = "npc.hunting_chinchompa_black",
                family = TrapFamily.BOX_TRAP,
                levelReq = 73,
                xp = 315.0,
                locSuffix = "chinchompa_black",
                loot = listOf("obj.chinchompa_black"),
                rate = CatchRate(low = -78, high = 228),
            )
        )

        add(
            HunterPrey(
                npc = NetTrapColour.SWAMP.prey,
                family = TrapFamily.NET_TRAP,
                levelReq = 29,
                xp = 152.0,
                locSuffix = NetTrapColour.SWAMP.catchSuffix,
                loot = listOf("obj.green_salamander"),
                rate = HunterCatchRates.netTrap(NetTrapColour.SWAMP),
            )
        )
        add(
            HunterPrey(
                npc = NetTrapColour.ORANGE.prey,
                family = TrapFamily.NET_TRAP,
                levelReq = 47,
                xp = 224.0,
                locSuffix = NetTrapColour.ORANGE.catchSuffix,
                loot = listOf("obj.orange_salamander"),
                rate = HunterCatchRates.netTrap(NetTrapColour.ORANGE),
            )
        )
        add(
            HunterPrey(
                npc = NetTrapColour.RED.prey,
                family = TrapFamily.NET_TRAP,
                levelReq = 59,
                xp = 272.0,
                locSuffix = NetTrapColour.RED.catchSuffix,
                loot = listOf("obj.red_salamander"),
                rate = HunterCatchRates.netTrap(NetTrapColour.RED),
            )
        )
        add(
            HunterPrey(
                npc = NetTrapColour.BLACK.prey,
                family = TrapFamily.NET_TRAP,
                levelReq = 67,
                xp = 319.2,
                locSuffix = NetTrapColour.BLACK.catchSuffix,
                loot = listOf("obj.black_salamander"),
                rate = HunterCatchRates.netTrap(NetTrapColour.BLACK),
            )
        )
        add(
            HunterPrey(
                npc = NetTrapColour.MOUNTAIN.prey,
                family = TrapFamily.NET_TRAP,
                levelReq = 79,
                xp = 344.0,
                locSuffix = NetTrapColour.MOUNTAIN.catchSuffix,
                loot = listOf("obj.mountain_salamander"),
                rate = HunterCatchRates.netTrap(NetTrapColour.MOUNTAIN),
            )
        )

        add(
            HunterPrey(
                npc = "npc.varlamore_hunterfox01",
                family = TrapFamily.DEADFALL,
                levelReq = 57,
                xp = 177.6,
                locSuffix = "fennec",
                loot = listOf("obj.bones", "obj.hunting_fennecfox_fur", "obj.hunting_fennecfox_meat"),
                rate = HunterCatchRates.estimated(57),
            )
        )
        add(
            HunterPrey(
                npc = "npc.huntingbeast_claws",
                family = TrapFamily.DEADFALL,
                levelReq = 23,
                xp = 102.4,
                locSuffix = "claw",
                loot = listOf("obj.huntingbeast_claws"),
                rate = HunterCatchRates.estimated(23),
            )
        )
        add(
            HunterPrey(
                npc = "npc.huntingbeast_barbedtail",
                family = TrapFamily.DEADFALL,
                levelReq = 33,
                xp = 134.4,
                locSuffix = "barbed",
                loot = listOf("obj.hunting_barbed_harpoon"),
                rate = HunterCatchRates.estimated(33),
            )
        )
        add(
            HunterPrey(
                npc = "npc.huntingbeast_spiky",
                family = TrapFamily.DEADFALL,
                levelReq = 37,
                xp = 147.2,
                locSuffix = "spike",
                loot = listOf("obj.huntingbeast_spike"),
                rate = HunterCatchRates.estimated(37),
            )
        )
        add(
            HunterPrey(
                npc = "npc.huntingbeast_sabreteeth",
                family = TrapFamily.DEADFALL,
                levelReq = 51,
                xp = 160.0,
                locSuffix = "sabre",
                loot = listOf("obj.bones", "obj.huntingbeast_sabreteeth"),
                rate = HunterCatchRates.estimated(51),
            )
        )
    }

    fun suffixes(family: TrapFamily): List<String> =
        ALL.filter { it.family == family }.map { it.locSuffix }.distinct()

    fun netTrapPrey(colour: NetTrapColour): HunterPrey? =
        ALL.firstOrNull { it.family == TrapFamily.NET_TRAP && it.npc == colour.prey }
}
