package org.rsmod.content.generic.shops

import dev.openrune.types.dbcol.DbColumnCodec.IntCodec
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.column

internal fun orderedShopRows(table: String) =
    DbHelper.table("dbtable.$table")
        .sortedBy { it.column("dbcol.$table:order", IntCodec) }
        .also { rows ->
            require(rows.isNotEmpty()) { "Missing $table catalogue; rebuild the server cache." }
            require(rows.map { it.column("dbcol.$table:order", IntCodec) } == rows.indices.toList()) {
                "$table order must be unique and contiguous."
            }
        }
