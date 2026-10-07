package org.rsmod.content.skills.smithing.util

import dev.openrune.types.ItemServerType
import org.rsmod.api.enums.SmithingProductToEnums
import org.rsmod.content.skills.smithing.table.SmithingBar

object SmithingData {
    const val ANVIL_CATEGORY = "category.anvil"
    const val SMITHING_INTERFACE = "interface.smithing"
    const val SMITHING_BAR_TYPE_VARBIT = "varbit.smithing_bar_type"

    // Resolve lazily so an unavailable dbcol cannot poison class initialization.
    val barsByOutput: Map<String, SmithingBar> by lazy {
        SmithingBar.all().associateBy { it.output.internalName }
    }

    private val barEnum = SmithingProductToEnums.smithing_product_to_item

    val barOutputInternals: Set<String> by lazy {
        barEnum.values.mapNotNull { it?.internalName }.toSet()
    }

    val allBars: List<SmithingBar> by lazy {
        SmithingBar.all().filter { it.output.internalName in barOutputInternals }
    }

    val productMeta: List<SmithingProductMeta> by lazy {
        SmithingProductToEnums.smithing_product_to_requirement.keys.mapNotNull { product ->
            val level =
                SmithingProductToEnums.smithing_product_to_requirement[product]
                    ?: return@mapNotNull null
            val barCount =
                SmithingProductToEnums.smithing_product_to_bars_required[product]
                    ?: return@mapNotNull null
            val numProduced =
                SmithingProductToEnums.smithing_product_to_quantity[product]
                    ?: return@mapNotNull null
            val bar = barForProduct(product.name) ?: return@mapNotNull null
            if (bar.output.internalName !in barOutputInternals) {
                return@mapNotNull null
            }
            SmithingProductMeta(
                product = product,
                name = product.name.lowercase(),
                level = level,
                barCount = barCount,
                numProduced = numProduced,
                bar = bar,
            )
        }
    }

    private val productMetaByBarAndName: Map<Pair<Int, String>, SmithingProductMeta> by lazy {
        productMeta.associateBy { it.bar.output.id to it.name }
    }

    val smithingButtonComponents: List<String> =
        listOf(
            "component.smithing:dagger",
            "component.smithing:axe",
            "component.smithing:chainbody",
            "component.smithing:medhelm",
            "component.smithing:darttips",
            "component.smithing:sword",
            "component.smithing:mace",
            "component.smithing:platelegs",
            "component.smithing:fullhelm",
            "component.smithing:arrowheads",
            "component.smithing:scimitar",
            "component.smithing:warhammer",
            "component.smithing:plateskirt",
            "component.smithing:squareshield",
            "component.smithing:knives",
            "component.smithing:longsword",
            "component.smithing:battleaxe",
            "component.smithing:platebody",
            "component.smithing:kiteshield",
            "component.smithing:other_1",
            "component.smithing:2h",
            "component.smithing:claws",
            "component.smithing:nails",
            "component.smithing:other_2",
            "component.smithing:other_3",
            "component.smithing:bolts",
            "component.smithing:limbs",
        )

    fun metaForName(bar: SmithingBar, itemName: String): SmithingProductMeta? =
        productMetaByBarAndName[bar.output.id to itemName]

    fun barIndexFor(bar: SmithingBar): Int? {
        for ((index, barType) in barEnum.backing) {
            if (barType?.id == bar.output.id) {
                return index
            }
        }
        return null
    }

    fun barAtIndex(index: Int): SmithingBar? =
        barEnum[index]?.internalName?.let { barsByOutput[it] }

    fun barForProduct(productName: String): SmithingBar? {
        val lower = productName.lowercase()
        allBars
            .firstOrNull { lower.startsWith(it.prefix) }
            ?.let {
                return it
            }
        return when {
            lower.endsWith("lantern frame") -> barsByOutput["obj.iron_bar"]
            lower.endsWith("lantern (unf)") -> barsByOutput["obj.steel_bar"]
            lower.endsWith("grapple tip") -> barsByOutput["obj.mithril_bar"]
            else -> null
        }
    }

    private val shayzien: Map<String, String> by lazy {
        val items =
            mapOf(
                "gloves" to
                    listOf(
                        "component.smithing:dagger",
                        "component.smithing:axe",
                        "component.smithing:chainbody",
                        "component.smithing:medhelm",
                        "component.smithing:darttips",
                    ),
                "boots" to
                    listOf(
                        "component.smithing:sword",
                        "component.smithing:mace",
                        "component.smithing:platelegs",
                        "component.smithing:fullhelm",
                        "component.smithing:arrowheads",
                    ),
                "helm" to
                    listOf(
                        "component.smithing:scimitar",
                        "component.smithing:warhammer",
                        "component.smithing:plateskirt",
                        "component.smithing:squareshield",
                        "component.smithing:knives",
                    ),
                "greaves" to
                    listOf(
                        "component.smithing:longsword",
                        "component.smithing:battleaxe",
                        "component.smithing:platebody",
                        "component.smithing:kiteshield",
                        "component.smithing:other_1",
                    ),
                "platebody" to
                    listOf(
                        "component.smithing:2h",
                        "component.smithing:claws",
                        "component.smithing:nails",
                        "component.smithing:other_2",
                        "component.smithing:other_3",
                    ),
            )

        items
            .flatMap { (type, components) ->
                components.mapIndexed { index, component ->
                    val level = index + 1
                    component to "shayzien $type ($level)"
                }
            }
            .toMap()
    }

    private val productNameOverrides: Map<Pair<String, String>, String> =
        mapOf(
            ("obj.adamantite_bar" to "component.smithing:limbs") to "adamantite limbs",
            ("obj.runite_bar" to "component.smithing:limbs") to "runite limbs",
            ("obj.adamantite_bar" to "component.smithing:nails") to "adamantite nails",
            ("obj.adamantite_bar" to "component.smithing:bolts") to "adamant bolts(unf)",
            ("obj.runite_bar" to "component.smithing:bolts") to "runite bolts (unf)",
            ("obj.iron_bar" to "component.smithing:other_2") to "oil lantern frame",
            ("obj.steel_bar" to "component.smithing:other_2") to "bullseye lantern (unf)",
            ("obj.lovakite_bar" to "component.smithing:other_3") to "shayzien body (5)",
        )

    fun typeForChild(child: String, bar: SmithingBar): String? {
        productNameOverrides[bar.output.internalName to child]?.let {
            return it
        }
        return when {
            bar.output.internalName == "obj.lovakite_bar" -> shayzien[child]
            child == "component.smithing:dagger" -> "${bar.prefix} dagger"
            child == "component.smithing:sword" -> "${bar.prefix} sword"
            child == "component.smithing:scimitar" -> "${bar.prefix} scimitar"
            child == "component.smithing:longsword" -> "${bar.prefix} longsword"
            child == "component.smithing:2h" -> "${bar.prefix} 2h sword"
            child == "component.smithing:axe" -> "${bar.prefix} axe"
            child == "component.smithing:mace" -> "${bar.prefix} mace"
            child == "component.smithing:warhammer" -> "${bar.prefix} warhammer"
            child == "component.smithing:battleaxe" -> "${bar.prefix} battleaxe"
            child == "component.smithing:claws" -> "${bar.prefix} claws"
            child == "component.smithing:chainbody" -> "${bar.prefix} chainbody"
            child == "component.smithing:platelegs" -> "${bar.prefix} platelegs"
            child == "component.smithing:plateskirt" -> "${bar.prefix} plateskirt"
            child == "component.smithing:platebody" -> "${bar.prefix} platebody"
            child == "component.smithing:nails" -> "${bar.prefix} nails"
            child == "component.smithing:medhelm" -> "${bar.prefix} med helm"
            child == "component.smithing:fullhelm" -> "${bar.prefix} full helm"
            child == "component.smithing:squareshield" -> "${bar.prefix} sq shield"
            child == "component.smithing:kiteshield" -> "${bar.prefix} kiteshield"
            child == "component.smithing:darttips" -> "${bar.prefix} dart tip"
            child == "component.smithing:arrowheads" -> "${bar.prefix} arrowtips"
            child == "component.smithing:knives" -> "${bar.prefix} knife"
            child == "component.smithing:other_1" ->
                when (bar.output.internalName) {
                    "obj.bronze_bar" -> "${bar.prefix} wire"
                    "obj.iron_bar" -> "${bar.prefix} spit"
                    "obj.steel_bar" -> "${bar.prefix} studs"
                    "obj.mithril_bar" -> "mith grapple tip"
                    else -> null
                }
            child == "component.smithing:bolts" -> "${bar.prefix} bolts (unf)"
            child == "component.smithing:limbs" -> "${bar.prefix} limbs"
            child == "component.smithing:other_3" -> "${bar.prefix} javelin tips"
            else -> null
        }
    }
}

data class SmithingProductMeta(
    val product: ItemServerType,
    val name: String,
    val level: Int,
    val barCount: Int,
    val numProduced: Int,
    val bar: SmithingBar,
)
