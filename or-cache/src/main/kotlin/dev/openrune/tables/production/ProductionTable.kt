package dev.openrune.tables.production

import dev.openrune.definition.dbtables.DBRowBuilder
import dev.openrune.definition.dbtables.DBTable
import dev.openrune.definition.dbtables.DBTableBuilder
import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.CacheVarLiteral

class ProductionExtraColumns internal constructor(private val table: DBTableBuilder) {
    internal val claimed = mutableSetOf<Int>()

    fun column(name: String = "", id: Int, vararg types: CacheVarLiteral) {
        claim(id)
        table.column(name, id, *types)
    }

    fun column(name: String, id: Int, types: Array<CacheVarLiteral>, values: Array<Any>? = null) {
        claim(id)
        table.column(name, id, types, values)
    }

    fun column(id: Int, types: Array<CacheVarLiteral>, values: Array<Any>? = null) {
        claim(id)
        table.column(id, types, values)
    }

    fun CacheVarLiteral.count(count: Int): Array<CacheVarLiteral> = Array(count) { this }

    private fun claim(id: Int) {
        require(id >= ProductionColumns.FIRST_EXTRA_COLUMN) {
            "production extra column $id overlaps a base column; use " +
                "${ProductionColumns.FIRST_EXTRA_COLUMN} or above"
        }
        claimed += id
    }
}

class ProductionTableRowScope(
    private val row: DBRowBuilder,
    private val includeCategory: Boolean,
    private val requireOutput: Boolean,
    private val defaultCategory: String?,
    private val xpTenthsColumn: Int,
    private val legacyXpTenths: Boolean,
) {
    fun production(block: ProductionRowBuilder.() -> Unit) {
        ProductionRowBuilder(
                row,
                includeCategory,
                requireOutput,
                defaultCategory,
                xpTenthsColumn,
                legacyXpTenths,
            )
            .apply(block)
            .apply()
    }

    fun column(col: Int, vararg values: Any) {
        row.column(col, *values)
    }

    fun columnRSCM(col: Int, vararg ids: String) {
        row.columnRSCM(col, *ids)
    }
}

class ProductionTableScope(
    private val table: DBTableBuilder,
    private val includeCategory: Boolean,
    private val requireOutput: Boolean,
    private val defaultCategory: String?,
    private val xpTenthsColumn: Int,
    private val legacyXpTenths: Boolean,
) {
    fun row(rowId: String, block: ProductionTableRowScope.() -> Unit) {
        table.row(rowId) {
            ProductionTableRowScope(
                    this,
                    includeCategory,
                    requireOutput,
                    defaultCategory,
                    xpTenthsColumn,
                    legacyXpTenths,
                )
                .block()
        }
    }

    fun row(rowId: Int, block: ProductionTableRowScope.() -> Unit) {
        table.row(rowId) {
            ProductionTableRowScope(
                    this,
                    includeCategory,
                    requireOutput,
                    defaultCategory,
                    xpTenthsColumn,
                    legacyXpTenths,
                )
                .block()
        }
    }
}

private fun DBTableBuilder.registerProductionColumns(
    includeCategory: Boolean,
    extraColumns: ProductionExtraColumns.() -> Unit,
): Int {
    ProductionColumns.register(this, includeCategory)
    val extras = ProductionExtraColumns(this).apply(extraColumns)
    return ProductionColumns.allocateXpTenths(this, extras.claimed)
}

/**
 * @param legacyXpTenths keep [ProductionColumns.COL_XP] in tenths for legacy crafting and
 *   combination-rune readers, which divide it by 10. Remove once they read `xp_tenths` instead.
 */
fun productionTable(
    tableId: String,
    serverOnly: Boolean = false,
    includeCategory: Boolean = true,
    requireOutput: Boolean = true,
    defaultCategory: String? = null,
    legacyXpTenths: Boolean = false,
    extraColumns: ProductionExtraColumns.() -> Unit = {},
    block: ProductionTableScope.() -> Unit,
): DBTable =
    dbTable(tableId, serverOnly = serverOnly) {
        rowType(
            "production",
            buildMap {
                put("production_include_category", includeCategory.toString())
                put("production_require_output", requireOutput.toString())
                if (defaultCategory != null) put("production_default_category", defaultCategory)
            },
        )
        val xpTenthsColumn = registerProductionColumns(includeCategory, extraColumns)
        ProductionTableScope(
                this,
                includeCategory,
                requireOutput,
                defaultCategory,
                xpTenthsColumn,
                legacyXpTenths,
            )
            .block()
    }

fun productionTable(
    tableId: Int,
    serverOnly: Boolean = false,
    includeCategory: Boolean = true,
    requireOutput: Boolean = true,
    defaultCategory: String? = null,
    legacyXpTenths: Boolean = false,
    extraColumns: ProductionExtraColumns.() -> Unit = {},
    block: ProductionTableScope.() -> Unit,
): DBTable =
    dbTable(tableId, serverOnly = serverOnly) {
        rowType(
            "production",
            buildMap {
                put("production_include_category", includeCategory.toString())
                put("production_require_output", requireOutput.toString())
                if (defaultCategory != null) put("production_default_category", defaultCategory)
            },
        )
        val xpTenthsColumn = registerProductionColumns(includeCategory, extraColumns)
        ProductionTableScope(
                this,
                includeCategory,
                requireOutput,
                defaultCategory,
                xpTenthsColumn,
                legacyXpTenths,
            )
            .block()
    }
