package org.rsmod.content.skills.thieving.configs

object CoinPouches {
    const val CITIZEN: String = "obj.pickpocket_coin_pouch_citizen"
    const val FARMER: String = "obj.pickpocket_coin_pouch_farmer"
    const val HAM: String = "obj.pickpocket_coin_pouch_ham"
    const val WARRIOR: String = "obj.pickpocket_coin_pouch_warrior"
    const val ROGUE: String = "obj.pickpocket_coin_pouch_rogue"
    const val CAVE_GOBLIN: String = "obj.pickpocket_coin_pouch_cavegoblin"
    const val GUARD: String = "obj.pickpocket_coin_pouch_guard"
    const val FREMENNIK: String = "obj.pickpocket_coin_pouch_fremennik"
    const val BEARDED_BANDIT: String = "obj.pickpocket_coin_pouch_bandit2"
    const val DESERT_BANDIT: String = "obj.pickpocket_coin_pouch_desertbandit"
    const val KNIGHT: String = "obj.pickpocket_coin_pouch_knight"
    const val BANDIT: String = "obj.pickpocket_coin_pouch_bandit"
    const val WATCHMAN: String = "obj.pickpocket_coin_pouch_watchman"
    const val MENAPHITE: String = "obj.pickpocket_coin_pouch_menaphite"
    const val PALADIN: String = "obj.pickpocket_coin_pouch_paladin"
    const val GNOME: String = "obj.pickpocket_coin_pouch_gnome"
    const val HERO: String = "obj.pickpocket_coin_pouch_hero"
    const val ELF: String = "obj.pickpocket_coin_pouch_elf"
    const val VYRE: String = "obj.pickpocket_coin_pouch_vyre"
    const val WEALTHY: String = "obj.pickpocket_coin_pouch_varlamore_wealthy"
    const val PIRATE: String = "obj.pickpocket_coin_pouch_pirate"

    val coinsPerPouch: Map<String, IntRange> =
        mapOf(
            CITIZEN to 3..3,
            FARMER to 9..9,
            HAM to 1..21,
            WARRIOR to 18..18,
            ROGUE to 25..40,
            CAVE_GOBLIN to 10..50,
            GUARD to 30..30,
            FREMENNIK to 40..40,
            BEARDED_BANDIT to 40..40,
            DESERT_BANDIT to 30..30,
            KNIGHT to 50..50,
            BANDIT to 50..50,
            WATCHMAN to 60..60,
            MENAPHITE to 60..60,
            PALADIN to 80..80,
            GNOME to 300..300,
            HERO to 200..300,
            ELF to 280..350,
            VYRE to 230..315,
            WEALTHY to 85..85,
            PIRATE to 72..72,
        )

    val all: List<String> = coinsPerPouch.keys.toList()

    // TODO: Verify the coin-pouch jingle family.
    val openJingles: List<String> =
        listOf(
            "synth.COINS_JINGLE_01",
            "synth.COINS_JINGLE_02",
            "synth.COINS_JINGLE_03",
            "synth.COINS_JINGLE_04",
            "synth.COINS_JINGLE_05",
        )

    fun isCoinPouch(obj: String): Boolean = obj in coinsPerPouch
}
