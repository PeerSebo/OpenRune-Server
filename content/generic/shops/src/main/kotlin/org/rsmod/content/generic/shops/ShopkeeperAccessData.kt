package org.rsmod.content.generic.shops

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.dbcol.DbColumnCodec.IntCodec
import dev.openrune.types.dbcol.DbColumnCodec.StringCodec
import dev.openrune.types.dbcol.LocIdCodec
import dev.openrune.types.dbcol.column
import dev.openrune.types.dbcol.multiColumnOptional

internal data class ShopkeeperAccessRule(val key: String, val stalls: Set<String>, val refusalCycles: Int)

internal object ShopkeeperAccessData {
    val rules: List<ShopkeeperAccessRule> by lazy {
        val bindings = ShopkeeperCatalog.shops.map { it.key }.toSet()
        orderedShopRows("shopkeeper_access").map { row ->
            val prefix = "dbcol.shopkeeper_access:"
            val key = row.column(prefix + "key", StringCodec)
            require(key in bindings) { "Unknown merchant binding $key in access row ${row.id}." }
            val stalls = row.multiColumnOptional(prefix + "stalls", LocIdCodec).filterNotNull()
            val names = row.multiColumnOptional(prefix + "stall_keys", StringCodec).filterNotNull()
            require(stalls.size == names.size && names.distinct().size == names.size)
            for ((index, name) in names.withIndex()) {
                require(name.asRSCM(RSCMType.LOC) == stalls[index])
                requireNotNull(ServerCacheManager.getObject(stalls[index]))
            }
            val refusal = row.column(prefix + "refusal_cycles", IntCodec)
            require(if (stalls.isEmpty()) refusal == 0 else refusal > 0)
            ShopkeeperAccessRule(key, names.toSet(), refusal)
        }.also { require(it.distinctBy { rule -> rule.key }.size == it.size) }
    }

    val byKey by lazy { rules.associateBy { it.key } }
    val stalls by lazy { rules.filter { it.stalls.isNotEmpty() }.associate { it.key to it.stalls } }
}
