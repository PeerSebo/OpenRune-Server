package org.rsmod.content.generic.shops.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

internal object RewardPacksTables {
    fun packs() = dbTable("dbtable.reward_packs", serverOnly = true) {
        column("order", 0, VarType.INT)
        column("pack", 1, VarType.OBJ)
        column("kind", 2, VarType.STRING)
        column("item", 3, VarType.OBJ)
        column("count", 4, VarType.INT)
        for ((order, pack) in packs.withIndex()) {
            row("dbrow.reward_pack_" + pack.pack.removePrefix("obj.")) {
                column(0, order)
                columnRSCM(1, pack.pack)
                column(2, pack.kind)
                if (pack.item.isNotEmpty()) columnRSCM(3, pack.item)
                column(4, pack.count)
            }
        }
    }

    fun gems() = dbTable("dbtable.reward_pack_gems", serverOnly = true) {
        column("order", 0, VarType.INT)
        column("item", 1, VarType.OBJ)
        column("weight", 2, VarType.INT)
        column("draw_bound", 3, VarType.INT)
        column("rare", 4, VarType.BOOLEAN)
        column("collection_item", 5, VarType.OBJ)
        for ((order, gem) in gems.withIndex()) {
            row("dbrow.reward_pack_gem_" + gem.item.removePrefix("obj.cert_uncut_")) {
                column(0, order)
                columnRSCM(1, gem.item)
                column(2, gem.weight)
                column(3, gem.drawBound)
                column(4, gem.rare)
                if (gem.collectionItem.isNotEmpty()) columnRSCM(5, gem.collectionItem)
            }
        }
    }

    private val packs = listOf(
        Pack("obj.pack_amylase", "fixed", "obj.amylase", 100),
        Pack("obj.pack_softclay", "fixed", "obj.cert_softclay", 100),
        Pack("obj.pack_softclay_guild", "fixed", "obj.cert_softclay", 100),
        Pack("obj.star_pack_softclay", "fixed", "obj.cert_softclay", 100),
        Pack("obj.shark_lure_pack", "fixed", "obj.shark_lure", 100),
        Pack("obj.reward_gem_bag_guild", "gems", "", 40),
    )
    private val gems = listOf(
        Gem("obj.cert_uncut_onyx", 1, 100000000, true, "obj.uncut_onyx"),
        Gem("obj.cert_uncut_sapphire", 100, 200, false, ""),
        Gem("obj.cert_uncut_emerald", 69, 200, false, ""),
        Gem("obj.cert_uncut_ruby", 24, 200, false, ""),
        Gem("obj.cert_uncut_diamond", 6, 200, false, ""),
        Gem("obj.cert_uncut_dragonstone", 1, 200, false, ""),
    )

    private data class Pack(val pack: String, val kind: String, val item: String, val count: Int)
    private data class Gem(val item: String, val weight: Int, val drawBound: Int, val rare: Boolean, val collectionItem: String)
}
