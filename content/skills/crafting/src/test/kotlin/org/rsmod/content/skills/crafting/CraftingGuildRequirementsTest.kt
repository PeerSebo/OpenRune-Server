package org.rsmod.content.skills.crafting

import dev.openrune.ServerCacheManager
import dev.openrune.types.InventoryServerType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CraftingGuildRequirementsTest {
    @BeforeAll fun cache() { ServerCacheManager.init(241).close() }

    @Test fun `entry requires an equipped apron or valid cape substitute`() {
        for (item in listOf("obj.brown_apron", "obj.golden_apron", "obj.skillcape_crafting", "obj.skillcape_crafting_trimmed", "obj.skillcape_max")) {
            val player = player()
            player.inv[0] = InvObj(item)
            assertFalse(player.hasGuildEntryOutfit(), "$item in the backpack is insufficient")
            player.inv[0] = null
            player.worn[if ("apron" in item) 4 else 1] = InvObj(item)
            assertTrue(player.hasGuildEntryOutfit(), item)
        }
    }

    @Test fun `high crafting alone and other aprons do not bypass the outfit requirement`() {
        val player = player()
        player.statMap.setBaseLevel("stat.crafting", 99)
        player.statMap.setCurrentLevel("stat.crafting", 99)
        assertFalse(player.hasGuildEntryOutfit())
        player.worn[4] = InvObj("obj.white_apron")
        assertFalse(player.hasGuildEntryOutfit())
    }

    private fun player() = Player().apply {
        inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
        worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
    }
}
