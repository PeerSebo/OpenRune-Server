package org.rsmod.content.minigames.sorceressgarden.configs

import dev.openrune.types.dbcol.DbColumnCodec
import dev.openrune.types.dbcol.DbColumnCodec.CoordGridCodec
import dev.openrune.types.dbcol.DbColumnCodec.IntCodec
import dev.openrune.types.dbcol.DbColumnCodec.ItemServerTypeCodec
import dev.openrune.types.dbcol.DbColumnCodec.LocTypeCodec
import dev.openrune.types.dbcol.DbColumnCodec.StringCodec
import dev.openrune.types.dbcol.DbHelper
import org.rsmod.map.CoordGrid

internal object GardenSeasonData {
    val rows: Map<String, Season> by lazy {
        validate(DbHelper.table("dbtable.garden_seasons").map(::read))
    }

    internal fun read(row: DbHelper): Season = Season(
        name = row.required("season", StringCodec),
        thievingLevel = row.required("thieving_level", IntCodec),
        doorLoc = row.required("door_loc", LocTypeCodec).internalName,
        catchEjectCoord = row.required("catch_eject_coord", CoordGridCodec),
        treeLoc = row.required("tree_loc", LocTypeCodec).internalName,
        herbLoc = row.required("herb_loc", LocTypeCodec).internalName,
        fruitObj = row.required("fruit_obj", ItemServerTypeCodec).internalName,
        juiceObj = row.required("juice_obj", ItemServerTypeCodec).internalName,
        sqirkCount = row.required("sqirk_count", IntCodec),
        farmingXp = row.required("farming_xp", IntCodec).toDouble(),
        osmanXp = row.required("osman_xp", IntCodec).toDouble(),
        thievingBoost = row.required("thieving_boost", IntCodec),
        runEnergyPercent = row.required("run_energy_percent", IntCodec),
        treeAnchor = row.required("tree_anchor", CoordGridCodec),
        herbAnchor = row.required("herb_anchor", CoordGridCodec),
    )

    internal fun validate(rows: List<Season>): Map<String, Season> {
        val names = GardenSeason.entries.map { it.name }
        require(rows.map { it.name }.toSet() == names.toSet() && rows.size == names.size) {
            "Sorceress Garden requires one definition for each season."
        }
        for (row in rows) {
            require(row.thievingLevel in 1..99 && row.sqirkCount > 0)
            require(row.farmingXp >= 0 && row.osmanXp >= 0 && row.thievingBoost >= 0)
            require(row.runEnergyPercent in 0..100)
            require(listOf(row.catchEjectCoord, row.treeAnchor, row.herbAnchor).all { it.level == 0 })
        }
        for (key in listOf<(Season) -> String>({ it.doorLoc }, { it.treeLoc }, { it.herbLoc }, { it.fruitObj }, { it.juiceObj })) {
            require(rows.distinctBy(key).size == rows.size) { "Garden season interaction definitions must be unique." }
        }
        val byName = rows.associateBy { it.name }
        return names.associateWith { byName.getValue(it) }
    }

    private fun <T, R> DbHelper.required(name: String, codec: DbColumnCodec<T, R>): R {
        val column = getColumn("dbcol.garden_seasons:$name")
        require(column.size == 1) { "Garden season row $id column $name must have one value." }
        return column.decodeAt(0, codec)
    }

    data class Season(
        val name: String,
        val thievingLevel: Int,
        val doorLoc: String,
        val catchEjectCoord: CoordGrid,
        val treeLoc: String,
        val herbLoc: String,
        val fruitObj: String,
        val juiceObj: String,
        val sqirkCount: Int,
        val farmingXp: Double,
        val osmanXp: Double,
        val thievingBoost: Int,
        val runEnergyPercent: Int,
        val treeAnchor: CoordGrid,
        val herbAnchor: CoordGrid,
    )
}
