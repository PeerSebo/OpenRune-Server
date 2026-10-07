package org.rsmod.content.bosses.hespori

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.game.entity.Player

@ResourceLock("ServerCacheManager")
class HesporiStateMigrationTest {
    @Test fun `legacy save migrates scalars while retaining reclaim payload and unrelated attributes`() {
        val player = Player()
        player.attr.putAllFromPersistence(mapOf(
            "hespori_rake" to 2,
            "hespori_planted_minute" to 123456,
            "hespori_defeated" to true,
            "hespori_in_arena" to true,
            "hespori_reclaim_paid" to true,
            "hespori_reclaim_items" to "995,100,0",
            "another_quest" to 19,
        ))
        player.migrateHesporiState()
        assertEquals(2, player.hesporiRakeProgress)
        assertEquals(123456, player.hesporiPlantedMinute)
        assertTrue(player.hesporiDefeated)
        assertTrue(player.hesporiInArena)
        assertTrue(player.hesporiReclaimPaid)
        assertEquals(mapOf("hespori_reclaim_items" to "995,100,0", "another_quest" to 19), player.attr.toPersistentMap())
        player.migrateHesporiState()
        assertEquals(8, HesporiCrop.stage(player))
    }

    @Test fun `stale legacy values cannot restore a harvested plant or a cleared reclaim payment`() {
        val player = Player()
        val oldSave = mapOf("hespori_rake" to 3, "hespori_planted_minute" to 0,
            "hespori_defeated" to true, "hespori_reclaim_paid" to true)
        player.attr.putAllFromPersistence(oldSave)
        player.migrateHesporiState()
        assertEquals(0, player.hesporiPlantedMinute)
        assertTrue(HesporiCrop.claimHarvest(player))
        player.hesporiReclaimPaid = false
        val restored = Player().apply { vars.backing.putAll(player.vars.backing) }
        restored.attr.putAllFromPersistence(oldSave)
        restored.migrateHesporiState()
        assertEquals(3, HesporiCrop.stage(restored))
        assertNull(restored.hesporiPlantedMinute)
        assertFalse(restored.hesporiDefeated)
        assertFalse(restored.hesporiReclaimPaid)
        assertTrue(restored.attr.toPersistentMap().isEmpty())
    }

    @Test fun `new saves and unset planted minute remain valid through migration`() {
        val player = Player()
        assertNull(player.hesporiPlantedMinute)
        player.hesporiRakeProgress = 1
        player.hesporiReclaimPaid = true
        player.migrateHesporiState()
        assertEquals(1, HesporiCrop.stage(player))
        assertTrue(player.hesporiReclaimPaid)
        player.hesporiPlantedMinute = 0
        assertEquals(0, player.hesporiPlantedMinute)
        player.hesporiPlantedMinute = null
        assertNull(player.hesporiPlantedMinute)
        assertTrue(player.hesporiReclaimPaid)
    }

    @Test fun `crop and recovery vars persist server side and independent bit fields do not overlap`() {
        for (name in listOf("varp.hespori_state", "varp.hespori_planted_minute")) {
            val type = checkNotNull(ServerCacheManager.getVarp(name.asRSCM()))
            assertEquals(VarpLifetime.Perm, type.scope)
            assertEquals(VarpTransmitLevel.Never, type.transmit)
        }
        val player = Player()
        player.migrateHesporiState()
        player.hesporiRakeProgress = 3
        player.hesporiDefeated = true
        player.hesporiInArena = true
        player.hesporiReclaimPaid = true
        player.hesporiPlantedMinute = 42
        player.hesporiRakeProgress = 0
        player.hesporiInArena = false
        assertTrue(player.hesporiDefeated)
        assertTrue(player.hesporiReclaimPaid)
        assertEquals(42, player.hesporiPlantedMinute)
        player.hesporiDefeated = false
        assertTrue(player.hesporiReclaimPaid)
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
