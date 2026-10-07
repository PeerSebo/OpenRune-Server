package org.rsmod.content.skills.runecrafting.configs

import dev.openrune.types.dbcol.DbColumnCodec.IntCodec
import dev.openrune.types.dbcol.DbColumnCodec.ItemServerTypeCodec
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.column
import dev.openrune.types.dbcol.multiColumn
import org.rsmod.api.table.runecrafting.RunecraftingRunesRow

internal object OuraniaRateData {
    val bands: List<Band> by lazy {
        val runeNames = RunecraftingRunesRow.all().map { it.output.internalName }.toSet()
        val rows = DbHelper.table("dbtable.ourania_rates").map { row ->
            val level = row.column("dbcol.ourania_rates:level", IntCodec)
            val runes = row.multiColumn("dbcol.ourania_rates:runes", ItemServerTypeCodec)
            val weights = row.multiColumn("dbcol.ourania_rates:weights", IntCodec)
            require(level in 1..99) { "Invalid Ourania level floor: $level" }
            require(runes.isNotEmpty() && runes.size == weights.size) {
                "Ourania level $level must pair every rune with a weight."
            }
            require(runes.map { it.id }.distinct().size == runes.size) {
                "Duplicate Ourania runes at level $level."
            }
            require(runes.all { it.internalName in runeNames }) {
                "Ourania level $level references a rune without a crafting row."
            }
            require(weights.all { it >= 0 } && weights.sumOf { it.toLong() } in 1L..Int.MAX_VALUE.toLong()) {
                "Invalid Ourania weights at level $level."
            }
            Band(level, runes.zip(weights) { rune, weight ->
                OuraniaRates.RuneWeight(rune.internalName, weight)
            })
        }.sortedBy { it.level }
        require(rows.isNotEmpty()) { "Ourania rates are missing; rebuild the server cache." }
        require(rows.first().level == 1 && rows.map { it.level }.distinct().size == rows.size) {
            "Ourania level floors must be unique and begin at one."
        }
        rows
    }

    data class Band(val level: Int, val weights: List<OuraniaRates.RuneWeight>)
}
