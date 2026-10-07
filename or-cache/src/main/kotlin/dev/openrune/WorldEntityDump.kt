package dev.openrune

import dev.openrune.definition.type.WorldEntityType
import dev.openrune.filesystem.Cache
import java.nio.file.Paths

/**
 * SERVER copies this archive unchanged; ServerCacheManager has no accessor for it.
 * `mainX/Z/Level` identify the source deck; bounds fields define the RebuildWorldEntityV4 extent.
 * Run `./gradlew :or-cache:dumpWorldEntities`.
 */
object WorldEntityDump {

    @JvmStatic
    fun main(args: Array<String>) {
        val liveDir =
            Paths.get(System.getProperty("user.dir"))
                .resolve(Paths.get(".data", "cache", "LIVE"))
                .normalize()

        val cache = Cache.load(liveDir)
        val types = mutableMapOf<Int, WorldEntityType>()
        OsrsCacheProvider.WorldEntityDecoder().load(cache, types)

        println("worldentity archive: ${types.size} definitions")
        if (types.isEmpty()) {
            println("EMPTY - the archive decoded but holds nothing; Tier 3 has no boat geometry.")
            return
        }

        println()
        println(
            "id".padEnd(6) +
                "name".padEnd(34) +
                "main(x,z,lvl)".padEnd(22) +
                "offset(x,z)".padEnd(14) +
                "size(?,z)".padEnd(12) +
                "anim".padEnd(7) +
                "active".padEnd(8) +
                "options"
        )
        for ((id, t) in types.toSortedMap()) {
            val opts = t.options.filterNotNull().filter { it.isNotBlank() }
            println(
                id.toString().padEnd(6) +
                    (t.name ?: "-").take(32).padEnd(34) +
                    "${t.mainX},${t.mainZ},${t.mainLevel}".padEnd(22) +
                    "${t.boundsOffsetX},${t.boundsOffsetZ}".padEnd(14) +
                    "${t.boundSizeZ},${t.boundsSizeZ}".padEnd(12) +
                    t.anim.toString().padEnd(7) +
                    t.active.toString().padEnd(8) +
                    opts.joinToString("/")
            )
        }

        println()
        println("--- distinct extents (the zone sizes RebuildWorldEntityV4 must carry) ---")
        types.values
            .groupingBy { "${t2s(it.boundSizeZ)}x${t2s(it.boundsSizeZ)}" }
            .eachCount()
            .toSortedMap()
            .forEach { (extent, count) -> println("  $extent -> $count definition(s)") }
    }

    private fun t2s(v: Int): String = v.toString()
}
