package org.rsmod.api.shops

import dev.openrune.ServerCacheManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

@ResourceLock("ServerCacheManager")
class StallShopAccessTest {
    @Test fun `saved theft survives relogging and later records remain mutable`() {
        val original = Player()
        val counter = CoordGrid(3269, 3411)
        VarPlayerIntMapSetter.set(original, "varp.playtime", 100)
        StallShopAccess.record(original, "loc.tea_stall", counter)
        val restored = Player()
        restored.attr.putAllFromPersistence(original.attr.toPersistentMap())
        VarPlayerIntMapSetter.set(restored, "varp.playtime", 500)
        assertFalse(StallShopAccess.canTrade(restored, counter, setOf("loc.tea_stall")))
        StallShopAccess.record(restored, "loc.tea_stall", counter)
        VarPlayerIntMapSetter.set(restored, "varp.playtime", 1499)
        assertFalse(StallShopAccess.canTrade(restored, counter, setOf("loc.tea_stall")))
        VarPlayerIntMapSetter.set(restored, "varp.playtime", 1500)
        assertTrue(StallShopAccess.canTrade(restored, counter, setOf("loc.tea_stall")))
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
