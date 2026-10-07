package dev.openrune

import java.io.File

object ExamineAudit {

    private fun csv(name: String): Map<Int, String> {
        val nested = File("../.data/raw-cache/examines/$name")
        val file = if (nested.exists()) nested else File(".data/raw-cache/examines/$name")
        if (!file.exists()) return emptyMap()
        return file
            .readText()
            .lineSequence()
            .filter { it.isNotBlank() }
            .associate { line ->
                val (idStr, text) = line.split(",", limit = 2)
                idStr.trim().toInt() to text.trim().removeSurrounding("\"")
            }
    }

    private fun blank(text: String?): Boolean =
        text.isNullOrEmpty() || text.equals("null", ignoreCase = true)

    @JvmStatic
    fun main(args: Array<String>) {
        ServerCacheManager.init(240)

        val npcCsv = csv("npcs.csv")
        val locCsv = csv("locs.csv")
        val itemCsv = csv("items.csv")

        val npcs = ServerCacheManager.getNpcs()
        val locs = ServerCacheManager.getObjects()
        val items = ServerCacheManager.getItems()

        fun report(
            label: String,
            total: Int,
            withText: Int,
            csvSize: Int,
            recoverable: Int,
        ) {
            println(
                "$label: packed=$total withExamine=$withText " +
                    "(${percent(withText, total)}) missing=${total - withText} " +
                    "| csvRows=$csvSize csvCoversMissing=$recoverable"
            )
        }

        val npcWith = npcs.values.count { !blank(it.examine) }
        val npcRecoverable = npcs.filter { blank(it.value.examine) && npcCsv[it.key] != null }.size
        report("npc ", npcs.size, npcWith, npcCsv.size, npcRecoverable)

        val locWith = locs.values.count { !blank(it.desc) }
        val locRecoverable = locs.filter { blank(it.value.desc) && locCsv[it.key] != null }.size
        report("loc ", locs.size, locWith, locCsv.size, locRecoverable)

        val itemWith = items.values.count { !blank(it.examine) }
        val itemRecoverable =
            items.filter { blank(it.value.examine) && itemCsv[it.key] != null }.size
        report("item", items.size, itemWith, itemCsv.size, itemRecoverable)

        // ~17k of the 34k item ids are unused placeholders named "null"; counting them as gaps
        // makes the item coverage look far worse than a player can ever observe.
        val named = items.filterValues { it.name.isNotEmpty() && !it.name.equals("null", true) }
        val namedMissing = named.values.count { blank(it.examine) }
        val namedCert = named.filter { blank(it.value.examine) && it.value.certlink > 0 }.size
        println(
            "item(named only): packed=" +
                named.size +
                " withExamine=" +
                (named.size - namedMissing) +
                " missing=" +
                namedMissing +
                " (of which noted/cert=" +
                namedCert +
                ")"
        )

        val namedNpcs = npcs.filterValues { it.name.isNotEmpty() && !it.name.equals("null", true) }
        val namedNpcGap = namedNpcs.count { blank(it.value.examine) && npcCsv[it.key] == null }
        println(
            "npc(named only): packed=" +
                namedNpcs.size +
                " projectedWithExamine=" +
                (namedNpcs.size - namedNpcGap) +
                " stillMissing=" +
                namedNpcGap
        )

        val namedLocs = locs.filterValues { it.name.isNotEmpty() && !it.name.equals("null", true) }
        val namedLocGap = namedLocs.count { blank(it.value.desc) && locCsv[it.key] == null }
        println(
            "loc(named only): packed=" +
                namedLocs.size +
                " projectedWithExamine=" +
                (namedLocs.size - namedLocGap) +
                " stillMissing=" +
                namedLocGap
        )

        println()
        println("--- sample: npcs the csv names but the packed type does not ---")
        npcs.entries
            .asSequence()
            .filter { blank(it.value.examine) && npcCsv[it.key] != null }
            .take(10)
            .forEach { println("  npc ${it.key} '${it.value.name}' csv='${npcCsv[it.key]}'") }

        println("--- sample: locs the csv names but the packed type does not ---")
        locs.entries
            .asSequence()
            .filter { blank(it.value.desc) && locCsv[it.key] != null }
            .take(10)
            .forEach { println("  loc ${it.key} '${it.value.name}' csv='${locCsv[it.key]}'") }

        println("--- sample: named items with no examine at all ---")
        named.entries
            .asSequence()
            .filter { blank(it.value.examine) }
            .take(10)
            .forEach { println("  item ${it.key} '${it.value.name}' ex='${it.value.examine}'") }
    }

    private fun percent(part: Int, whole: Int): String =
        if (whole == 0) "n/a" else String.format("%.1f%%", part * 100.0 / whole)
}
