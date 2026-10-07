package org.rsmod.content.skills.agility.table

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.loc.MapLocDefinition
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.agility.configs.GnomeStrongholdCourse
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgilityTableCacheTest {
    @BeforeAll
    fun loadCache() {
        ServerCacheManager.init(241).close()
    }

    @Test
    fun `Gnome net and tree landings use the captured native obstacle sides`() {
        assertEquals(3423, GnomeStrongholdCourse.NET_FIRST_DEST_Z)
        assertEquals(3428, GnomeStrongholdCourse.NET_SECOND_DEST_Z)
        assertEquals(CoordGrid(2487, 3420), GnomeStrongholdCourse.BRANCH_DOWN_DEST)
        val targets = mapOf(
            "loc.obstical_net2" to CoordGrid(2473, 3425),
            "loc.obstical_net3" to CoordGrid(2485, 3426),
            "loc.climbing_tree" to CoordGrid(2486, 3419, 2),
            "loc.climbing_branch" to CoordGrid(2473, 3422, 1),
        )
        val cache = ServerCacheManager.init(241)
        try {
            for ((symbol, tile) in targets) {
                val bytes = checkNotNull(cache.data(MAPS, (tile.mx shl 8) or tile.mz, 1))
                val spawns = MapLocListDecoder.decode(InlineByteBuf(bytes)).spawns
                assertTrue(spawns.longIterator().asSequence().any { packed ->
                    val loc = MapLocDefinition(packed)
                    loc.id == symbol.asRSCM() && loc.level == tile.level &&
                        tile.mx * 64 + loc.localX == tile.x && tile.mz * 64 + loc.localZ == tile.z
                }, "$symbol has no native placement at $tile")
            }
        } finally {
            cache.close()
        }
    }

    @Test
    fun `packed course data retains ordered obstacles and linked stages`() {
        val courses = AgilityCourses.courses
        assertEquals(19, courses.size)
        assertTrue(courses.all { it.obstacles.isNotEmpty() })
        val advanced = courses.single { it.name == "Shayzien Advanced Agility Course" }
        assertEquals(45, advanced.level)
        assertTrue(advanced.obstacles.any { it.stages.size > 1 })
        assertEquals(508.0, advanced.obstacles.sumOf { it.xp * it.repeats } + advanced.lapXp, 0.05)
    }

    @Test
    fun `barbarian laps do not require claiming ordinary ladders worldwide`() {
        val course = AgilityCourses.courses.single { it.name == "Barbarian Outpost Agility Course" }
        val ladder = course.obstacles.indexOfFirst { SHARED_LADDER in it.locs }
        assertTrue(ladder >= 0)
        assertFalse(ladder in course.steps)
        assertEquals(3, course.steps.count { it == course.obstacles.lastIndex })
    }

    @Test
    fun `Prifddinas forest landings match the native map beyond the dark hole`() {
        val course = AgilityCourses.courses.single { it.name == "Prifddinas Agility Course" }
        val ends = listOf(
            CoordGrid(2269, 3389, 0), CoordGrid(2269, 3393, 2),
            CoordGrid(2257, 3390, 2), CoordGrid(2247, 3397, 2),
            CoordGrid(2246, 3406, 2), CoordGrid(2250, 3416, 2), CoordGrid(2260, 3425, 0),
        )
        assertEquals(ends, course.obstacles.subList(4, 11).map { it.landing.resolve(CoordGrid(0, 0, 0)) })

        val targets = mapOf(
            "loc.prif_agility_rope_bridge1" to CoordGrid(2264, 3390, 2),
            "loc.prif_agility_tightrope1" to CoordGrid(2253, 3390, 2),
            "loc.prif_agility_rope_bridge2" to CoordGrid(2246, 3399, 2),
            "loc.prif_agility_tightrope2" to CoordGrid(2243, 3409, 2),
            "loc.prif_agility_tightrope3" to CoordGrid(2253, 3418, 2),
        )
        val cache = ServerCacheManager.init(241)
        try {
            for ((symbol, tile) in targets) {
                val bytes = checkNotNull(cache.data(MAPS, (tile.mx shl 8) or tile.mz, 1))
                val spawns = MapLocListDecoder.decode(InlineByteBuf(bytes)).spawns
                val found = spawns.longIterator().asSequence().any { packed ->
                    val loc = MapLocDefinition(packed)
                    loc.id == symbol.asRSCM() && loc.level == tile.level &&
                        tile.mx * 64 + loc.localX == tile.x && tile.mz * 64 + loc.localZ == tile.z
                }
                assertTrue(found, "$symbol has no native placement at $tile")
            }
        } finally {
            cache.close()
        }
    }

    @Test
    fun `Prifddinas stage timings and sounds survive cache packing`() {
        val course = AgilityCourses.courses.single { it.name == "Prifddinas Agility Course" }
        val ladder = course.obstacles.first().stages.single()
        assertEquals("seq.human_reachforladder", ladder.anim)
        assertEquals(1, ladder.ticks)
        val rope = course.obstacles[1].stages
        assertEquals(listOf(1, 6, 9, 1), rope.map { it.ticks })
        assertEquals(listOf(6, 9), rope.filter { it.sound != null }.map { it.soundLoops })
        val chimney = course.obstacles[2]
        assertEquals(CoordGrid(3269, 6112, 2), chimney.landing.resolve(CoordGrid(0, 0, 0)))
        assertEquals(20, chimney.stages[1].soundDelay)
        assertEquals(20, chimney.stages[1].animDelay)
    }

    @Test
    fun `table shortcut registration fills gaps without replacing local movement`() {
        val events = EventBus()
        val wall = "loc.fai_falador_castle_crumble_mid".asRSCM()
        events.subscribeSuspend<ProtectedAccess, LocEvents.Op1>(LocEvents.Op1::class.java, wall.toLong()) {}
        val existing = events.suspend[LocEvents.Op1::class.java, wall.toLong()]
        val script = TableAgilityShortcutScript(events, CollisionFlagMap(), XpModifiers(emptySet()))
        with(script) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }

        events.publish(GameLifecycle.Startup)

        assertSame(existing, events.suspend[LocEvents.Op1::class.java, wall.toLong()])
        assertTrue(hasLocHandler(events, "loc.hosidiusquest_rock_snake".asRSCM(), 1))
        assertTrue(AgilityShortcutTable.rows.sumOf { it.links.size } >= 300)
        assertEquals(10, AgilityShortcutTable.rows.count { it.links.isEmpty() })
        val prif = AgilityShortcutTable.rows.single { "loc.prif_slayer_dungeon_shortcut_1a" in it.locs }
        assertEquals("seq.human_squeeze", prif.anim)
    }
}
