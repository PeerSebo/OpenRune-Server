package org.rsmod.content.minigames.giantsfoundry.configs

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.StructType
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.dbcol.DbColumnCodec.EnumTypeIdCodec
import dev.openrune.types.dbcol.DbColumnCodec.IntCodec
import dev.openrune.types.dbcol.DbColumnCodec.StringCodec
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.types.dbcol.ObjIdCodec
import dev.openrune.types.dbcol.column
import dev.openrune.types.dbcol.multiColumn
import org.rsmod.content.skills.smithing.util.SmithingData

object FoundryData {
    private val native by lazy { DbHelper.table("dbtable.giants_foundry_native_catalogue").single() }

    val words: Map<Int, String> by lazy {
        nativeEnum("words").values.mapValues { (_, value) -> value as String }.also {
            require(it.keys == (1..6).toSet())
        }
    }

    val temperatureSteps: List<Int> by lazy {
        DbHelper.table("dbtable.giants_foundry_temperature").single()
            .multiColumn("dbcol.giants_foundry_temperature:steps", IntCodec)
            .also {
                require(it.isNotEmpty() && it.all { value -> value > 0 })
                require(it.zipWithNext().all { (left, right) -> left < right })
            }
    }

    fun temperatureStep(elapsed: Int): Int {
        require(elapsed >= 0)
        return temperatureSteps[elapsed.coerceAtMost(temperatureSteps.lastIndex)]
    }

    val moulds: List<FoundryMould> by lazy {
        FoundryMouldPart.entries.flatMap { part ->
            nativeEnum(part.name.lowercase()).values.entries.sortedBy { it.key }.map { (index, value) ->
                val structId = value as Int
                val struct = requireNotNull(ServerCacheManager.getStruct(structId))
                val unlock = if (index <= 5) "varbit.giants_foundry_unlocked_mould_${part.key}_${12 - index}" else null
                unlock?.let { requireNotNull(ServerCacheManager.getVarbit(it.asRSCM())) }
                FoundryMould(
                    part, index, structId,
                    struct.getString(param("mould_name")),
                    struct.requiredInt("mould_level"),
                    struct.requiredInt("mould_width"),
                    struct.requiredInt("mould_weight"),
                    struct.requiredInt("mould_profile"),
                    unlock,
                ).also { require(it.name.isNotBlank() && it.level in 1..99) }
            }.also { require(it.map(FoundryMould::index) == (1..11).toList()) }
        }
    }

    val rewards: List<FoundryReward> by lazy {
        nativeEnum("rewards").values.entries.sortedBy { it.key }.map { (index, value) ->
            val struct = requireNotNull(ServerCacheManager.getStruct(value as Int))
            val itemId = struct.params?.get(param("reward_item")) as? Int
            val mouldId = struct.params?.get(param("reward_mould")) as? Int
            require((itemId == null) != (mouldId == null))
            val item = itemId?.let {
                requireNotNull(ServerCacheManager.getItem(it))
                RSCM.getReverseMapping(RSCMType.OBJ, it)
            }
            val mould = mouldId?.let { moulds.single { mould -> mould.structId == it } }
            FoundryReward(index, item, mould, struct.requiredInt("reward_price")).also { require(it.cost > 0) }
        }.also { require(it.map(FoundryReward::index) == (1..24).toList()) }
    }

    val metals: List<FoundryMetal> by lazy {
        DbHelper.table("dbtable.giants_foundry_metals").map { row ->
            val prefix = "dbcol.giants_foundry_metals:"
            val itemId = row.column(prefix + "bar", ObjIdCodec)
            val bar = RSCM.getReverseMapping(RSCMType.OBJ, itemId)
            val smithing = requireNotNull(SmithingData.barsByOutput[bar])
            val count = row.column(prefix + "count_varbit", IntCodec)
            requireNotNull(ServerCacheManager.getVarbit(count))
            FoundryMetal(
                row.column(prefix + "tier", IntCodec),
                row.column(prefix + "key", StringCodec),
                bar, smithing.levelReq.coerceAtLeast(15),
                RSCM.getReverseMapping(RSCMType.VARBIT, count),
            )
        }.sortedBy { it.tier }.also {
            require(it.map(FoundryMetal::tier) == (1..6).toList())
            require(it.map(FoundryMetal::key).distinct().size == it.size)
        }
    }

    val uniformPieces: List<List<String>> by lazy {
        rewards.filter { it.index in 5..8 }.map { reward ->
            val item = requireNotNull(reward.item)
            if (reward.index == 8) listOf(item, "obj.smithing_uniform_gloves_ice") else listOf(item)
        }.also { require(it.size == 4) }
    }

    val materials: Map<Int, FoundryMaterial> by lazy {
        val byBar = metals.associateBy { it.bar }
        buildMap {
            for (metal in metals) put(metal.bar.asRSCM(), FoundryMaterial(metal, 1))
            for (product in SmithingData.productMeta) {
                val metal = byBar[product.bar.output.internalName] ?: continue
                if (product.numProduced != 1 || product.barCount <= 1) continue
                put(product.product.id, FoundryMaterial(metal, product.barCount - 1))
            }
        }
    }

    fun mould(part: FoundryMouldPart, index: Int): FoundryMould? = moulds.find { it.part == part && it.index == index }

    fun mouldScore(firstWord: Int, secondWord: Int, selections: List<FoundryMould>): Int {
        require(firstWord in words && secondWord in words && firstWord != secondWord)
        require(selections.map { it.part }.toSet() == FoundryMouldPart.entries.toSet() && selections.size == 3)
        return selections.sumOf { it.score(firstWord) + it.score(secondWord) }
    }

    private fun nativeEnum(column: String) = requireNotNull(ServerCacheManager.getEnum(
        native.column("dbcol.giants_foundry_native_catalogue:$column", EnumTypeIdCodec),
    ))

    private fun param(key: String) = native.column("dbcol.giants_foundry_native_catalogue:${key}_param", IntCodec)

    private fun StructType.requiredInt(key: String): Int = requireNotNull(params?.get(param(key)) as? Int)
}

data class FoundryMetal(val tier: Int, val key: String, val bar: String, val level: Int, val countVarbit: String)

data class FoundryMaterial(val metal: FoundryMetal, val bars: Int)
