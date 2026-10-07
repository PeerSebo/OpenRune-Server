package org.rsmod.content.bosses.skotizo

import dev.openrune.ServerCacheManager
import dev.openrune.types.InventoryServerType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatacombsTotemTest {
    @BeforeAll fun cache() { ServerCacheManager.init(241).close() }

    @Test fun `only eligible Catacombs monsters gain the extra roll`() {
        val giant = Npc("npc.kourend_hillgiant", org.rsmod.map.CoordGrid.ZERO)
        assertTrue(CatacombsTotemRules.eligible(true, giant))
        assertFalse(CatacombsTotemRules.eligible(false, giant))
        assertFalse(CatacombsTotemRules.eligible(true, Npc("npc.kourend_ghost1", org.rsmod.map.CoordGrid.ZERO)))
        assertFalse(CatacombsTotemRules.eligible(true, Npc("npc.cata_boss", org.rsmod.map.CoordGrid.ZERO)))
        assertFalse(CatacombsTotemRules.eligible(true, Npc("npc.cata_boss_minion", org.rsmod.map.CoordGrid.ZERO)))
    }

    @Test fun `HP rates match dragons and superior monsters guarantee exactly one selected piece`() {
        assertEquals(250, CatacombsTotemRules.denominator(250, false))
        assertEquals(305, CatacombsTotemRules.denominator(195, false))
        assertEquals(1, CatacombsTotemRules.denominator(250, true))
        assertEquals("obj.cata_totem1", CatacombsTotemRules.nextPart(listOf(0, 0, 0)))
        assertEquals("obj.cata_totem2", CatacombsTotemRules.nextPart(listOf(1, 0, 0)))
        assertEquals("obj.cata_totem3", CatacombsTotemRules.nextPart(listOf(1, 1, 0)))
        assertEquals("obj.cata_totem1", CatacombsTotemRules.nextPart(listOf(1, 1, 1)))
    }

    @Test fun `banked pieces prevent receiving another copy before the missing piece`() {
        val player = Player().apply { inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28)) }
        val bank = Inventory(InventoryServerType(size = 400), arrayOfNulls(400))
        player.invMap["inv.bank"] = bank
        bank[0] = InvObj("obj.cata_totem1", 2)
        player.inv[0] = InvObj("obj.cata_totem2", 1)
        assertEquals(listOf(2, 1, 0), CatacombsTotemRules.counts(player))
        assertEquals("obj.cata_totem3", CatacombsTotemRules.nextPart(CatacombsTotemRules.counts(player)))
        bank[1] = InvObj("obj.cata_totem3", 1)
        assertEquals("obj.cata_totem2", CatacombsTotemRules.nextPart(CatacombsTotemRules.counts(player)))
    }
}
