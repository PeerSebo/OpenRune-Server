package org.rsmod.content.skills.hunter.configs

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType

internal enum class RumourMethod(val denominator: Int, val pity: Int, val fullOutfitPity: Int) {
    BIRD(20, 40, 38), BOX(50, 100, 94), BUTTERFLY(40, 80, 76), DEADFALL(15, 30, 28),
    FALCONRY(10, 20, 18), NET(25, 50, 46), PIT(15, 30, 28), TRACKING(15, 30, 28), HERBIBOAR(7, 14, 12),
}

internal enum class RumourMaster(val saveCode: Int, val saveName: String, val npc: String, val level: Int, val tier: Int, val pool: Char) {
    GILMAN(1, "gilman", "npc.hg_gilman", 46, 0, 'G'), CERVUS(2, "cervus", "npc.hg_cervus", 57, 1, 'C'),
    ORNUS(3, "ornus", "npc.hg_ornus", 57, 1, 'O'), ACO(4, "aco", "npc.hg_aco", 72, 2, 'A'),
    TECO(5, "teco", "npc.hg_teco", 72, 2, 'T'), WOLF(6, "wolf", "npc.hg_wolf", 91, 3, 'W');
    val sack: String get() = "obj.hg_lootsack_t$tier"
    val xpMultiplier: Int get() = when (tier) { 2 -> 55; 3 -> 60; else -> 50 }
}

internal enum class HunterRumourPrey(
    val saveCode: Int,
    val displayName: String, val npc: String, val level: Int, val part: String,
    val method: RumourMethod, val masters: String, val quest: String? = null,
) {
    WAGTAIL(1, "Tropical wagtail", "npc.multicoloured_bird", 19, "obj.hg_tailfeather", RumourMethod.BIRD, "G"),
    WILD(2, "Wild kebbit", "npc.huntingbeast_claws", 23, "obj.hg_kebbittuft", RumourMethod.DEADFALL, "G"),
    SAPPHIRE(3, "Sapphire glacialis", "npc.butterfly_glacialis", 25, "obj.hg_butterflywing_blue", RumourMethod.BUTTERFLY, "G"),
    SWAMP(4, "Swamp lizard", "npc.salamander_green", 29, "obj.hg_lizardclaw", RumourMethod.NET, "GC", "quest_priestinperil"),
    LARUPIA(5, "Spined larupia", "npc.hunting_jaguar", 31, "obj.hg_larupia_ear", RumourMethod.PIT, "GO"),
    BARBED(6, "Barb-tailed kebbit", "npc.huntingbeast_barbedtail", 33, "obj.hg_kebbittuft", RumourMethod.DEADFALL, "G"),
    SNOWY(7, "Snowy knight", "npc.butterfly_snowy", 35, "obj.hg_butterflywing_white", RumourMethod.BUTTERFLY, "GO"),
    PRICKLY(8, "Prickly kebbit", "npc.huntingbeast_spiky", 37, "obj.hg_kebbittuft", RumourMethod.DEADFALL, "G"),
    JERBOA(9, "Embertailed jerboa", "npc.varlamore_hunterjerboa01", 39, "obj.hg_jerboatail_large", RumourMethod.BOX, "GO", "quest_eaglespeak"),
    GRAAHK(10, "Horned graahk", "npc.hunting_leopard", 41, "obj.hg_graahk_horn", RumourMethod.PIT, "GC"),
    SPOTTED(11, "Spotted kebbit", "npc.huntingbeast_speedy", 43, "obj.hg_kebbittuft", RumourMethod.FALCONRY, "GCO"),
    WARLOCK(12, "Black warlock", "npc.butterfly_warlock", 45, "obj.hg_butterflywing_black", RumourMethod.BUTTERFLY, "GC"),
    ORANGE(13, "Orange salamander", "npc.salamander_orange", 47, "obj.hg_lizardclaw_orange", RumourMethod.NET, "GCOA"),
    RAZOR(14, "Razor-backed kebbit", "loc.hunting_trail_end_bush29", 49, "obj.hg_kebbittuft", RumourMethod.TRACKING, "GC"),
    SABRE(15, "Sabre-toothed kebbit", "npc.huntingbeast_sabreteeth", 51, "obj.hg_kebbittuft", RumourMethod.DEADFALL, "GCOAT"),
    CHINCHOMPA(16, "Grey chinchompa", "npc.hunting_chinchompa", 53, "obj.hg_chinchompatuft", RumourMethod.BOX, "GCAT", "quest_eaglespeak"),
    KYATT(17, "Sabre-toothed kyatt", "npc.hunting_snow_tiger", 55, "obj.hg_kyatttooth", RumourMethod.PIT, "GOAT"),
    DARK(18, "Dark kebbit", "npc.huntingbeast_silent", 57, "obj.hg_kebbittuft", RumourMethod.FALCONRY, "GCAT"),
    FOX(19, "Pyre fox", "npc.varlamore_hunterfox01", 57, "obj.hg_foxfluff", RumourMethod.DEADFALL, "GCO"),
    RED(20, "Red salamander", "npc.salamander_red", 59, "obj.hg_lizardclaw_red", RumourMethod.NET, "GOATW"),
    RED_CHINCHOMPA(21, "Red chinchompa", "npc.hunting_chinchompa_big", 63, "obj.hg_chinchompatuft_red", RumourMethod.BOX, "GCOATW", "quest_eaglespeak"),
    DASHING(22, "Dashing kebbit", "npc.huntingbeast_speedy2", 69, "obj.hg_kebbittuft", RumourMethod.FALCONRY, "GATW"),
    SUN_ANTELOPE(23, "Sunlight antelope", "npc.sunlight_antelope", 72, "obj.hg_antelopehoof_sun", RumourMethod.PIT, "GATW"),
    SUN_MOTH(24, "Sunlight moth", "npc.moth_sunlight", 75, "obj.hg_mothwing_sun", RumourMethod.BUTTERFLY, "GCT"),
    TECU(25, "Tecu salamander", "npc.salamander_mountain", 79, "obj.hg_lizardclaw_mountain", RumourMethod.NET, "GAW"),
    HERBIBOAR(26, "Herbiboar", "npc.fossil_herbiboar_visible", 80, "obj.hg_herbytuft", RumourMethod.HERBIBOAR, "GTW", "quest_bonevoyage"),
    MOON_MOTH(27, "Moonlight moth", "npc.moth_moonlight", 85, "obj.hg_mothwing_moon", RumourMethod.BUTTERFLY, "GAW"),
    MOON_ANTELOPE(28, "Moonlight antelope", "npc.moonlight_antelope", 91, "obj.hg_antelopehoof_moon", RumourMethod.PIT, "GW");

    // Persisted species IDs must remain stable across assignment-table changes.
    val partTag: Int get() = if (npc.startsWith("loc.")) 0x10000 or npc.asRSCM(RSCMType.LOC) else npc.asRSCM(RSCMType.NPC)
}
