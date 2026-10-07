package org.rsmod.content.minigames.sorceressgarden.configs

import dev.openrune.ServerCacheManager
import java.net.URLClassLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.map.CoordGrid

@ResourceLock("ServerCacheManager")
class GardenCatalogueTest {
    @Test
    fun `fresh enum and reader initialization succeed from either access direction`() {
        val prefix = "org.rsmod.content.minigames.sorceressgarden.configs.GardenSeason"
        for (readerFirst in listOf(true, false)) {
            val location = GardenSeason::class.java.protectionDomain.codeSource.location
            val parent = GardenSeason::class.java.classLoader
            val loader = object : URLClassLoader(arrayOf(location), parent) {
                override fun loadClass(name: String, resolve: Boolean): Class<*> {
                    if (!name.startsWith(prefix)) return super.loadClass(name, resolve)
                    synchronized(getClassLoadingLock(name)) {
                        val type = findLoadedClass(name) ?: findClass(name)
                        if (resolve) resolveClass(type)
                        return type
                    }
                }
            }
            loader.use {
                fun readRows(): Map<*, *> {
                    val type = Class.forName("${prefix}Data", true, loader)
                    return type.getMethod("getRows").invoke(type.getField("INSTANCE").get(null)) as Map<*, *>
                }
                if (readerFirst) assertEquals(4, readRows().size)
                val enumType = Class.forName(prefix, true, loader)
                val seasons = enumType.enumConstants
                assertEquals(listOf("WINTER", "SPRING", "AUTUMN", "SUMMER"), seasons.map { it.toString() })
                val companion = enumType.getField("Companion").get(null)
                val byFruit = companion.javaClass.getMethod("getByFruit").invoke(companion) as Map<*, *>
                val byJuice = companion.javaClass.getMethod("getByJuice").invoke(companion) as Map<*, *>
                for (season in seasons) {
                    assertSame(season, byFruit[enumType.getMethod("getFruitObj").invoke(season)])
                    assertSame(season, byJuice[enumType.getMethod("getJuiceObj").invoke(season)])
                }
                assertEquals(4, readRows().size)
            }
        }
    }

    @Test
    fun `packed seasons preserve original fields enum identities and fruit juice lookups`() {
        assertEquals(listOf("WINTER", "SPRING", "AUTUMN", "SUMMER"), GardenSeason.entries.map { it.name })
        assertEquals(listOf(0, 1, 2, 3), GardenSeason.entries.map { it.ordinal })
        for (values in fixture("garden-seasons-before.tsv")) {
            val season = GardenSeason.valueOf(values[0])
            assertEquals(values, listOf(
                season.name, season.thievingLevel.toString(), quoted(season.doorLoc), coord(season.catchEjectCoord),
                quoted(season.treeLoc), quoted(season.herbLoc), quoted(season.fruitObj), quoted(season.juiceObj),
                season.sqirkCount.toString(), season.farmingXp.toString(), season.osmanXp.toString(),
                season.thievingBoost.toString(), season.runEnergyPercent.toString(), coord(season.treeAnchor), coord(season.herbAnchor),
            ))
            assertSame(season, GardenSeason.byFruit[season.fruitObj])
            assertSame(season, GardenSeason.byJuice[season.juiceObj])
        }
        assertEquals(GardenSeason.entries, GardenSeason.byFruit.values.toList())
        assertEquals(GardenSeason.entries, GardenSeason.byJuice.values.toList())
    }

    @Test
    fun `all elemental assignments preserve original order season start and every patrol step`() {
        val original = fixture("garden-patrols-before.tsv")
        assertEquals(original.map { it[0] }, ElementalPatrols.all.keys.toList())
        for (values in original) {
            val patrol = ElementalPatrols.all.getValue(values[0])
            val start = CoordGrid(values[2].toInt(), values[3].toInt(), 0)
            val expected = expandOriginal(start, values[4])
            assertSame(GardenSeason.valueOf(values[1]), patrol.season)
            assertEquals(expected, patrol.loop, values[0])
            assertTrue(patrol.loop.size > 1)
            for (index in patrol.loop.indices) {
                val from = patrol.loop[index]
                val to = patrol.loop[(index + 1) % patrol.loop.size]
                assertTrue(kotlin.math.abs(from.x - to.x) <= 1 && kotlin.math.abs(from.z - to.z) <= 1)
            }
        }
    }

    @Test
    fun `route parser still rejects malformed directions open routes and malformed lengths`() {
        for (runs in listOf("N", "Q1", "N1", "N2 E1 S2")) {
            assertThrows(IllegalArgumentException::class.java) { ElementalPatrols.route(2899, 5466, runs) }
        }
    }

    @Test
    fun `season validation rejects missing duplicate and colliding interaction definitions`() {
        val rows = GardenSeasonData.rows.values.toList()
        assertEquals(GardenSeasonData.rows, GardenSeasonData.validate(rows.reversed()))
        assertThrows(IllegalArgumentException::class.java) { GardenSeasonData.validate(rows.dropLast(1)) }
        assertThrows(IllegalArgumentException::class.java) { GardenSeasonData.validate(rows + rows.first()) }
        assertThrows(IllegalArgumentException::class.java) {
            GardenSeasonData.validate(listOf(rows.first().copy(fruitObj = rows[1].fruitObj)) + rows.drop(1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            GardenSeasonData.validate(listOf(rows.first().copy(sqirkCount = 0)) + rows.drop(1))
        }
    }

    private fun expandOriginal(start: CoordGrid, runs: String): List<CoordGrid> {
        val result = mutableListOf(start)
        for (run in runs.split(' ')) {
            val direction = run.takeWhile(Char::isLetter)
            val dx = if ('E' in direction) 1 else if ('W' in direction) -1 else 0
            val dz = if ('N' in direction) 1 else if ('S' in direction) -1 else 0
            repeat(run.dropWhile(Char::isLetter).toInt()) { result += result.last().translate(dx, dz) }
        }
        assertEquals(start, result.removeLast())
        return result
    }

    private fun quoted(value: String) = "\"$value\""
    private fun coord(value: CoordGrid) = "CoordGrid(${value.x}, ${value.z}, ${value.level})"

    companion object {
        private fun fixture(name: String) = checkNotNull(GardenCatalogueTest::class.java.getResource("/$name"))
            .readText().lineSequence().filter { it.isNotBlank() }.map { it.split('\t') }.toList()

        @JvmStatic @BeforeAll fun cache() {
            ServerCacheManager.init(241).close()
            assertEquals(listOf("WINTER", "SPRING", "AUTUMN", "SUMMER"), GardenSeasonData.rows.keys.toList())
        }
    }
}
