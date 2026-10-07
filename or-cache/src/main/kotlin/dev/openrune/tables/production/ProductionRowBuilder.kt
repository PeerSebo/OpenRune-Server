package dev.openrune.tables.production

import dev.openrune.definition.constants.ConstantProvider
import dev.openrune.definition.dbtables.DBRowBuilder

class ProductionRowBuilder(
    private val row: DBRowBuilder,
    private val includeCategory: Boolean,
    private val requireOutput: Boolean = true,
    private val defaultCategory: String? = null,
    private val xpTenthsColumn: Int = ProductionColumns.FIRST_EXTRA_COLUMN,
    private val legacyXpTenths: Boolean = false,
) {
    private data class ItemStack(val id: String, val amount: Int)

    private val inputs = mutableListOf<ItemStack>()
    private val outputs = mutableListOf<ItemStack>()
    private val statReqs = mutableListOf<Pair<String, Int>>()
    private var xpTenthsValue: Int? = null
    private var categoryValue: String? = null

    fun input(item: String, amount: Int = 1) {
        inputs.add(ItemStack(item, amount))
    }

    fun input(vararg items: String) {
        items.forEach { input(it) }
    }

    fun input(items: List<String>) {
        items.forEach { input(it) }
    }

    fun output(item: String, amount: Int = 1) {
        outputs.add(ItemStack(item, amount))
    }

    fun output(vararg items: String) {
        items.forEach { output(it) }
    }

    fun output(items: List<String>) {
        items.forEach { output(it) }
    }

    fun statReq(stat: String, level: Int) {
        statReqs.add(stat to level)
    }

    fun statReq(vararg reqs: Pair<String, Int>) {
        statReqs.addAll(reqs)
    }

    fun statReq(reqs: List<Pair<String, Int>>) {
        statReqs.addAll(reqs)
    }

    /**
     * Recipe XP in tenths: `xpTenths(25)` awards 2.5xp. Integer DB columns cannot store
     * fractional XP directly. Writes canonical `xp_tenths` plus the legacy floored
     * [ProductionColumns.COL_XP] for unmigrated readers.
     */
    fun xpTenths(amount: Int) {
        xpTenthsValue = amount
    }

    fun category(value: String) {
        categoryValue = value
    }

    internal fun apply() {
        require(inputs.isNotEmpty()) { "production row requires at least one input" }
        if (requireOutput) {
            require(outputs.isNotEmpty()) { "production row requires at least one output" }
        }
        require(statReqs.isNotEmpty()) { "production row requires at least one statReq" }
        val xpTenths = requireNotNull(xpTenthsValue) { "production row requires xpTenths" }

        row.columnRSCM(ProductionColumns.COL_INPUT, *inputs.map { it.id }.toTypedArray())
        row.column(ProductionColumns.COL_INPUT_AMOUNT, *inputs.map { it.amount }.toTypedArray())

        val statReqValues =
            statReqs.flatMap { (stat, level) -> listOf(ConstantProvider.getMapping(stat), level) }
        row.column(ProductionColumns.COL_STAT_REQ, *statReqValues.toTypedArray())

        row.column(ProductionColumns.COL_XP, if (legacyXpTenths) xpTenths else xpTenths / 10)
        row.column(xpTenthsColumn, xpTenths)

        if (outputs.isNotEmpty()) {
            row.columnRSCM(ProductionColumns.COL_OUTPUT, *outputs.map { it.id }.toTypedArray())
            row.column(
                ProductionColumns.COL_OUTPUT_AMOUNT,
                *outputs.map { it.amount }.toTypedArray(),
            )
        }

        val category = categoryValue ?: defaultCategory
        category?.let { value ->
            require(includeCategory) { "category() is not supported for this production table" }
            row.column(ProductionColumns.COL_CATEGORY, value)
        }
    }
}
