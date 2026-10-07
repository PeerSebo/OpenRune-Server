package org.rsmod.api.net.rsprot.player

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.game.ui.UserInterface
import org.rsmod.game.ui.UserInterfaceMap

@ResourceLock("ServerCacheManager")
class InterfaceEventsTest {
    @Test
    fun `runtime parent mask enables native quantity button operations`() {
        val ui = UserInterfaceMap()
        val component = ServerCacheManager.fromComponent(QUANTITY_BUTTON)
        ui.events.add(QUANTITY_BUTTON, -1..-1, IfEvent.Op1.bitmask)
        assertTrue(InterfaceEvents.isEnabled(ui, component, -1, IfEvent.Op1))
        assertFalse(InterfaceEvents.isEnabled(ui, component, -1, IfEvent.Op2))
        assertFalse(InterfaceEvents.isEnabled(ui, component, 0, IfEvent.Op1))
    }

    @Test
    fun `zero override disables parent operations and last matching override wins`() {
        val ui = UserInterfaceMap()
        val tool = "component.farming_tools:rake"
        val component = ServerCacheManager.fromComponent(tool)
        assertTrue(component.hasEvent(IfEvent.Op1))
        ui.events.add(tool, -1..-1, 0L)
        assertFalse(InterfaceEvents.isEnabled(ui, component, -1, IfEvent.Op1))
        ui.events.add(tool, -1..-1, IfEvent.Op2.bitmask)
        assertTrue(InterfaceEvents.isEnabled(ui, component, -1, IfEvent.Op2))
        assertFalse(InterfaceEvents.isEnabled(ui, component, -1, IfEvent.Op1))
    }

    @Test
    fun `child overrides retain inclusive bounds without affecting the parent`() {
        val ui = UserInterfaceMap()
        val component = ServerCacheManager.fromComponent(QUANTITY_BUTTON)
        ui.events.add(QUANTITY_BUTTON, 0..3, IfEvent.Op2.bitmask)
        assertEquals(component.hasEvent(IfEvent.Op2), InterfaceEvents.isEnabled(ui, component, -1, IfEvent.Op2))
        for (slot in 0..3) assertTrue(InterfaceEvents.isEnabled(ui, component, slot, IfEvent.Op2))
        assertFalse(InterfaceEvents.isEnabled(ui, component, 4, IfEvent.Op2))
    }

    @Test
    fun `clearing overrides restores native parent defaults`() {
        val ui = UserInterfaceMap()
        val component = ServerCacheManager.fromComponent(QUANTITY_BUTTON)
        ui.events.add(QUANTITY_BUTTON, -1..0, IfEvent.Op2.bitmask)
        assertTrue(InterfaceEvents.isEnabled(ui, component, -1, IfEvent.Op2))
        assertTrue(InterfaceEvents.isEnabled(ui, component, 0, IfEvent.Op2))
        assertFalse(InterfaceEvents.isEnabled(ui, component, 1, IfEvent.Op2))
        ui.events.clear(UserInterface(component.interfaceId))
        assertEquals(component.hasEvent(IfEvent.Op1), InterfaceEvents.isEnabled(ui, component, -1, IfEvent.Op1))
        assertEquals(component.hasEvent(IfEvent.Op2), InterfaceEvents.isEnabled(ui, component, -1, IfEvent.Op2))
        assertFalse(InterfaceEvents.isEnabled(ui, component, 0, IfEvent.Op2))
    }

    companion object {
        private const val QUANTITY_BUTTON = "component.farming_tools:quantity_5"

        @JvmStatic
        @BeforeAll
        fun cache() { ServerCacheManager.init(241).close() }
    }
}
