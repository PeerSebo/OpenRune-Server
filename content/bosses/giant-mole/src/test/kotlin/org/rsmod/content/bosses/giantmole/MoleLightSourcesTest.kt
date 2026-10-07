package org.rsmod.content.bosses.giantmole

import dev.openrune.types.InventoryServerType
import dev.openrune.types.util.UncheckedType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

@OptIn(UncheckedType::class)
class MoleLightSourcesTest {
    @Test fun `extinguishing preserves slot count variables and protected lanterns in a full bag`() {
        val contents = Array<InvObj?>(28) { InvObj(4550, 1) }
        contents[7] = InvObj(594, 3, 42)
        val inventory = Inventory(InventoryServerType(size = 28), contents)
        assertTrue(MoleLightSources.extinguish(inventory, mapOf(594 to 596)))
        assertEquals(InvObj(596, 3, 42), inventory[7])
        assertEquals(27, inventory.count { it?.id == 4550 })
        assertTrue(inventory.modifiedSlots[7])
        assertFalse(MoleLightSources.extinguish(inventory, mapOf(594 to 596)))
    }

    @Test fun `closed lantern alone is unaffected`() {
        val inventory = Inventory(InventoryServerType(size = 1), arrayOf(InvObj(4550, 1)))
        assertFalse(MoleLightSources.extinguish(inventory, mapOf(594 to 596)))
        assertFalse(inventory.hasModifiedSlots())
    }
}
