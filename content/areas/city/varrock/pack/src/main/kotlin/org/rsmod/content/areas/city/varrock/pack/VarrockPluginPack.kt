package org.rsmod.content.areas.city.varrock.pack

import dev.openrune.cache.tools.tasks.CacheTask
import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType
import dev.openrune.pack.PluginPack
import org.rsmod.map.CoordGrid

class VarrockPluginPack : PluginPack() {
    override fun extraTasks(): List<CacheTask> = listOf(VarrockGardenModels())

    override fun dbTables() = listOf(
        apothecaryPotions(),
        dbTable("dbtable.varrock_garden_patches", serverOnly = true) {
            column("loc", 0, VarType.LOC)
            val patches = mapOf(
                "delphiniums" to "loc.garden_delphinium_patch",
                "snowdrops" to "loc.garden_snowdrop_patch",
                "white_roses" to "loc.garden_rosebush_patch_white",
                "red_roses" to "loc.garden_rosebush_patch_red",
                "pink_roses" to "loc.garden_rosebush_patch_pink",
                "white_tree" to "loc.garden_white_tree_patch",
                "vines" to "loc.garden_vine_patch",
            )
            for ((name, loc) in patches) {
                row("dbrow.varrock_garden_$name") { columnRSCM(0, loc) }
            }
        },
        dbTable("dbtable.varrock_garden_corners", serverOnly = true) {
            column("patch", 0, VarType.LOC)
            column("corner", 1, VarType.LOC)
            column("coords", 2, VarType.COORDGRID)
            val corners = listOf(
                Triple("delphiniums", "loc.garden_delphinium_patch", listOf(CoordGrid(3226, 3474), CoordGrid(3227, 3474), CoordGrid(3227, 3476))),
                Triple("snowdrops", "loc.garden_snowdrop_patch", listOf(CoordGrid(3232, 3479), CoordGrid(3232, 3484))),
                Triple("white_roses", "loc.garden_rosebush_patch_white", listOf(CoordGrid(3232, 3472))),
                Triple("red_roses", "loc.garden_rosebush_patch_red", listOf(CoordGrid(3229, 3472), CoordGrid(3230, 3472))),
                Triple("pink_roses", "loc.garden_rosebush_patch_pink", listOf(CoordGrid(3227, 3472))),
            )
            for ((key, patch, coords) in corners) {
                row("dbrow.varrock_garden_corner_$key") {
                    columnRSCM(0, patch)
                    columnRSCM(1, "loc.varrock_garden_corner_$key")
                    column(2, *coords.map { it.packed }.toTypedArray<Any>())
                }
            }
        },
    )
}
