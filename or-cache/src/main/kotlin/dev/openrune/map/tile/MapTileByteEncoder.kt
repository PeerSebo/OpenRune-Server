package dev.openrune.map.tile

import dev.openrune.cache.MAPS
import dev.openrune.filesystem.Cache
import dev.openrune.map.util.toReadableByteArray
import io.netty.buffer.Unpooled
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator
import org.rsmod.map.square.MapSquareKey

/*
 * Passes raw `m[x]_[z]` bytes through: tile-file merging is unsupported, so decoding and
 * re-encoding these large files is unnecessary.
 */
public object MapTileByteEncoder {
    public fun encodeAll(cache: Cache, definitions: Map<MapSquareKey, MapTileByteDefinition>) {
        val archive = MAPS
        for ((key, definition) in definitions) {
            val group = "m${key.x}_${key.z}"
            val newBuf = Unpooled.wrappedBuffer(definition.data)
            cache.write(archive, group, newBuf.toReadableByteArray())
        }
    }
}
