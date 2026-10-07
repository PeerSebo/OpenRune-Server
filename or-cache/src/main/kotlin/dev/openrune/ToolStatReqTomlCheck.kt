package dev.openrune

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.toml.rsconfig.decodeRuneScape
import dev.openrune.toml.rsconfig.decodeRuneScapeBlocks
import dev.openrune.toml.rsconfig.rsconfig
import dev.openrune.toml.tomlMapper
import dev.openrune.types.ItemServerType
import java.io.File
import kotlin.reflect.typeOf

/**
 * Checks `.data/raw-cache/server/items.toml` statreq params without writing cache data.
 * Uses the packer's `decodeRuneScapeBlocks` token resolution and `decodeRuneScape` into
 * [ItemServerType]. Unresolved RSCM tokens otherwise fail during `freshCache`, potentially
 * leaving SERVER as a partial LIVE clone.
 *
 * Run: `./gradlew :or-cache:toolStatReqTomlCheck`
 */
object ToolStatReqTomlCheck {

    @JvmStatic
    fun main(args: Array<String>) {
        ServerCacheManager.init(240)

        val statreqSkill = (1..7).map { RSCM.getRSCM("param.statreq" + it + "_skill") }
        val statreqLevel = (1..7).map { RSCM.getRSCM("param.statreq" + it + "_level") }

        val mapper = tomlMapper { rsconfig { enableConstantProvider() } }
        val plain = tomlMapper {}

        val file = File(".data/raw-cache/server/items.toml")
        val blocks = mapper.decodeRuneScapeBlocks(file.toPath()).filter { it.name == "item" }
        println("decoded blocks=" + blocks.size)

        var withStatreq = 0
        val lines = mutableListOf<String>()
        for (block in blocks) {
            val type =
                plain.decodeRuneScape(typeOf<ItemServerType>(), block.map.properties)
                    as ItemServerType
            val params = type.paramsRaw ?: continue
            if (statreqSkill.none { params.containsKey(it) }) continue
            withStatreq++
            val reqs =
                statreqSkill.indices.mapNotNull { i ->
                    val skill = params[statreqSkill[i]] ?: return@mapNotNull null
                    val level = params[statreqLevel[i]]
                    val statName =
                        (skill as? Int)?.let {
                            runCatching { RSCM.getReverseMapping(RSCMType.STAT, it) }.getOrNull()
                        } ?: "RAW<$skill>"
                    statName +
                        " " +
                        level +
                        " [skillValue=" +
                        skill +
                        " (" +
                        skill.javaClass.simpleName +
                        ") levelValue=" +
                        level +
                        " (" +
                        level?.javaClass?.simpleName +
                        ")]"
                }
            val symbol =
                runCatching { RSCM.getReverseMapping(RSCMType.OBJ, type.id) }.getOrNull() ?: "?"
            lines += "  id=" + type.id + " " + symbol + " -> " + reqs
        }
        println("item blocks carrying a statreq param after decode: " + withStatreq)
        lines.sorted().forEach(::println)
    }
}
