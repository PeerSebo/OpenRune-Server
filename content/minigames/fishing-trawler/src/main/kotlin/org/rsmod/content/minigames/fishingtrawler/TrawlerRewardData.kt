package org.rsmod.content.minigames.fishingtrawler

import dev.openrune.types.dbcol.DbColumnCodec.IntCodec
import dev.openrune.types.dbcol.DbColumnCodec.ItemServerTypeCodec
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.column

internal object TrawlerRewardData {
    val fish: List<TrawlerCatchTable.Fish> by lazy {
        val rows = DbHelper.table("dbtable.trawler_catch")
            .sortedBy { it.column("dbcol.trawler_catch:order", IntCodec) }
        require(rows.isNotEmpty()) { "Fishing Trawler catch is missing; rebuild the server cache." }
        require(rows.map { it.column("dbcol.trawler_catch:order", IntCodec) } == rows.indices.toList()) {
            "Fishing Trawler catch order must be unique and contiguous."
        }
        val fish = rows.map { row ->
            val item = row.column("dbcol.trawler_catch:fish", ItemServerTypeCodec)
            val level = row.column("dbcol.trawler_catch:level", IntCodec)
            val xp = row.column("dbcol.trawler_catch:xp_tenths", IntCodec)
            require(level in 1..TrawlerCatchTable.MAX_LEVEL && xp >= 0) {
                "Invalid Fishing Trawler catch level or XP for ${item.internalName}."
            }
            TrawlerCatchTable.Fish(item.internalName, level, xp)
        }
        require(fish.map { it.item }.distinct().size == fish.size) {
            "Fishing Trawler catch contains duplicate items."
        }
        fish
    }

    val anglerPieces: List<String> by lazy {
        val rows = DbHelper.table("dbtable.trawler_angler_outfit")
            .sortedBy { it.column("dbcol.trawler_angler_outfit:order", IntCodec) }
        require(rows.isNotEmpty()) { "Fishing Trawler outfit is missing; rebuild the server cache." }
        require(rows.map { it.column("dbcol.trawler_angler_outfit:order", IntCodec) } == rows.indices.toList()) {
            "Fishing Trawler outfit order must be unique and contiguous."
        }
        val pieces = rows.map {
            it.column("dbcol.trawler_angler_outfit:piece", ItemServerTypeCodec).internalName
        }
        require(pieces.distinct().size == pieces.size) {
            "Fishing Trawler outfit contains duplicate pieces."
        }
        pieces
    }

    fun load() {
        fish
        anglerPieces
    }
}
