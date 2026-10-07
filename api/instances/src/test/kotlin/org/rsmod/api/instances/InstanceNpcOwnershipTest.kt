package org.rsmod.api.instances

import dev.openrune.types.NpcServerType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.rsmod.annotations.InternalApi
import org.rsmod.api.instances.region.InstanceAreaResolver
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.PlayerList
import org.rsmod.routefinder.collision.CollisionFlagMap
import sun.misc.Unsafe

@OptIn(InternalApi::class)
class InstanceNpcOwnershipTest {
    @Test fun `changing a boss model retains its owning instance`() {
        val manager = manager()
        val npc = npc()
        val session = InstanceId(1)
        manager.attachNpc(session, npc)
        val original = npc.uid

        npc.transmog(NpcServerType(id = 2, name = "Second phase", hitpoints = 100), Int.MAX_VALUE)
        npc.assignUid()

        assertNotEquals(original, npc.uid)
        assertEquals(session, manager.instanceForNpc(npc))
    }

    @Test fun `an NPC replacing a boss slot does not inherit its ownership`() {
        val manager = manager()
        val npc = npc()
        manager.attachNpc(InstanceId(1), npc)
        val replacement = npc()

        assertEquals(npc.uid, replacement.uid)
        assertNull(manager.instanceForNpc(replacement))
        npc.slotId = PathingEntity.INVALID_SLOT
        npc.clearUid()
        assertNull(manager.instanceForNpc(npc))
    }

    private fun manager() = InstanceManager(unused<RegionRepository>(), unused<NpcRepository>(),
        PlayerList(), EventBus(), unused<InstanceAreaResolver>(), MapClock(100), CollisionFlagMap())

    private fun npc() = Npc(NpcServerType(id = 1, name = "Boss", hitpoints = 100)).apply {
        slotId = 1
        assignUid()
    }

    private inline fun <reified T> unused(): T {
        val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        return (field.get(null) as Unsafe).allocateInstance(T::class.java) as T
    }
}
