package org.rsmod.content.other.levelup

import org.rsmod.api.player.midiJingle
import org.rsmod.game.entity.Player

// MIDI_JINGLE uses archive-11 group IDs, not RSCM jingle IDs.
internal object Jingles {
    private val IDS: Map<String, Int> =
        mapOf(
            "jingle.farming_levelup" to 10,
            "jingle.farming_levelup_2" to 11,
            "jingle.advance_agility" to 28,
            "jingle.advance_attack" to 29,
            "jingle.advance_attack2" to 30,
            "jingle.advance_carpentry" to 31,
            "jingle.advance_carpentry2" to 32,
            "jingle.advance_cooking" to 33,
            "jingle.advance_cooking2" to 34,
            "jingle.advance_crafting" to 35,
            "jingle.advance_crafting2" to 36,
            "jingle.advance_defense" to 37,
            "jingle.advance_defense2" to 38,
            "jingle.advance_firemarking" to 39,
            "jingle.advance_firemarking2" to 40,
            "jingle.advance_fishing" to 41,
            "jingle.advance_fishing2" to 42,
            "jingle.advance_fletching" to 43,
            "jingle.advance_fletching2" to 44,
            "jingle.advance_herblaw" to 45,
            "jingle.advance_herblaw2" to 46,
            "jingle.advance_hitpoints" to 47,
            "jingle.advance_hitpoints2" to 48,
            "jingle.advance_hunting" to 49,
            "jingle.advance_hunting2" to 50,
            "jingle.advance_magic" to 51,
            "jingle.advance_magic2" to 52,
            "jingle.advance_mining" to 53,
            "jingle.advance_mining2" to 54,
            "jingle.advance_prayer" to 55,
            "jingle.advance_prayer2" to 56,
            "jingle.advance_ranged" to 57,
            "jingle.advance_ranged2" to 58,
            "jingle.advance_runecraft" to 59,
            "jingle.advance_runecraft2" to 60,
            "jingle.advance_slayer" to 61,
            "jingle.advance_slayer2" to 62,
            "jingle.advance_smithing" to 63,
            "jingle.advance_smithing2" to 64,
            "jingle.advance_strength" to 65,
            "jingle.advance_strength2" to 66,
            "jingle.advance_thieving" to 67,
            "jingle.advance_thieving2" to 68,
            "jingle.advance_woodcutting" to 69,
            "jingle.advance_woodcutting2" to 70,
            "jingle.advance_sailing_charting" to 830,
            "jingle.advance_sailing" to 831,
            "jingle.advance_sailing2" to 832,
        )

    fun idOf(jingle: String): Int? = IDS[jingle]

    fun play(player: Player, jingle: String) {
        val id = idOf(jingle) ?: return
        playRaw(player, id)
    }

    fun playRaw(player: Player, id: Int) {
        player.midiJingle(id)
    }
}
