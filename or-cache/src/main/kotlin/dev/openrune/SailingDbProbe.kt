package dev.openrune

import dev.openrune.types.dbcol.DbColumnCodec
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.cacheLiteralForOpenRuneVarType
import dev.openrune.types.dbcol.multiColumnMixedOptional

object SailingDbProbe {
    private const val HULL_TABLE = 178
    private const val MATERIAL_COLUMN = 12

    @JvmStatic
    fun main(args: Array<String>) {
        ServerCacheManager.init(240)

        val rows = DbHelper.table("dbtable.sailing_boat_hull")
        println("dbtable.sailing_boat_hull ($HULL_TABLE): ${rows.size} rows")

        for (row in rows) {
            val defined = runCatching { row.getColumn(MATERIAL_COLUMN) }.getOrNull()
            if (defined == null) {
                println("  row ${row.id}: column $MATERIAL_COLUMN NOT DEFINED")
                continue
            }
            val types = defined.types
            val literals =
                types.map { t ->
                    val lit = runCatching { cacheLiteralForOpenRuneVarType(t) }.getOrNull()
                    "id=${t.id} ch='${t.ch}' -> ${lit?.let { it::class.simpleName + "/" + it }}"
                }
            val values = defined.column.values
            println(
                "  row ${row.id}: types=${types.size} values=${values?.size} " +
                    "| ${literals.joinToString(" , ")}"
            )
        }

        println()
        println("--- decoding column 12 via multiColumnMixedOptional (the path that threw) ---")
        for (row in rows) {
            val decoded =
                runCatching {
                        row.multiColumnMixedOptional(
                            "dbcol.sailing_boat_hull:material",
                            DbColumnCodec.ItemServerTypeCodec,
                            DbColumnCodec.IntCodec,
                        )
                    }
                    .fold(
                        { pairs ->
                            pairs.chunked(2).joinToString {
                                val obj = it.getOrNull(0) as? dev.openrune.types.ItemServerType
                                (obj?.name ?: "?") + " x" + (it.getOrNull(1) ?: "?")
                            }
                        },
                        { "THREW: " + it },
                    )
            println("  row " + row.id + ": " + decoded)
        }
    }
}
