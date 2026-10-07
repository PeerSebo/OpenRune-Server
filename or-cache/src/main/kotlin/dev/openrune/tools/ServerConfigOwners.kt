package dev.openrune.tools

import java.nio.file.Path

internal class ServerConfigOwners {
    private val sources = mutableMapOf<Pair<String, Int>, Path>()

    fun claim(table: String, id: Int, source: Path) {
        val previous = sources.putIfAbsent(table to id, source)
        require(previous == null) {
            "Duplicate server config [$table] id=$id in $previous and $source. " +
                "Consolidate the definitions under one content owner; overlays do not merge fields."
        }
    }
}
