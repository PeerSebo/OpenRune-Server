package org.rsmod.content.skills.slayer.core

import dev.openrune.ServerCacheManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.config.refs.SlayerBossTargets
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.table.slayer.SlayerMastersRow
import org.rsmod.api.table.slayer.SlayerTaskSublistRow
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SlayerNativeBossTaskTest {
    @BeforeAll fun nativeCache() { ServerCacheManager.init(241).close() }

    @Test fun `native boss rows retain separate sub IDs despite all task IDs being minus one`() {
        val rows = SlayerTaskSublistRow.all().filter { it.taskSubtableId in setOf(18, 19, 25) }
        assertEquals(3, rows.size)
        assertTrue(rows.all { it.task.id == -1 })
        assertEquals("the Cave Kraken boss", SlayerBossTasks.task(18)?.nameLowercase)
        assertEquals("the Thermonuclear Smoke Devil", SlayerBossTasks.task(19)?.nameLowercase)
        assertEquals("Sarachnis", SlayerBossTasks.task(25)?.nameLowercase)
        assertTrue(SlayerBossTasks.isBossTask(98))
        assertFalse(SlayerBossTasks.isBossTask(-1))
    }

    @Test fun `native master selection checks mandatory Slayer levels and returns distinct bosses`() {
        val player = Player()
        for ((_, stat) in ServerCacheManager.getStats()) player.statMap.setBaseLevel(stat.internalName, 99)
        val master = SlayerMastersRow.all().first { it.masterId == 5 }
        val all = SlayerBossTasks.eligibleBossTasks(player, master)
        assertEquals(setOf(18, 19, 25), all.map { it.bossId }.toSet())
        assertTrue(all.all { it.masterTask.task.id == 98 })
        player.statMap.setBaseLevel("stat.slayer", 86)
        player.statMap.setCurrentLevel("stat.slayer", 99)
        assertEquals(setOf(25), SlayerBossTasks.eligibleBossTasks(player, master).map { it.bossId }.toSet())
        player.statMap.setBaseLevel("stat.slayer", 87)
        assertEquals(setOf(18, 25), SlayerBossTasks.eligibleBossTasks(player, master).map { it.bossId }.toSet())
    }

    @Test fun `boss assignment credits its own boss but never ordinary variants or another boss`() {
        val player = Player()
        VarPlayerIntMapSetter.set(player, "varp.slayer_target", 98)
        VarPlayerIntMapSetter.set(player, "varbit.slayer_target_bossid", 18)
        VarPlayerIntMapSetter.set(player, "varp.slayer_count", 5)
        val boss = Npc("npc.slayer_kraken_boss", org.rsmod.map.CoordGrid.ZERO)
        assertFalse(SlayerTaskManager.decreaseTask(player, Npc("npc.slayer_kraken", org.rsmod.map.CoordGrid.ZERO)))
        assertFalse(SlayerTaskManager.decreaseTask(player, Npc("npc.smoke_devil_boss", org.rsmod.map.CoordGrid.ZERO)))
        assertEquals(5, player.vars["varp.slayer_count"])
        assertTrue(SlayerTaskManager.decreaseTask(player, boss))
        assertEquals(4, player.vars["varp.slayer_count"])
        assertEquals("the Cave Kraken boss", SlayerBossTasks.current(player)?.nameLowercase)
        assertTrue(SlayerTaskManager.countsAsTaskKill(Npc("npc.slayer_kraken_boss_whirlpool", org.rsmod.map.CoordGrid.ZERO), 98, 18))
        assertFalse(SlayerTaskManager.countsAsTaskKill(Npc("npc.slayer_kraken_boss_tentacle", org.rsmod.map.CoordGrid.ZERO), 98, 18))
    }

    @Test fun `ordinary Kraken and smoke devil tasks still accept the boss alternatives`() {
        assertTrue(SlayerTaskManager.countsAsTaskKill(Npc("npc.slayer_kraken_boss", org.rsmod.map.CoordGrid.ZERO), 92))
        assertTrue(SlayerTaskManager.countsAsTaskKill(Npc("npc.smoke_devil_boss", org.rsmod.map.CoordGrid.ZERO), 95))
        assertTrue(SlayerTaskManager.countsAsTaskKill(Npc("npc.smoke_devil_boss", org.rsmod.map.CoordGrid.ZERO), 98, 19))
        assertTrue(SlayerTaskManager.countsAsTaskKill(Npc("npc.sarachnis", org.rsmod.map.CoordGrid.ZERO), 98, 25))
        assertFalse(SlayerTaskManager.countsAsTaskKill(Npc("npc.sarachnis", org.rsmod.map.CoordGrid.ZERO), 98, 0))
    }

    @Test fun `Skotizo counts both demon categories without adding his helpers to the boss task`() {
        val boss = Npc("npc.cata_boss", org.rsmod.map.CoordGrid.ZERO)
        assertTrue(SlayerTaskManager.countsAsTaskKill(boss, 29))
        assertTrue(SlayerTaskManager.countsAsTaskKill(boss, 30))
        assertFalse(SlayerBossTargets.matches(boss.visType, 18))
    }
}
