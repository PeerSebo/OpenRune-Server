package org.rsmod.content.minigames.blastfurnace.core

import dev.openrune.types.ItemServerType
import org.rsmod.content.skills.smithing.table.SmithingBar
import org.rsmod.content.skills.smithing.util.SmithingBonuses
internal class BlastFurnaceBar(
    val output: ItemServerType,
    val levelReq: Int,
    val smithingBar: SmithingBar,
    val input: List<ItemServerType>,
    val inputAmount: List<Int>,
) {
    val primaryOre: ItemServerType
        get() = input[0]

    val primaryAmount: Int
        get() = inputAmount[0]

    val secondaryOre: ItemServerType?
        get() = input.getOrNull(1)

    val secondaryAmount: Int
        get() = inputAmount.getOrNull(1) ?: 0

    val usesCoal: Boolean
        get() = secondaryOre?.internalName == "obj.coal"
}
internal object BlastFurnaceRecipes {

    private const val LEAD = "obj.lead_bar"
    private const val CUPRONICKEL = "obj.cupronickel_bar"

    private val includedOutputs =
        setOf(
            "obj.bronze_bar",
            "obj.iron_bar",
            "obj.silver_bar",
            LEAD,
            "obj.steel_bar",
            "obj.gold_bar",
            CUPRONICKEL,
            "obj.mithril_bar",
            "obj.adamantite_bar",
            "obj.runite_bar",
        )

    val all: List<BlastFurnaceBar> by lazy {
        SmithingBar.all()
            .filter { it.output.internalName in includedOutputs }
            .map { bar ->
                val amounts =
                    if (SmithingBonuses.requiresCoal(bar)) {
                        listOf(bar.inputAmount[0], SmithingBonuses.halfCoalAmount(bar))
                    } else {
                        bar.inputAmount
                    }
                BlastFurnaceBar(bar.output, bar.levelReq, bar, bar.input, amounts)
            }
    }

    private val byOutput: Map<String, BlastFurnaceBar> by lazy {
        all.associateBy { it.output.internalName }
    }
    private val byPrimaryOre: Map<String, BlastFurnaceBar> by lazy {
        all.associateBy { it.primaryOre.internalName }
    }

    fun byOutputName(outputInternal: String): BlastFurnaceBar? = byOutput[outputInternal]

    fun forPrimaryOre(oreInternal: String): BlastFurnaceBar? = byPrimaryOre[oreInternal]

    val ironFallback: BlastFurnaceBar by lazy { byOutput.getValue("obj.iron_bar") }
    val steel: BlastFurnaceBar by lazy { byOutput.getValue("obj.steel_bar") }
}
