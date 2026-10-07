package org.rsmod.api.player.stat

import com.google.inject.Guice
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.game.entity.Player

@OptIn(InternalApi::class)
@ResourceLock("ServerCacheManager")
class PlayerStatResetServiceTest {
    @Test
    fun `explicit reset runs cleanup once before restoring every native stat`() {
        val player = Player()
        val stats = ServerCacheManager.getStats().values.map { RSCM.getReverseMapping(RSCMType.STAT, it.id) }
        stats.forEachIndexed { index, stat ->
            player.statMap.setBaseLevel(stat, 40)
            player.statMap.setCurrentLevel(stat, if (index % 2 == 0) 60 else 20)
        }
        var calls = 0
        val reset = PlayerStatResetService(setOf(PlayerStatResetHook {
            calls++
            assertEquals(60, it.stat(stats.first()))
        }))

        reset.resetAll(player)

        assertEquals(1, calls)
        for (stat in stats) assertEquals(40, player.stat(stat), stat)
    }

    @Test
    fun `empty hook module supports full resets without any content module`() {
        val service = Guice.createInjector(PlayerStatResetModule()).getInstance(PlayerStatResetService::class.java)
        val player = Player().apply {
            statMap.setBaseLevel("stat.attack", 40)
            statMap.setCurrentLevel("stat.attack", 20)
        }
        service.resetAll(player)
        assertEquals(40, player.stat("stat.attack"))
    }

    @Test
    fun `ordinary partial restores keep maintenance sources and unrelated stats`() {
        val player = Player().apply {
            statMap.setBaseLevel("stat.attack", 40)
            statMap.setCurrentLevel("stat.attack", 20)
            statMap.setBaseLevel("stat.strength", 40)
            statMap.setCurrentLevel("stat.strength", 55)
        }
        StatBoostDecayPrevention.add(player, "stat.strength", "test.maintenance")

        player.statRestoreAll(listOf("stat.attack"))

        assertEquals(40, player.stat("stat.attack"))
        assertEquals(55, player.stat("stat.strength"))
        assertTrue(StatBoostDecayPrevention.prevents(player, "stat.strength"))
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
