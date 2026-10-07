package org.rsmod.content.generic.shops.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

internal object RewardShopsTable {
    fun build() = dbTable("dbtable.reward_merchant_shops", serverOnly = true) {
        column("order", 0, VarType.INT)
        column("key", 1, VarType.STRING)
        column("title", 2, VarType.STRING)
        column("currency", 3, VarType.STRING)
        column("allow_selling", 4, VarType.BOOLEAN)
        column("display_template", 5, VarType.INV)
        column("sell_multiplier", 6, VarType.INT)
        column("buy_multiplier", 7, VarType.INT)
        column("delta", 8, VarType.INT)
        column("restock_cycles", 9, VarType.INT)
        column("items", 10, VarType.OBJ)
        column("prices", 11, VarType.INT)
        column("counts", 12, VarType.INT)
        val shops = listOf("grace" to grace, "belona" to belona, "alry" to alry, "alry_trained" to alryTrained)
        for ((order, entry) in shops.withIndex()) {
            val (key, shop) = entry
            row("dbrow.reward_merchant_" + key) {
                column(0, order)
                column(1, key)
                column(2, shop.title)
                column(3, shop.currency)
                column(4, shop.allowSelling)
                columnRSCM(5, "inv.generalshop1")
                column(6, 1000)
                column(7, if (shop.allowSelling) 800 else 0)
                column(8, 0)
                column(9, 1)
                columnRSCM(10, *shop.items.map { it.item }.toTypedArray())
                column(11, *shop.items.map { it.price }.toTypedArray<Any>())
                column(12, *shop.items.map { it.count }.toTypedArray<Any>())
            }
        }
    }

    private val grace = RewardStock(
        "Grace's Graceful Clothing", "currency.marks_of_grace", true,
        listOf(
            RewardItem("obj.graceful_hood", 35),
            RewardItem("obj.graceful_top", 55),
            RewardItem("obj.graceful_legs", 60),
            RewardItem("obj.graceful_gloves", 30),
            RewardItem("obj.graceful_boots", 40),
            RewardItem("obj.graceful_cape", 40),
            RewardItem("obj.pack_amylase", 10, 1000),
        ),
    )
    private val belona = RewardStock(
        "Mining Guild Mineral Exchange", "currency.unidentified_minerals", true,
        listOf(
            RewardItem("obj.mguild_gloves", 40),
            RewardItem("obj.mguild_gloves_superior", 100),
            RewardItem("obj.pack_softclay_guild", 10, 1000),
            RewardItem("obj.reward_gem_bag_guild", 20),
        ),
    )
    private val alryItems = listOf(
        RewardItem("obj.fishingrod_pearl", 100, 1000),
        RewardItem("obj.fishingrod_pearl_fly", 120, 1000),
        RewardItem("obj.fish_sack", 500, 1000),
        RewardItem("obj.trawler_reward_hat", 50, 1000),
        RewardItem("obj.trawler_reward_top", 50, 1000),
        RewardItem("obj.trawler_reward_legs", 50, 1000),
        RewardItem("obj.trawler_reward_boots", 50, 1000),
        RewardItem("obj.shark_lure_pack", 1, 10000),
    )
    private val alry = RewardStock(
        "Alry the Angler's Angling Accessories", "currency.molch_pearls", false, alryItems,
    )
    private val alryTrained = alry.copy(
        items = alryItems + RewardItem("obj.fishingrod_pearl_brut", 150, 1000),
    )
    private data class RewardItem(val item: String, val price: Int, val count: Int = 100)
    private data class RewardStock(val title: String, val currency: String, val allowSelling: Boolean, val items: List<RewardItem>)
}
