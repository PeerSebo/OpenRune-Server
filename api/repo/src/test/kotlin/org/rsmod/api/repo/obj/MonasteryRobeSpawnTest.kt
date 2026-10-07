package org.rsmod.api.repo.obj

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.obj.MapObjDefinition
import dev.openrune.map.obj.MapObjListDecoder
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.obj.Obj
import org.rsmod.game.obj.ObjEntity
import org.rsmod.game.obj.ObjScope
import org.rsmod.map.CoordGrid

@ResourceLock("ServerCacheManager")
class MonasteryRobeSpawnTest {
    @Test
    fun `packed monastery robes spawn publicly and each taken robe respawns once`() {
        val cache = ServerCacheManager.init(241)
        val spawns = try {
            val data = checkNotNull(cache.data(MAPS, (47 shl 8) or 54, 6))
            MapObjListDecoder.decode(InlineByteBuf(data)).packedSpawns.map(::MapObjDefinition)
        } finally {
            cache.close()
        }
        val expected = mapOf(
            "obj.monkrobebottom".asRSCM() to CoordGrid(3059, 3488, 1),
            "obj.monkrobetop".asRSCM() to CoordGrid(3059, 3487, 1),
        )
        val robes = spawns.filter { it.id in expected }.map { def ->
            val coords = CoordGrid(47 * 64 + def.localX, 54 * 64 + def.localZ, def.level)
            assertEquals(expected[def.id], coords)
            assertEquals(1, def.count)
            Obj(coords, ObjEntity(def.id, def.count, ObjScope.Perm.id), 0, Obj.NULL_OBSERVER_ID)
        }
        assertEquals(2, robes.size)
        val clock = MapClock()
        val registry = ObjRegistry(ZoneUpdateMap())
        val repo = ObjRepository(clock, registry)
        robes.forEach { repo.addDelayed(it, spawnDelay = 0, duration = Int.MAX_VALUE) }
        assertEquals(2, repo.flushDelayedAdds())
        assertEquals(2, registry.count())
        val observer = Player()
        for (robe in robes) {
            assertTrue(registry.isValid(observer, robe))
            val type = checkNotNull(ServerCacheManager.getItem(robe.type))
            assertEquals("Take", type.options.getOpOrNull(2))
            assertTrue(type.respawnRate in 1 until Int.MAX_VALUE)
            assertTrue(repo.del(robe))
            assertFalse(repo.del(robe), "A duplicate pickup must not schedule another respawn")
            assertFalse(registry.isValid(observer, robe))
            clock.cycle += type.respawnRate - 1
            repo.processDurations()
            assertFalse(registry.isValid(observer, robe))
            clock.tick()
            repo.processDurations()
            assertTrue(registry.isValid(observer, robe))
            repo.processDurations()
            assertEquals(2, registry.count())
            assertSame(robe, repo.findAll(robe.coords).single())
        }
    }
}
