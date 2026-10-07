@file:OptIn(dev.openrune.types.util.UncheckedType::class)

package org.rsmod.game.entity.player

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.rsmod.game.inv.InvObj

class AppearanceWornOverrideTest {
    @Test fun `visual replacement and hidden shield leave equipment untouched`() {
        val appearance = Appearance()
        val weapon = InvObj(1, 1)
        val shield = InvObj(2, 1)
        val bowl = InvObj(3, 1)
        appearance.wornOverrides = mapOf(3 to bowl, 5 to null)
        assertEquals(bowl, appearance.wornObj(3, weapon))
        assertNull(appearance.wornObj(5, shield))
        assertEquals(weapon, appearance.wornObj(4, weapon))
        appearance.wornOverrides = emptyMap()
        assertEquals(weapon, appearance.wornObj(3, weapon))
        assertEquals(shield, appearance.wornObj(5, shield))
    }

    @Test fun `overrides are copied and appearance rebuilds when cleared`() {
        val appearance = Appearance()
        val source = mutableMapOf<Int, InvObj?>(3 to InvObj(3, 1))
        appearance.clearRebuildFlag()
        appearance.wornOverrides = source
        assertTrue(appearance.rebuild)
        source.clear()
        assertNotNull(appearance.wornObj(3, null))
        appearance.clearRebuildFlag()
        appearance.wornOverrides = emptyMap()
        assertTrue(appearance.rebuild)
    }

    @Test fun `hide objects takes precedence over visual overrides`() {
        val appearance = Appearance()
        appearance.wornOverrides = mapOf(3 to InvObj(3, 1))
        appearance.hideObjs = true
        assertNull(appearance.wornObj(3, InvObj(1, 1)))
    }
}
