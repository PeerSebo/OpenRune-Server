package dev.openrune

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType

/**
 * Find equippable items without statreq parameters and print their levelrequire for comparison.
 * levelrequire gates tool use, not equipping: an axe may require Woodcutting to chop but still
 * lack an Attack requirement to wield. [StatReqAudit] checks the decoding of existing statreqs.
 *
 * Run: `./gradlew :or-cache:toolStatReqAudit`
 */
object ToolStatReqAudit {

    @JvmStatic
    fun main(args: Array<String>) {
        ServerCacheManager.init(240)

        val items = ServerCacheManager.getItems()
        val equippable = items.filterValues { it.wearpos1 != -1 }

        val statreqSkill = (1..7).map { RSCM.getRSCM("param.statreq" + it + "_skill") }
        val statreqLevel = (1..7).map { RSCM.getRSCM("param.statreq" + it + "_level") }
        val levelRequire = RSCM.getRSCM("param.levelrequire")

        fun ItemServerType.gated(): Boolean =
            statreqSkill.any { paramsRaw?.containsKey(it) == true }

        fun ItemServerType.levelRequire(): Int? = paramsRaw?.get(levelRequire) as? Int

        fun ItemServerType.requirements(): List<String> =
            statreqSkill.indices.mapNotNull { i ->
                val skill = paramsRaw?.get(statreqSkill[i]) ?: return@mapNotNull null
                val level = paramsRaw?.get(statreqLevel[i])
                "skill=" + skill + " level=" + level
            }

        fun symbol(id: Int): String =
            runCatching { RSCM.getReverseMapping(RSCMType.OBJ, id) }.getOrNull() ?: "?"

        val gated = equippable.values.filter { it.gated() }
        val ungated = equippable.values.filter { !it.gated() }
        println("equippable=" + equippable.size + " gated=" + gated.size + " ungated=" + ungated.size)

        println()
        println("--- ungated equippable items by weaponCategory ---")
        val byCategory = ungated.groupBy { it.weaponCategory }
        val gatedByCategory = gated.groupBy { it.weaponCategory }
        byCategory.entries
            .sortedByDescending { it.value.size }
            .forEach { (category, list) ->
                val gatedCount = gatedByCategory[category]?.size ?: 0
                println("  category=" + category + " ungated=" + list.size + " gated=" + gatedCount)
            }

        println()
        println("--- StatType resolution for the stats this audit gates on ---")
        for (statId in listOf(0, 1, 2, 4, 16, 18)) {
            val stat = ServerCacheManager.getStats(statId)
            println("  stat " + statId + " -> " + (stat?.displayName ?: "NULL (requirement would be skipped)"))
        }

        println()
        println("--- families named like tools ---")
        val toolWords =
            listOf("axe", "pickaxe", "harpoon", "hammer", "knife", "sickle", "trident", "net")
        for (word in toolWords) {
            val matches =
                equippable.values.filter { it.name.lowercase().contains(word) }.sortedBy { it.id }
            if (matches.isEmpty()) continue
            val ungatedMatches = matches.filter { !it.gated() }
            println()
            println("  \"" + word + "\": " + matches.size + " equippable, " + ungatedMatches.size + " ungated")
        }

        println()
        println("--- every item in a tool weaponCategory, gated or not ---")
        val toolCategories = listOf("Axe", "Pickaxe", "Harpoon")
        for (category in toolCategories) {
            val list = equippable.values.filter { it.weaponCategory?.toString() == category }
            println("  " + category + ": " + list.size + " items")
            for (type in list.sortedBy { it.id }) {
                val reqs = type.requirements()
                val flag = if (reqs.isEmpty()) "UNGATED" else "gated  "
                println(
                    "    " + flag + " id=" + type.id + " " + symbol(type.id) + " | " + type.name +
                        " levelrequire=" + type.levelRequire() + " " + reqs
                )
            }
        }

        println()
        println("--- harpoon/net/tool-shaped items outside those categories ---")
        val extra =
            equippable.values.filter {
                val n = it.name.lowercase()
                (n.contains("harpoon") || n.contains("sickle") || n.contains("hammer") ||
                    n.contains("machete") || n.contains("secateurs") || n.contains("rake") ||
                    n.contains("spade") || n.contains("bullseye") || n.contains("crystal saw"))
            }
        for (type in extra.sortedBy { it.id }) {
            val reqs = type.requirements()
            val flag = if (reqs.isEmpty()) "UNGATED" else "gated  "
            println(
                "    " + flag + " id=" + type.id + " " + symbol(type.id) + " | " + type.name + " cat=" + type.weaponCategory +
                    " levelrequire=" + type.levelRequire() + " " + reqs
            )
        }
    }
}
