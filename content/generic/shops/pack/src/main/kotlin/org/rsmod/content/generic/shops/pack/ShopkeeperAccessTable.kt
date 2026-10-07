package org.rsmod.content.generic.shops.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

internal object ShopkeeperAccessTable {
    fun build() = dbTable("dbtable.shopkeeper_access", serverOnly = true) {
        column("order", 0, VarType.INT)
        column("key", 1, VarType.STRING)
        column("stalls", 2, VarType.LOC)
        column("stall_keys", 3, VarType.STRING)
        column("refusal_cycles", 4, VarType.INT)
        for ((order, rule) in rules.withIndex()) {
            row("dbrow.shopkeeper_access_" + rule.key.replace('.', '_')) {
                column(0, order)
                column(1, rule.key)
                if (rule.stalls.isNotEmpty()) {
                    columnRSCM(2, *rule.stalls)
                    column(3, *rule.stalls)
                }
                column(4, rule.refusalCycles)
            }
        }
    }

    private val rules = listOf(
        Rule("inv.bakery", 1500, "loc.cakethiefstall"),
        Rule("inv.bakery2", 1500, "loc.cakethiefstall"),
        Rule("inv.bakery3", 1500, "loc.hos_stall_bread"),
        Rule("inv.ardougnegemstall", 1000, "loc.gemthiefstall"),
        Rule("inv.kourendgemstall", 1000, "loc.gemthiefstall"),
        Rule("inv.spicestall", 600, "loc.spicethiefstall"),
        Rule("inv.viking_furshop", 1000, "loc.furthiefstall"),
        Rule("inv.keldagrim_silver_stall", 1000, "loc.silverthiefstall"),
        Rule("inv.silverstall", 1000, "loc.dwarf_market_silver"),
        Rule("inv.keldagrim_bread_stall", 1000, "loc.dwarf_market_bakery"),
        Rule("inv.keldagrim_gem_stall", 1000, "loc.dwarf_market_gems"),
        Rule("inv.keldagrim_crafting_stall", 1000, "loc.dwarf_market_crafting"),
        Rule("inv.seed_stall", 1000, "loc.seed_stall"),
        Rule("inv.teashop", 1000, "loc.tea_stall"),
        Rule("inv.fortis_shop_baker", 1000, "loc.fortis_market_stall_bakers"),
        Rule("inv.fortis_shop_fur", 1000, "loc.fortis_market_stall_fur"),
        Rule("inv.fortis_shop_gems", 1000, "loc.fortis_market_stall_gems"),
        Rule("inv.fortis_shop_silk", 1000, "loc.fortis_market_stall_silk"),
        Rule("inv.fortis_shop_spices", 1000, "loc.fortis_market_stall_spice"),
        Rule("inv.misc_fishmonger", 1000, "loc.misc_fish_market"),
        Rule("inv.misc_greengrocer", 1500, "loc.misc_veg_market"),
        Rule("inv.etc_fishmonger", 1000, "loc.etc_fish_market"),
        Rule("inv.etc_greengrocer", 1500, "loc.etc_veg_market"),
        Rule("inv.warrens_fishmonger", 1000, "loc.fish_stall_warrens"),
        Rule("inv.xbows_shop_addy", 1000, "loc.xbows_dwarf_market"),
        Rule("profile.prif_spice", 1000, "loc.prif_marketstall_spice"),
        Rule("profile.prif_silver", 1000, "loc.prif_marketstall_silver"),
        Rule("profile.prif_gem", 1000, "loc.prif_marketstall_gem"),
        Rule("inv.blurberrybar", 0),
        Rule("inv.death_pub", 0),
        Rule("inv.royal_bar", 0),
        Rule("inv.shayzien_pub", 0),
        Rule("inv.pub_cam_torum", 0),
        Rule("inv.pub_fortis_1", 0),
        Rule("inv.pub_fortis_2", 0),
        Rule("inv.pub_aldarin", 0),
        Rule("inv.pub_mistrock", 0),
        Rule("inv.pub_quetzacalli", 0),
        Rule("inv.pub_auburnvale", 0),
        Rule("inv.pub_nemus", 0),
        Rule("inv.pub_port_roberts", 0),
        Rule("inv.fd_bandit_bar", 0),
        Rule("inv.feud_alispub", 0),
        Rule("inv.pub_deepfin", 0),
        Rule("profile.lovakengj_bartender", 0),
        Rule("profile.hosidius_bartender_1", 0),
    )

    private class Rule(val key: String, val refusalCycles: Int, vararg val stalls: String)
}
