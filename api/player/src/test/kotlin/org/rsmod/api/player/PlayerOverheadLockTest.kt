package org.rsmod.api.player

import dev.openrune.ServerCacheManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.prayerDrainCounter
import org.rsmod.game.entity.Player

@OptIn(InternalApi::class)
@ResourceLock("ServerCacheManager")
class PlayerOverheadLockTest {
    @Test
    fun `protection lock preserves other active prayers and quick prayer selection`() {
        val player = Player().apply { currentMapClock = 100 }
        VarPlayerIntMapSetter.set(player, "varbit.prayer_protectfrommagic", 1)
        VarPlayerIntMapSetter.set(player, "varbit.prayer_rapidheal", 1)
        VarPlayerIntMapSetter.set(player, "varbit.quickprayer_active", 1)
        VarPlayerIntMapSetter.set(player, "varbit.quickprayer_selected", 7)

        player.lockOverheads(5)

        assertEquals(0, player.vars["varbit.prayer_protectfrommagic"])
        assertEquals(1, player.vars["varbit.prayer_rapidheal"])
        assertEquals(1, player.vars["varbit.quickprayer_active"])
        assertEquals(7, player.vars["varbit.quickprayer_selected"])
        assertTrue(player.overheadsLocked)
    }

    @Test
    fun `removing the last active prayer clears quick prayer activity and drain`() {
        val player = Player().apply { currentMapClock = 100 }
        VarPlayerIntMapSetter.set(player, "varbit.prayer_protectfrommelee", 1)
        VarPlayerIntMapSetter.set(player, "varbit.quickprayer_active", 1)
        player.prayerDrainCounter = 10

        player.lockOverheads(5)

        assertEquals(0, player.vars["varbit.prayer_protectfrommelee"])
        assertEquals(0, player.vars["varbit.quickprayer_active"])
        assertEquals(0, player.prayerDrainCounter)
    }

    @Test
    fun `a shorter repeated lock cannot shorten an existing lock`() {
        val player = Player().apply { currentMapClock = 100 }
        player.lockOverheads(10)
        player.currentMapClock = 102
        player.lockOverheads(2)
        player.currentMapClock = 109
        assertTrue(player.overheadsLocked)
        player.currentMapClock = 110
        assertFalse(player.overheadsLocked)
        assertThrows(IllegalArgumentException::class.java) { player.lockOverheads(0) }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun cache() { ServerCacheManager.init(241).close() }
    }
}
