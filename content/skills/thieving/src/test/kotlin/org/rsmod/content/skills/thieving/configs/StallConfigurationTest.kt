package org.rsmod.content.skills.thieving.configs

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.config.refs.params

@ResourceLock("ServerCacheManager")
class StallConfigurationTest {
    @Test
    fun `all migrated stalls retain their requirements rewards depletion and guard ranges`() {
        val lines = checkNotNull(javaClass.getResourceAsStream("/stall-catalogue.tsv"))
            .bufferedReader().use { it.readLines() }
        assertEquals(51, lines.size)
        for (line in lines) {
            val columns = line.split('\t')
            val (symbol, level, xp, respawn, empty) = columns
            val guards = columns[5]
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM()))
            assertTrue(type.isContentType("content.stall"), symbol)
            assertEquals(level.toInt(), type.paramOrNull(params.levelrequire), symbol)
            assertEquals(xp.toInt(), type.paramOrNull(params.skill_xp), symbol)
            assertEquals(respawn.toInt(), type.paramOrNull(params.respawn_time), symbol)
            assertEquals(empty.asRSCM(), type.paramOrNull(params.next_loc_stage)?.id, symbol)
            assertEquals(guards.toInt(), type.paramOrNull(ThievingParams.thieving_guard_range) ?: 0, symbol)
        }
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
