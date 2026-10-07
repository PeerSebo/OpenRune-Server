package dev.openrune

import dev.openrune.definition.type.ItemType
import dev.openrune.filesystem.Cache
import dev.openrune.rscm.RSCM
import java.io.File
import java.nio.file.Paths

object ItemTomlParamAudit {

    @JvmStatic
    fun main(args: Array<String>) {
        ServerCacheManager.init(240)

        val liveCache =
            Cache.load(Paths.get(System.getProperty("user.dir"), ".data", "cache", "LIVE"))
        val live = mutableMapOf<Int, ItemType>()
        OsrsCacheProvider.ItemDecoder(240).load(liveCache, live)

        val file = File(".data/raw-cache/server/items.toml")
        var currentId: Int? = null
        var inParams = false
        var blocks = 0
        var entries = 0
        var same = 0
        var changed = 0
        var added = 0
        var unresolvedItem = 0
        var unresolvedValue = 0
        val changedSamples = mutableListOf<String>()
        val addedByParam = sortedMapOf<Int, Int>()

        fun resolve(raw: String): Any? {
            val trimmed = raw.trim()
            if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                val text = trimmed.removeSurrounding("\"")
                val prefix = text.substringBefore('.', "")
                return if (prefix.isNotEmpty() && text.contains('.')) {
                    runCatching { RSCM.getRSCM(text) }.getOrNull() ?: text
                } else {
                    text
                }
            }
            return trimmed.toIntOrNull() ?: trimmed.toBooleanStrictOrNull()?.let { if (it) 1 else 0 }
        }

        for (line in file.readLines()) {
            val text = line.trim()
            when {
                text == "[[item]]" -> {
                    blocks++
                    currentId = null
                    inParams = false
                }
                text == "[item.params]" -> inParams = true
                text.startsWith("id = ") && !inParams -> {
                    val symbol = text.removePrefix("id = ").trim().removeSurrounding("\"")
                    currentId = runCatching { RSCM.getRSCM(symbol) }.getOrNull()
                    if (currentId == null) unresolvedItem++
                }
                text.startsWith("[") -> inParams = false
                inParams && text.startsWith("\"param.") -> {
                    entries++
                    val key = text.substringBefore("\"=", "").removePrefix("\"")
                    val paramId = runCatching { RSCM.getRSCM(key) }.getOrNull()
                    val value = resolve(text.substringAfter("\"=", ""))
                    val liveParams = currentId?.let { live[it]?.params }
                    when {
                        paramId == null || value == null -> unresolvedValue++
                        liveParams == null || !liveParams.containsKey(paramId) -> {
                            added++
                            addedByParam[paramId] = (addedByParam[paramId] ?: 0) + 1
                        }
                        liveParams[paramId].toString() == value.toString() -> same++
                        else -> {
                            changed++
                            if (changedSamples.size < 15) {
                                changedSamples +=
                                    "item=$currentId param=$paramId live=${liveParams[paramId]} toml=$value"
                            }
                        }
                    }
                }
            }
        }

        println("items.toml blocks=$blocks paramEntries=$entries")
        println("  duplicate of LIVE value : $same")
        println("  would CHANGE LIVE value : $changed")
        println("  would ADD a new key     : $added")
        println("  unresolved item symbol  : $unresolvedItem")
        println("  unresolved param/value  : $unresolvedValue")
        println()
        println("--- would-change samples ---")
        changedSamples.forEach { println("  $it") }
        println()
        println("--- would-add, by param id ---")
        addedByParam.entries.sortedByDescending { it.value }.take(25).forEach { (id, count) ->
            println("  param $id: $count")
        }
    }
}
