package org.rsmod.content.minigames.sorceressgarden.drops

import org.rsmod.api.random.GameRandom
import org.rsmod.content.minigames.sorceressgarden.configs.GardenSeason

// TODO: replace inferred seasonal herb weights with the native tables.
object GardenHerbTables {
    private data class HerbRow(val obj: String, val weight: Int)

    private val winter =
        listOf(
            HerbRow("obj.unidentified_guam", 24),
            HerbRow("obj.unidentified_marentill", 20),
            HerbRow("obj.unidentified_tarromin", 10),
        )

    private val spring =
        listOf(
            HerbRow("obj.unidentified_tarromin", 30),
            HerbRow("obj.unidentified_harralander", 25),
            HerbRow("obj.unidentified_ranarr", 15),
            HerbRow("obj.unidentified_irit", 5),
        )

    private val autumn =
        listOf(
            HerbRow("obj.unidentified_harralander", 30),
            HerbRow("obj.unidentified_ranarr", 30),
            HerbRow("obj.unidentified_irit", 20),
            HerbRow("obj.unidentified_avantoe", 12),
            HerbRow("obj.unidentified_kwuarm", 7),
        )

    private val summer =
        listOf(
            HerbRow("obj.unidentified_ranarr", 30),
            HerbRow("obj.unidentified_irit", 25),
            HerbRow("obj.unidentified_avantoe", 25),
            HerbRow("obj.unidentified_kwuarm", 15),
            HerbRow("obj.unidentified_cadantine", 10),
            HerbRow("obj.unidentified_toadflax", 6),
        )

    private val bySeason: Map<GardenSeason, List<HerbRow>> =
        mapOf(
            GardenSeason.WINTER to winter,
            GardenSeason.SPRING to spring,
            GardenSeason.AUTUMN to autumn,
            GardenSeason.SUMMER to summer,
        )

    fun roll(random: GameRandom, season: GardenSeason): String {
        val table = bySeason.getValue(season)
        val total = table.sumOf { it.weight }
        var roll = random.of(total)
        for (row in table) {
            if (roll < row.weight) {
                return row.obj
            }
            roll -= row.weight
        }
        return table.last().obj
    }
}
