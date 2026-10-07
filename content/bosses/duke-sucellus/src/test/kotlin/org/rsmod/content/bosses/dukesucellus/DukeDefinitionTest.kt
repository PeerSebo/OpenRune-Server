package org.rsmod.content.bosses.dukesucellus

import dev.openrune.OsrsCacheProvider
import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.NpcType
import dev.openrune.filesystem.Cache
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.config.refs.BaseParams
import org.rsmod.map.CoordGrid

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class DukeDefinitionTest {
    private val nativeNpcs = mutableMapOf<Int, NpcType>()

    @BeforeAll fun cache() {
        ServerCacheManager.init(241).close()
        val live = Cache.load(Paths.get(".data/cache/LIVE"))
        try {
            OsrsCacheProvider.NPCDecoder(240).load(live, nativeNpcs)
        } finally {
            live.close()
        }
    }

    @Test fun `Duke retains his native body through sleeping combat and death`() {
        for (symbol in listOf("asleep", "awake", "dead")) {
            val type = checkNotNull(ServerCacheManager.getNpc("npc.duke_sucellus_$symbol".asRSCM()))
            assertEquals(listOf(49194), checkNotNull(nativeNpcs[type.id]).models?.toList())
            assertEquals(7, type.size)
            assertNotNull(ServerCacheManager.getAnim(type.standAnim))
        }
        val asleep = checkNotNull(ServerCacheManager.getNpc("npc.duke_sucellus_asleep".asRSCM()))
        assertEquals(0, asleep.wanderRange)
        assertEquals("varp.total_duke_sucellus_kills".asRSCM(), asleep.param(BaseParams.killcount_varp).id)
    }

    @Test fun `normal Duke room resolves both its entry and exit`() {
        val row = org.rsmod.api.table.InstanceSettingsRow.getRow("dbrow.instance_duke_sucellus")
        assertEquals("duke_sucellus", row.key)
        assertEquals(CoordGrid(3039, 6435, 0), row.enterCoord)
        assertEquals(CoordGrid(3039, 6432, 0), row.exitCoord)
        assertEquals(1, row.maxPlayers)
    }

    @Test fun `new encounter sounds resolve through runtime gamevals`() {
        val pattern = Regex("\"(synth\\.[A-Za-z0-9_]+)\"")
        val symbols = mutableSetOf<String>()
        for (module in listOf("duke-sucellus", "muspah", "vardorvis", "leviathan")) {
            Files.walk(Paths.get("content/bosses/$module/src/main/kotlin")).use { paths ->
                paths.filter { it.toString().endsWith(".kt") }.forEach { path ->
                    pattern.findAll(Files.readString(path)).forEach { symbols += it.groupValues[1] }
                }
            }
        }
        assertTrue(symbols.isNotEmpty())
        val unresolved = symbols.filter { runCatching { it.asRSCM(RSCMType.SYNTH) }.isFailure }
        assertEquals(emptyList<String>(), unresolved, "Unresolved encounter sound symbols")
    }
}
