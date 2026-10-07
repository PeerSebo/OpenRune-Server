package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.types.InvScope
import dev.openrune.types.InvStackType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj

@ResourceLock("ServerCacheManager")
class CostumeInventoryTest {
    @Test
    fun `costume containers are permanent personal storage rather than restocking shops`() {
        for (suffix in listOf("armour", "capes", "magic_wardrobe", "holiday_items", "ame",
            "treasure_trail_0", "treasure_trail_1", "treasure_trail_2", "treasure_trail_3")) {
            val symbol = "inv.poh_costume_room_${suffix}_inv"
            val first = Player().invMap.getOrPut(symbol)
            val second = Player().invMap.getOrPut(symbol)
            assertEquals(InvScope.Perm, first.type.scope, symbol)
            assertEquals(InvStackType.Always, first.type.stack, symbol)
            assertEquals(100, first.size, symbol)
            assertTrue(first.type.stock.isEmpty(), symbol)
            assertFalse(first.type.restock, symbol)
            assertFalse(first.type.protect, symbol)
            first[0] = InvObj("obj.bronze_full_helm", 2)
            assertNull(second[0], symbol)
        }
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
