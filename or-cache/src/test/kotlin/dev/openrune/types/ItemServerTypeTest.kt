package dev.openrune.types

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ItemServerTypeTest {
    @Test
    fun `native equipment operation names require a wear position`() {
        for (operation in listOf("Wield", "Wear", "Equip", "Hold", "Chill")) {
            val item = ItemServerType(
                wearpos1 = 3,
                interfaceOptions = mutableListOf(null, operation, null, null, "Drop"),
            )
            assertTrue(item.isEquipable, operation)
            item.wearpos1 = -1
            assertFalse(item.isEquipable, operation)
        }
    }

    @Test
    fun `other item actions do not become equipment actions`() {
        for (operation in listOf("Eat", "Read", "Play", "Teleport", "Drop", null)) {
            val item = ItemServerType(
                wearpos1 = 3,
                interfaceOptions = mutableListOf(null, operation, null, null, "Drop"),
            )
            assertFalse(item.isEquipable, operation)
        }
        assertFalse(ItemServerType().isEquipable)
        assertFalse(ItemServerType(wearpos1 = 3, interfaceOptions = mutableListOf()).isEquipable)
    }
}
