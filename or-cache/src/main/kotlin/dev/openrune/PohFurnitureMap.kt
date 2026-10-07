package dev.openrune

import dev.openrune.filesystem.Cache
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.obj
import dev.openrune.types.dbcol.string
import java.nio.file.Paths

object PohFurnitureMap {
    private const val BUILT_FURNITURE_CATEGORY = 206

    @JvmStatic
    fun main(args: Array<String>) {
        ServerCacheManager.init(240)

        val liveDir =
            Paths.get(System.getProperty("user.dir"))
                .resolve(Paths.get(".data", "cache", "LIVE"))
                .normalize()
        val live = OsrsCacheProvider(Cache.load(liveDir), 240)
        live.init()

        // Keep all category-206 locs per model so shared models are reported as ambiguous.
        val locsByModel = HashMap<Int, MutableList<Int>>()
        for ((locId, locType) in live.objects) {
            if (locType.category != BUILT_FURNITURE_CATEGORY) continue
            for (model in locType.objectModels.orEmpty()) {
                locsByModel.getOrPut(model) { mutableListOf() }.add(locId)
            }
        }
        println(
            "// category-$BUILT_FURNITURE_CATEGORY locs indexed by model: ${locsByModel.size} models"
        )

        var resolved = 0
        var ambiguous = 0
        val unresolved = mutableListOf<String>()

        val pairs = mutableListOf<String>()
        val furniture = DbHelper.table("dbtable.furniture")
        for (row in furniture) {
            val rowId = row.id
            val name = row.string("dbcol.furniture:name")
            val dbrowName = RSCM.getReverseMapping(RSCMType.DBROW, rowId)
            val itemId = row.obj("dbcol.furniture:model_obj").id
            val invModel = live.items[itemId]?.inventoryModel

            if (invModel == null) {
                unresolved += "$rowId \"$name\" (item $itemId has no LIVE definition)"
                continue
            }

            val candidates = locsByModel[invModel]
            if (candidates.isNullOrEmpty()) {
                unresolved += "$rowId \"$name\" (model $invModel matches no cat-206 loc)"
                continue
            }

            // A model is often shared by several category-206 locs (the same chair at different
            // rotations, or a themed variant). Break the tie with the naming convention: the loc
            // whose rscm name shares the longest prefix with the dbrow's name is the intended one.
            // That folds both independent signals - model identity and naming - into one answer,
            // and falls back to the lowest id when the names say nothing.
            val locId =
                if (candidates.size == 1) {
                    candidates.first()
                } else {
                    ambiguous++
                    candidates.minWithOrNull(
                        compareByDescending<Int> { candidate ->
                                commonPrefixLength(
                                    dbrowName.substringAfter('.'),
                                    RSCM.getReverseMapping(RSCMType.LOC, candidate)
                                        .substringAfter('.'),
                                )
                            }
                            .thenBy { it }
                    )!!
                }
            val locName = RSCM.getReverseMapping(RSCMType.LOC, locId)
            if (candidates.size > 1) {
                pairs += "    // tie-broken from $candidates (model $invModel)"
            }
            resolved++
            pairs += "    \"$dbrowName\" to \"$locName\", // $name (loc $locId)"
        }

        println(
            "// resolved=$resolved ambiguous=$ambiguous unresolved=${unresolved.size} " +
                "total=${furniture.size}"
        )
        println("\n// ---- pasteable pairs ----")
        pairs.forEach(::println)

        println("\n// ---- UNRESOLVED (${unresolved.size}) ----")
        unresolved.forEach { println("// $it") }
    }

    private fun commonPrefixLength(a: String, b: String): Int {
        val max = minOf(a.length, b.length)
        var i = 0
        while (i < max && a[i] == b[i]) i++
        return i
    }
}
