package org.rsmod.content.areas.city.varrock.locs

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.filesystem.Cache
import dev.openrune.map.GameMapBuilder
import dev.openrune.map.GameMapDecoder
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM.asRSCM
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.loc.LocRegistryRegion
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.NpcList
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.loc.LocLayerConstants.GROUND
import org.rsmod.routefinder.loc.LocShapeConstants.CENTREPIECE_STRAIGHT

@ResourceLock("ServerCacheManager")
class VarrockGardenCornersTest {
    @Test fun `eight native garden corners use the quarter circle matching their paving direction`() {
        val f = Fixture()
        val before = f.snapshot()
        val expected = Fixture()
        expected.addMissingPatch("loc.garden_delphinium_patch")
        f.script.replaceCorners()

        for (corner in Corners) {
            val native = before.single { it.coords == corner.coords && it.id == corner.patch.asRSCM() }
            val paving = before.single { it.coords == corner.coords && it.id == "loc.garden_paving_curved".asRSCM() }
            val actual = f.locs.findAll(corner.coords).single { it.layer == native.layer }
            assertEquals(corner.angle, paving.angle.id, "Native border at ${corner.coords}")
            assertEquals(corner.replacement.asRSCM(), actual.id)
            assertEquals(paving.angle, actual.angle)
            assertEquals(native.shape, actual.shape)
            assertTrue(paving in f.locs.findAll(corner.coords).toList())
            assertEquals(checkNotNull(ServerCacheManager.getObject(native.id)).multiVarBit,
                checkNotNull(ServerCacheManager.getObject(actual.id)).multiVarBit)
        }
        assertEquals(9, f.updates.updatedZones.values.sumOf { it.size })
        assertEquals(expected.collisionSnapshot(), f.collisionSnapshot(),
            "Corner geometry must retain native patch collision, including the restored tile")
    }

    @Test fun `missing delphinium corner is restored over its curved paving with the native saved state`() {
        val f = Fixture()
        val before = f.locs.findAll(MissingDelphinium).toList()
        assertFalse(before.any { it.layer == GROUND })
        val paving = before.single { it.id == "loc.garden_paving_curved".asRSCM() }
        assertEquals(0, paving.angle.id)

        f.script.replaceCorners()
        val actual = f.locs.findAll(MissingDelphinium).single { it.layer == GROUND }
        assertEquals("loc.varrock_garden_corner_delphiniums".asRSCM(), actual.id)
        assertEquals(CENTREPIECE_STRAIGHT, actual.shape.id)
        assertEquals(paving.angle, actual.angle)
        assertTrue(paving in f.locs.findAll(MissingDelphinium).toList())
        val native = checkNotNull(ServerCacheManager.getObject("loc.garden_delphinium_patch".asRSCM()))
        assertEquals(native.multiVarBit, checkNotNull(ServerCacheManager.getObject(actual.id)).multiVarBit)
    }

    @Test fun `restoring a missing corner does not overwrite another ground object`() {
        val f = Fixture()
        f.addMissingPatch("loc.garden_rosebush_patch_white")
        val before = f.locs.findAll(MissingDelphinium).toSet()
        f.script.replaceCorners()
        assertEquals(before, f.locs.findAll(MissingDelphinium).toSet())
    }

    @Test fun `missing delphinium paving prevents the restored weeds from spawning`() {
        val f = Fixture()
        val paving = f.locs.findAll(MissingDelphinium).single { it.id == "loc.garden_paving_curved".asRSCM() }
        assertTrue(f.locs.del(paving, Int.MAX_VALUE))
        f.script.replaceCorners()
        assertFalse(f.locs.findAll(MissingDelphinium).any { it.layer == GROUND })
    }

    @Test fun `straight interior and white tree patches retain their native placements`() {
        val f = Fixture()
        val before = f.snapshot()
        val targets = Corners.map { it.coords to it.patch.asRSCM() }.toSet()
        val unchanged = before.filter { (it.coords to it.id) !in targets }.toSet()
        val untouchedPatchIds = setOf(
            "loc.garden_delphinium_patch", "loc.garden_rosebush_patch_white",
            "loc.garden_rosebush_patch_red", "loc.garden_rosebush_patch_pink",
            "loc.garden_white_tree_patch", "loc.garden_snowdrop_patch", "loc.garden_vine_patch",
        ).map { it.asRSCM() }
        for (id in untouchedPatchIds) assertTrue(unchanged.any { it.id == id }, "No untouched native patch for $id")

        f.script.replaceCorners()
        val changedKeys = before.filter { (it.coords to it.id) in targets }.map { it.coords to it.layer }.toSet()
        val after = f.snapshot().filter {
            (it.coords to it.layer) !in changedKeys && !(it.coords == MissingDelphinium && it.layer == GROUND)
        }.toSet()
        assertEquals(unchanged, after)
        assertEquals(before.size + 1, f.snapshot().size)
    }

    @Test fun `reapplying garden startup does not duplicate corners or publish further changes`() {
        val f = Fixture()
        f.script.replaceCorners()
        val once = f.snapshot().toSet()
        val collision = f.collisionSnapshot()
        f.updates.clear()
        repeat(3) { f.script.replaceCorners() }
        assertEquals(once, f.snapshot().toSet())
        assertEquals(collision, f.collisionSnapshot())
        assertTrue(f.updates.updatedZones.isEmpty())
    }

    @Test fun `missing corner paving leaves the original patch intact`() {
        val f = Fixture()
        val target = Corners.first()
        val paving = f.locs.findAll(target.coords).single { it.id == "loc.garden_paving_curved".asRSCM() }
        assertTrue(f.locs.del(paving, Int.MAX_VALUE))
        f.script.replaceCorners()
        assertTrue(f.locs.findLoc(target.coords, target.patch))
        assertFalse(f.locs.findLoc(target.coords, target.replacement))
    }

    private class Fixture {
        private val clock = MapClock(100)
        private val storage = LocZoneStorage()
        val updates = ZoneUpdateMap()
        val collision = CollisionFlagMap()
        private val normal = LocRegistryNormal(updates, collision, storage)
        private val regions = RegionRegistry(
            RegionListSmall(), RegionListLarge(), normal, collision, storage,
            NpcRegistry(NpcList(), collision, EventBus()), ControllerRegistry(clock, ControllerList()),
            ZonePlayerActivityBitSet(),
        )
        val locs = LocRepository(clock, LocRegistry(storage, normal,
            LocRegistryRegion(updates, collision, storage, regions)), regions)
        val script = VarrockGardenCorners(locs)

        init {
            val cache = Cache.load(Path.of(".data/cache/LIVE"))
            try {
                val square = MapSquareKey(50, 54)
                val tiles = MapTileDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, square.id, 0))))
                val scenery = MapLocListDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, square.id, 1))))
                for (level in 0..3) for (x in 3192..3271 step 8) for (z in 3448..3527 step 8) {
                    collision.allocateIfAbsent(x, z, level)
                }
                val builder = GameMapBuilder()
                GameMapDecoder.putMaps(collision, square, tiles)
                GameMapDecoder.putLocs(builder, collision, square, tiles, scenery)
                for ((zone, data) in builder.zoneBuilders) storage.mapLocs[zone] = data.build()
            } finally {
                cache.close()
            }
        }

        fun snapshot(): List<LocInfo> = buildList {
            for (level in 0..3) for (x in 3200..3263 step 8) for (z in 3456..3519 step 8) {
                addAll(locs.findAll(ZoneKey.from(CoordGrid(x, z, level))).toList())
            }
        }

        fun collisionSnapshot(): List<Int> = buildList {
            for (level in 0..3) for (x in 3200..3263) for (z in 3456..3519) {
                add(collision[x, z, level])
            }
        }

        fun addMissingPatch(symbol: String) {
            locs.add(LocInfo(GROUND, MissingDelphinium,
                LocEntity(symbol.asRSCM(), CENTREPIECE_STRAIGHT, 0)), Int.MAX_VALUE)
        }
    }

    private data class Corner(val coords: CoordGrid, val patch: String, val replacement: String, val angle: Int)

    private companion object {
        val MissingDelphinium = CoordGrid(3226, 3474)
        val Corners = listOf(
            Corner(CoordGrid(3227, 3474), "loc.garden_delphinium_patch", "loc.varrock_garden_corner_delphiniums", 3),
            Corner(CoordGrid(3227, 3476), "loc.garden_delphinium_patch", "loc.varrock_garden_corner_delphiniums", 2),
            Corner(CoordGrid(3232, 3472), "loc.garden_rosebush_patch_white", "loc.varrock_garden_corner_white_roses", 1),
            Corner(CoordGrid(3229, 3472), "loc.garden_rosebush_patch_red", "loc.varrock_garden_corner_red_roses", 1),
            Corner(CoordGrid(3230, 3472), "loc.garden_rosebush_patch_red", "loc.varrock_garden_corner_red_roses", 2),
            Corner(CoordGrid(3227, 3472), "loc.garden_rosebush_patch_pink", "loc.varrock_garden_corner_pink_roses", 2),
            Corner(CoordGrid(3232, 3479), "loc.garden_snowdrop_patch", "loc.varrock_garden_corner_snowdrops", 0),
            Corner(CoordGrid(3232, 3484), "loc.garden_snowdrop_patch", "loc.varrock_garden_corner_snowdrops", 1),
        )

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
