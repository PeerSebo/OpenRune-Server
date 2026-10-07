package org.rsmod.content.skills.agility.table

import dev.openrune.types.ObjectServerType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.player.events.interact.LocContentEvents
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.events.EventBus

class AgilityBindingsTest {
    @Test
    fun `shared content handlers are preserved for generic ladders and passages`() {
        val events = EventBus()
        events.subscribeSuspend<ProtectedAccess, LocContentEvents.Op1>(LocContentEvents.Op1::class.java, 2L) {}
        val type = ObjectServerType(id = 1).apply { contentGroup = 2 }

        assertTrue(hasLocHandler(events, type, 1))
        assertFalse(hasLocHandler(events, type, 2))
    }

    @Test
    fun `table handlers preserve existing bespoke object operations`() {
        val events = EventBus()
        events.subscribeSuspend<ProtectedAccess, LocEvents.Op1>(LocEvents.Op1::class.java, 1L) {}

        assertTrue(hasLocHandler(events, 1, 1))
        assertFalse(hasLocHandler(events, 1, 2))
        assertFalse(hasLocHandler(events, 2, 1))
    }

    @Test
    fun `table handlers preserve approach handlers including distant stepping stones`() {
        val events = EventBus()
        events.subscribeSuspend<ProtectedAccess, LocEvents.Ap1>(LocEvents.Ap1::class.java, 1L) {}

        assertTrue(hasLocHandler(events, 1, 1))
        assertFalse(hasLocHandler(events, 1, 2))
    }
}
