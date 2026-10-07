package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.DBRowType
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.table.PohFurnitureRow
import org.rsmod.content.skills.construction.configs.PohFurnitureMenu

// Server furniture rows must map to client table rows before invoking clientscript 1404.
internal object PohFurnitureDbRows {
    private class ClientRow(val id: Int, val name: String, val recipe: String)

    private class Index(
        val byRecipe: Map<String, List<ClientRow>>,
        val byName: Map<String, List<ClientRow>>,
        // Use a valid fallback row: an unknown ID aborts clientscript 1404.
        val fallback: Int,
    )

    private val anyNails: Int by lazy { PohFurnitureMenu.ANY_NAILS.asRSCM(RSCMType.OBJ) }

    private val index: Index by lazy { buildIndex() }

    private val byFurnitureRow: Map<Int, Int> by lazy {
        PohFurnitureRow.all().associate { it.rowId to resolve(it) }
    }

    fun clientRow(row: PohFurnitureRow): Int = byFurnitureRow[row.rowId] ?: index.fallback

    private fun buildIndex(): Index {
        val parsed =
            ServerCacheManager.getRowsForTable(PohFurnitureMenu.CLIENT_TABLE).mapNotNull(::parse)
        return Index(
            byRecipe = parsed.groupBy(ClientRow::recipe),
            byName = parsed.groupBy { normalise(it.name) },
            fallback = parsed.minOfOrNull(ClientRow::id) ?: -1,
        )
    }

    private fun parse(row: DBRowType): ClientRow? {
        val columns = row.definedColumns()
        val name =
            columns[PohFurnitureMenu.COL_NAME]?.values?.firstOrNull()?.toString() ?: return null
        val level =
            columns[PohFurnitureMenu.COL_REQUIREMENT]?.values?.getOrNull(1) as? Int ?: return null
        val materials = columns[PohFurnitureMenu.COL_MATERIAL]?.values ?: emptyArray()
        return ClientRow(row.id, name, recipeKey(level, materials.asList()))
    }

    private fun resolve(row: PohFurnitureRow): Int {
        val byRecipe = index.byRecipe[recipeKey(row)]
        if (!byRecipe.isNullOrEmpty()) {
            return best(byRecipe, row.menuName).id
        }
        val byName = index.byName[normalise(row.menuName)]
        if (!byName.isNullOrEmpty()) {
            return best(byName, row.menuName).id
        }
        return index.fallback
    }

    private fun best(candidates: List<ClientRow>, menuName: String): ClientRow {
        if (candidates.size == 1) {
            return candidates[0]
        }
        val wanted = words(menuName)
        return candidates.sortedBy(ClientRow::id).maxBy { candidate ->
            words(candidate.name).count { it in wanted }
        }
    }

    private fun recipeKey(row: PohFurnitureRow): String {
        val materials = ArrayList<Any>(row.material.size * 2 + 2)
        for (material in row.material) {
            materials += material.t0.id
            materials += material.t1
        }
        if (row.nails > 0) {
            materials += anyNails
            materials += row.nails
        }
        return recipeKey(row.level, materials)
    }

    private fun recipeKey(level: Int, materials: List<Any>): String {
        val pairs = ArrayList<String>(materials.size / 2)
        var i = 0
        while (i + 1 < materials.size) {
            pairs += "${materials[i]}x${materials[i + 1]}"
            i += 2
        }
        pairs.sort()
        return "$level|${pairs.joinToString(",")}"
    }

    private fun normalise(name: String): String = name.lowercase().filter(Char::isLetterOrDigit)

    private fun words(name: String): Set<String> =
        name.lowercase().split(NON_WORD).filter(String::isNotEmpty).toSet()

    private val NON_WORD = Regex("[^a-z0-9]+")
}
