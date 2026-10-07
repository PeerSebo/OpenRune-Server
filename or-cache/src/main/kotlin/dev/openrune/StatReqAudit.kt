package dev.openrune

import dev.openrune.definition.type.ItemType
import dev.openrune.filesystem.Cache
import dev.openrune.rscm.RSCM
import dev.openrune.types.ItemServerType
import dev.openrune.types.StatType
import java.nio.file.Paths

object StatReqAudit {

    @JvmStatic
    fun main(args: Array<String>) {
        ServerCacheManager.init(240)

        val items = ServerCacheManager.getItems()
        val equippable = items.filterValues { it.wearpos1 != -1 }
        println("packed items=${items.size} equippable=${equippable.size}")

        val tiers =
            (1..7).map {
                RSCM.getRSCM("param.statreq${it}_skill") to
                    RSCM.getRSCM("param.statreq${it}_level")
            }

        println()
        println("--- param resolution (a null here is a boot-time crash in BaseParams) ---")
        val names =
            (1..7).flatMap { listOf("param.statreq${it}_skill", "param.statreq${it}_level") } +
                listOf("param.statreq_failmessage1", "param.statreq_failmessage2")
        for (name in names) {
            val id = runCatching { RSCM.getRSCM(name) }.getOrNull()
            val packed = id?.let { ServerCacheManager.getParam(it) }
            val typed =
                when {
                    id == null -> "no rscm symbol"
                    name.endsWith("_skill") ->
                        runCatching { ParamTypes.byId(id, StatType::class) }
                            .exceptionOrNull()
                            ?.let { "THREW ${it.message}" } ?: "ok"
                    else ->
                        runCatching { ParamTypes.byId(id, Int::class) }
                            .exceptionOrNull()
                            ?.let { "THREW ${it.message}" } ?: "ok"
                }
            println("  $name -> id=$id packed=${packed != null} byId=$typed")
        }

        println()
        println("--- raw vs typed, per tier ---")
        for ((index, pair) in tiers.withIndex()) {
            val (skillId, levelId) = pair
            val raw = equippable.filterValues { it.paramsRaw?.containsKey(skillId) == true }
            val skillParam = ParamTypes.byId(skillId, StatType::class)
            var typedOk = 0
            var typedNull = 0
            var typedThrew = 0
            for (type in raw.values) {
                try {
                    if (type.paramOrNull<StatType>(skillParam) != null) typedOk++ else typedNull++
                } catch (_: Throwable) {
                    typedThrew++
                }
            }
            println(
                "  tier ${index + 1} (skill=$skillId level=$levelId): raw=${raw.size} " +
                    "typedOk=$typedOk typedNull=$typedNull typedThrew=$typedThrew"
            )
        }

        println()
        println("--- samples ---")
        for (sample in listOf(1127, 1333, 11832, 4151, 9747, 8839)) {
            val type = items[sample] ?: continue
            val text =
                tiers.mapNotNull { (skillId, levelId) ->
                    val stat = type.paramOrNull<StatType>(ParamTypes.byId(skillId, StatType::class))
                    val level = type.paramOrNull<Int>(ParamTypes.byId(levelId, Int::class))
                    stat?.let { "${it.displayName} $level" }
                }
            println("  $sample ${type.name}: $text")
        }

        println()
        println("--- enforcement coverage ---")
        fun gated(type: ItemServerType, pairs: List<Pair<Int, Int>>): Int =
            pairs.count { type.paramsRaw?.containsKey(it.first) == true }

        val gatedItemsTier12 = equippable.values.count { gated(it, tiers.take(2)) > 0 }
        val gatedItemsAll = equippable.values.count { gated(it, tiers) > 0 }
        val entriesTier12 = equippable.values.sumOf { gated(it, tiers.take(2)) }
        val entriesAll = equippable.values.sumOf { gated(it, tiers) }
        println("equippable items with any requirement: tiers1-2=$gatedItemsTier12 all=$gatedItemsAll")
        println("requirement entries: tiers1-2=$entriesTier12 all=$entriesAll")

        val missingLevel =
            equippable.values.sumOf { type ->
                tiers.count { (skillId, levelId) ->
                    type.paramsRaw?.containsKey(skillId) == true &&
                        type.paramsRaw?.containsKey(levelId) != true
                }
            }
        println("skill params with no matching level param (would gate at level 0): $missingLevel")

        val higherTierOnly =
            equippable.values.filter { gated(it, tiers.drop(2)) > 0 && gated(it, tiers.take(2)) == 0 }
        println("items whose ONLY requirement is tier 3+: ${higherTierOnly.size}")

        val higherTier = equippable.values.filter { gated(it, tiers.drop(2)) > 0 }
        println("items carrying a tier 3+ requirement: ${higherTier.size}")
        println("  " + higherTier.map { it.name }.sorted().joinToString(", "))

        println()
        println("--- packed param maps vs the LIVE base ---")
        val liveCache =
            Cache.load(Paths.get(System.getProperty("user.dir"), ".data", "cache", "LIVE"))
        val liveItems = mutableMapOf<Int, ItemType>()
        OsrsCacheProvider.ItemDecoder(240).load(liveCache, liveItems)
        var identical = 0
        var differs = 0
        var packedOnly = 0
        for ((id, packed) in items) {
            val live = liveItems[id]
            when {
                live == null -> packedOnly++
                (live.params ?: emptyMap<Int, Any>()) ==
                    (packed.paramsRaw ?: emptyMap<Int, Any>()) -> identical++
                else -> {
                    differs++
                    if (differs <= 5) println("  differs id=$id ${packed.name}")
                }
            }
        }
        println("identical=$identical differs=$differs packedOnly=$packedOnly")
    }
}
