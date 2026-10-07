package org.rsmod.content.minigames.fishingtrawler.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

internal fun trawlerCatch() = dbTable("dbtable.trawler_catch", serverOnly = true) {
    column("order", 0, VarType.INT)
    column("fish", 1, VarType.OBJ)
    column("level", 2, VarType.INT)
    column("xp_tenths", 3, VarType.INT)

    val fish = listOf(
        Triple("obj.mantaray", 81, 690),
        Triple("obj.raw_seaturtle", 79, 570),
        Triple("obj.raw_shark", 76, 660),
        Triple("obj.raw_swordfish", 50, 600),
        Triple("obj.raw_lobster", 40, 540),
        Triple("obj.raw_tuna", 35, 480),
        Triple("obj.oystershell", 16, 510),
        Triple("obj.raw_anchovies", 15, 7),
        Triple("obj.raw_sardine", 15, 7),
        Triple("obj.raw_shrimp", 15, 7),
    )
    for ((order, entry) in fish.withIndex()) {
        row("dbrow.trawler_catch_${entry.first.removePrefix("obj.")}") {
            column(0, order)
            columnRSCM(1, entry.first)
            column(2, entry.second)
            column(3, entry.third)
        }
    }
}

internal fun trawlerAnglerOutfit() = dbTable("dbtable.trawler_angler_outfit", serverOnly = true) {
    column("order", 0, VarType.INT)
    column("piece", 1, VarType.OBJ)

    val pieces = listOf(
        "obj.trawler_reward_hat",
        "obj.trawler_reward_top",
        "obj.trawler_reward_legs",
        "obj.trawler_reward_boots",
    )
    for ((order, piece) in pieces.withIndex()) {
        row("dbrow.trawler_angler_${piece.removePrefix("obj.trawler_reward_")}") {
            column(0, order)
            columnRSCM(1, piece)
        }
    }
}
