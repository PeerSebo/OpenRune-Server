package org.rsmod.content.skills.sailing

import dev.openrune.cache.MAPS
import dev.openrune.definition.codec.OverlayCodec
import dev.openrune.definition.codec.TextureCodec
import dev.openrune.definition.codec.WorldEntityCodec
import dev.openrune.filesystem.Cache
import dev.openrune.gamevals.GameValProvider
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.tile.MapTileDefinition
import io.netty.buffer.Unpooled
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.nio.file.Paths
import java.util.BitSet
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.game.entity.WorldEntity
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

// Overlay water flags are unset; derive navigable water from named textures instead.
@Singleton
internal class SailingSeaMap internal constructor(
    private val collision: CollisionFlagMap,
    private val waterSquares: MutableMap<Int, BitSet> = mutableMapOf(),
    private val cacheRevision: Int = 241,
) {
    @Inject constructor(collision: CollisionFlagMap, config: ServerConfig) :
        this(collision, mutableMapOf(), config.revision)

    private var initialized = false
    private val hullBounds = mutableMapOf<Int, HullBounds>()

    fun initialize() {
        if (initialized) return
        val live = Cache.load(Paths.get(".data", "cache", "LIVE"))
        val nativeBounds = mutableMapOf<Int, HullBounds>()
        val waterOverlays = try {
            val worldEntityCodec = WorldEntityCodec()
            for (id in live.files(2, 72)) {
                val bytes = live.data(2, 72, id) ?: continue
                val type = worldEntityCodec.loadData(id, bytes)
                if (type.boundSizeZ <= 0 || type.boundsSizeZ <= 0) continue
                nativeBounds[id] = HullBounds(
                    width = (type.boundSizeZ + 127) / 128,
                    length = (type.boundsSizeZ + 127) / 128,
                    offsetX = type.boundsOffsetX,
                    offsetZ = type.boundsOffsetZ,
                )
            }
            ordinaryWaterOverlays(live, cacheRevision)
        } finally {
            live.close()
        }
        check(waterOverlays.isNotEmpty()) { "No named ordinary-water overlays in the LIVE cache." }
        val server = Cache.load(Paths.get(".data", "cache", "SERVER"))
        val loaded = mutableMapOf<Int, BitSet>()
        try {
            for (square in server.archives(MAPS)) {
                // Restrict to root-world maps; water on copied deck templates is not another ocean.
                if (square > 25286) continue
                val bytes = server.data(MAPS, square, 0) ?: continue
                val water = decodeWaterTiles(bytes, waterOverlays)
                if (!water.isEmpty) loaded[square] = water
            }
        } finally {
            server.close()
        }
        waterSquares.clear()
        waterSquares.putAll(loaded)
        hullBounds.clear()
        hullBounds.putAll(nativeBounds)
        blockWalkingOnWater()
        initialized = true
    }

    internal fun blockWalkingOnWater() {
        for ((square, tiles) in waterSquares) {
            val baseX = (square shr 8) * 64
            val baseZ = (square and 255) * 64
            var tile = tiles.nextSetBit(0)
            while (tile >= 0) {
                val x = baseX + (tile shr 6)
                val z = baseZ + (tile and 63)
                if (collision.isZoneAllocated(x, z, 0)) {
                    collision.add(x, z, 0, CollisionFlag.BLOCK_WALK)
                }
                tile = tiles.nextSetBit(tile + 1)
            }
        }
    }

    fun canMove(entity: WorldEntity, endFineX: Int, endFineZ: Int, angle: Int): Boolean {
        val bounds = hullBounds[entity.configId] ?: return false
        return canMove(
            entity.fineX, entity.fineZ, endFineX, endFineZ, angle,
            bounds.width, bounds.length, entity.projectedLevel, entity.angle,
            bounds.offsetX, bounds.offsetZ,
        )
    }

    fun isNavigable(x: Int, z: Int, level: Int = 0): Boolean {
        if (level != 0 || x !in 0 until CoordGrid.MAP_WIDTH || z !in 0 until CoordGrid.MAP_LENGTH) {
            return false
        }
        val square = ((x shr 6) shl 8) or (z shr 6)
        val local = ((x and 63) shl 6) or (z and 63)
        if (waterSquares[square]?.get(local) != true) return false
        // BLOCK_WALK includes sea floor; use separate loc/wall collision for boats.
        return collision[x, z, level] and OBSTACLE_MASK == 0
    }

    fun canMove(
        startFineX: Int,
        startFineZ: Int,
        endFineX: Int,
        endFineZ: Int,
        angle: Int,
        width: Int,
        length: Int,
        level: Int = 0,
        startAngle: Int = angle,
        boundsOffsetX: Int = 0,
        boundsOffsetZ: Int = 0,
    ): Boolean {
        if (width !in 1..96 || length !in 1..96 || angle !in 0..2047 || startAngle !in 0..2047) {
            return false
        }
        if (startFineX !in 0 until MAX_FINE_X || endFineX !in 0 until MAX_FINE_X ||
            startFineZ !in 0 until MAX_FINE_Z || endFineZ !in 0 until MAX_FINE_Z) {
            return false
        }
        val halfWidth = width * 64.0
        val halfLength = length * 64.0
        val xExtent: Double
        val zExtent: Double
        val offsetX: Double
        val offsetZ: Double
        if (startAngle != angle) {
            val maxX = abs(boundsOffsetX.toDouble()) + halfWidth
            val maxZ = abs(boundsOffsetZ.toDouble()) + halfLength
            val radius = sqrt(maxX * maxX + maxZ * maxZ)
            xExtent = radius
            zExtent = radius
            offsetX = 0.0
            offsetZ = 0.0
        } else {
            val radians = angle * (2.0 * PI / 2048.0)
            val sine = sin(radians)
            val cosine = cos(radians)
            xExtent = abs(cosine) * halfWidth + abs(sine) * halfLength
            zExtent = abs(sine) * halfWidth + abs(cosine) * halfLength
            offsetX = cosine * boundsOffsetX + sine * boundsOffsetZ
            offsetZ = -sine * boundsOffsetX + cosine * boundsOffsetZ
        }
        val minX = floor((min(startFineX, endFineX) + offsetX - xExtent + EPSILON) / 128).toInt()
        val minZ = floor((min(startFineZ, endFineZ) + offsetZ - zExtent + EPSILON) / 128).toInt()
        val maxX = ceil((max(startFineX, endFineX) + offsetX + xExtent - EPSILON) / 128).toInt() - 1
        val maxZ = ceil((max(startFineZ, endFineZ) + offsetZ + zExtent - EPSILON) / 128).toInt() - 1
        if (minX < 0 || minZ < 0 || maxX >= CoordGrid.MAP_WIDTH || maxZ >= CoordGrid.MAP_LENGTH) {
            return false
        }
        for (x in minX..maxX) {
            for (z in minZ..maxZ) {
                if (!isNavigable(x, z, level)) return false
            }
        }
        return true
    }

    private data class HullBounds(val width: Int, val length: Int, val offsetX: Int, val offsetZ: Int)

    internal companion object {
        private const val MAX_FINE_X = CoordGrid.MAP_WIDTH * 128
        private const val MAX_FINE_Z = CoordGrid.MAP_LENGTH * 128
        private const val EPSILON = 0.000001
        private const val OBSTACLE_MASK =
            0xFF or CollisionFlag.LOC or CollisionFlag.GROUND_DECOR or CollisionFlag.BLOCK_PLAYERS

        private val ORDINARY_WATER_SPRITES = setOf(
            "water", "water_open", "water_shelf", "water_dropoff", "water_deep", "water_trench",
        )

        internal fun ordinaryWaterOverlays(live: Cache, revision: Int): Set<Int> {
            val sprites = checkNotNull(GameValProvider.loadIsolated().mappings["sprites"])
            val waterSprites = ORDINARY_WATER_SPRITES.map { name ->
                checkNotNull(sprites["sprites.$name"]) { "Missing native water sprite: sprites.$name" }
            }.toSet()
            val textureCodec = TextureCodec(revision)
            val waterTextures = live.files(9, 0).filter { id ->
                val bytes = live.data(9, 0, id) ?: return@filter false
                val buffer = Unpooled.wrappedBuffer(bytes)
                try {
                    val texture = textureCodec.builder(id)
                    with(textureCodec) { texture.read(0, buffer) }
                    check(!buffer.isReadable) { "Trailing texture data for $id" }
                    textureCodec.build(texture).fileId in waterSprites
                } finally {
                    buffer.release()
                }
            }.toSet()
            val overlayCodec = OverlayCodec()
            return live.files(2, 4).filter { id ->
                val bytes = live.data(2, 4, id) ?: return@filter false
                overlayCodec.loadData(id, bytes).texture in waterTextures
            }.toSet()
        }

        internal fun decodeWaterTiles(bytes: ByteArray, waterOverlays: Set<Int>): BitSet {
            val buffer = Unpooled.wrappedBuffer(bytes)
            val map = try {
                val decoded = MapTileDecoder.decode(buffer)
                check(decoded.tileHeights.size == 4 * 64 * 64) {
                    "Incomplete map tile data for Sailing."
                }
                // Count all terminating tile opcodes to reject truncated terrain streams.
                decoded
            } finally {
                buffer.release()
            }
            val water = BitSet(64 * 64)
            for ((tile, overlay) in map.overlays) {
                if (tile.level != 0 || overlay.shape != 0 || overlay.id !in waterOverlays) continue
                val above = MapSquareGrid(tile.x, tile.z, 1)
                val bridge = (map.rules[above]?.toInt() ?: 0) and MapTileDefinition.LINK_BELOW != 0
                if (!bridge) water.set((tile.x shl 6) or tile.z)
            }
            return water
        }
    }
}
