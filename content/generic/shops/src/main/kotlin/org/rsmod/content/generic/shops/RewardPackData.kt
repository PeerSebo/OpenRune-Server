package org.rsmod.content.generic.shops

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.dbcol.DbColumnCodec.BooleanCodec
import dev.openrune.types.dbcol.DbColumnCodec.IntCodec
import dev.openrune.types.dbcol.DbColumnCodec.StringCodec
import dev.openrune.types.dbcol.ObjIdCodec
import dev.openrune.types.dbcol.column
import dev.openrune.types.dbcol.columnOptional
import org.rsmod.api.random.GameRandom

internal enum class RewardPackKind { Fixed, Gems }
internal data class RewardPack(val pack: String, val kind: RewardPackKind, val item: String?, val count: Int)
internal data class RewardGem(val item: String, val weight: Int, val drawBound: Int, val rare: Boolean, val collectionItem: String?)

internal object RewardPackData {
    val packs: List<RewardPack> by lazy {
        orderedShopRows("reward_packs").map { row ->
            val prefix = "dbcol.reward_packs:"
            val pack = row.column(prefix + "pack", ObjIdCodec)
            val type = requireNotNull(ServerCacheManager.getItem(pack))
            require(type.interfaceOptions[0] == "Open") { "Reward pack ${type.internalName} has no native Open action." }
            val kind = when (val kind = row.column(prefix + "kind", StringCodec)) {
                "fixed" -> RewardPackKind.Fixed
                "gems" -> RewardPackKind.Gems
                else -> error("Unknown reward pack kind $kind.")
            }
            val item = row.columnOptional(prefix + "item", ObjIdCodec)?.let(::itemSymbol)
            val count = row.column(prefix + "count", IntCodec)
            require(count > 0 && (kind == RewardPackKind.Fixed) == (item != null))
            RewardPack(itemSymbol(pack), kind, item, count)
        }.also { require(it.distinctBy { pack -> pack.pack }.size == it.size) }
    }

    val gems: List<RewardGem> by lazy {
        orderedShopRows("reward_pack_gems").map { row ->
            val prefix = "dbcol.reward_pack_gems:"
            RewardGem(
                item = itemSymbol(row.column(prefix + "item", ObjIdCodec)),
                weight = row.column(prefix + "weight", IntCodec),
                drawBound = row.column(prefix + "draw_bound", IntCodec),
                rare = row.column(prefix + "rare", BooleanCodec),
                collectionItem = row.columnOptional(prefix + "collection_item", ObjIdCodec)?.let(::itemSymbol),
            ).also { require(it.weight in 1..it.drawBound) }
        }.also { gems ->
            require(gems.count { it.rare } == 1 && gems.first().rare)
            require(gems.distinctBy { it.item }.size == gems.size)
            val normal = gems.filterNot { it.rare }
            require(normal.isNotEmpty() && normal.map { it.drawBound }.distinct().size == 1)
            require(normal.sumOf { it.weight.toLong() } == normal.first().drawBound.toLong())
        }
    }

    val fixedPacks by lazy {
        packs.filter { it.kind == RewardPackKind.Fixed }.associate { it.pack to requireNotNull(it.item) }
    }

    private val rareGem by lazy { gems.single { it.rare } }
    private val normalGems by lazy { gems.filterNot { it.rare } }

    fun rollGem(random: GameRandom): String {
        if (random.of(rareGem.drawBound) < rareGem.weight) return rareGem.item
        var roll = random.of(normalGems.first().drawBound)
        for (gem in normalGems) {
            if (roll < gem.weight) return gem.item
            roll -= gem.weight
        }
        error("Gem reward roll is outside the validated distribution.")
    }

    fun validate() {
        packs
        gems
    }

    private fun itemSymbol(id: Int): String {
        requireNotNull(ServerCacheManager.getItem(id))
        return RSCM.getReverseMapping(RSCMType.OBJ, id)
    }
}
