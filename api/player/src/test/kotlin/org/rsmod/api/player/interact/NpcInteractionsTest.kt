package org.rsmod.api.player.interact

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.NpcServerType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.events.interact.*
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NpcInteractionsTest {
    private lateinit var events: EventBus
    private lateinit var interactions: NpcInteractions
    private lateinit var player: Player
    private lateinit var npc: Npc
    private lateinit var leaf: NpcServerType

    @BeforeAll fun cache() { ServerCacheManager.init(241).close() }

    @BeforeEach fun fixture() {
        events = EventBus()
        interactions = NpcInteractions(events)
        player = Player()
        npc = Npc(checkNotNull(ServerCacheManager.getNpc("npc.thessalia".asRSCM())))
        leaf = checkNotNull(interactions.multiNpc(npc.visType, player.vars))
        assertEquals("npc.thessalia_normal".asRSCM(), leaf.id)
        assertNotEquals(npc.id, leaf.id)
        events.subscribeSuspend<ProtectedAccess, NpcDefaultEvents.Op1>(NpcDefaultEvents.Op1::class.java, OpDefaultEvent.ID.toLong()) {}
        events.subscribeSuspend<ProtectedAccess, NpcDefaultEvents.Ap1>(NpcDefaultEvents.Ap1::class.java, OpDefaultEvent.ID.toLong()) {}
    }

    @Test fun `parent dialogue wins over visible fallback and global default`() {
        events.subscribeSuspend<ProtectedAccess, NpcEvents.Op1>(NpcEvents.Op1::class.java, npc.id.toLong()) {}
        events.subscribeSuspend<ProtectedAccess, NpcUnimplementedEvents.Op1>(NpcUnimplementedEvents.Op1::class.java, leaf.id.toLong()) {}
        val result = assertInstanceOf(NpcEvents.Op1::class.java, interactions.opTrigger(player, npc, InteractionOp.Op1))
        assertEquals(npc.id.toLong(), result.id)
        assertSame(npc, result.npc)
    }

    @Test fun `visible dialogue retains priority and is keyed to its registered type`() {
        events.subscribeSuspend<ProtectedAccess, NpcEvents.Op1>(NpcEvents.Op1::class.java, npc.id.toLong()) {}
        events.subscribeSuspend<ProtectedAccess, NpcEvents.Op1>(NpcEvents.Op1::class.java, leaf.id.toLong()) {}
        val result = assertInstanceOf(NpcEvents.Op1::class.java, interactions.opTrigger(player, npc, InteractionOp.Op1))
        assertEquals(leaf.id.toLong(), result.id)
        assertTrue(events.contains(result::class.java, result.id))
        assertSame(npc, result.npc)
    }

    @Test fun `parent content dialogue wins over visible fallback`() {
        val type = npc.type.copy(contentGroup = 12345)
        events.subscribeSuspend<ProtectedAccess, NpcContentEvents.Op1>(NpcContentEvents.Op1::class.java, type.contentGroup.toLong()) {}
        events.subscribeSuspend<ProtectedAccess, NpcUnimplementedEvents.Op1>(NpcUnimplementedEvents.Op1::class.java, leaf.id.toLong()) {}
        val result = assertInstanceOf(NpcContentEvents.Op1::class.java, interactions.opTrigger(player, npc, InteractionOp.Op1, type))
        assertEquals(type.contentGroup.toLong(), result.id)
    }

    @Test fun `visible shop fallback is keyed to its registered type`() {
        events.subscribeSuspend<ProtectedAccess, NpcUnimplementedEvents.Op3>(NpcUnimplementedEvents.Op3::class.java, leaf.id.toLong()) {}
        val result = assertInstanceOf(NpcUnimplementedEvents.Op3::class.java, interactions.opTrigger(player, npc, InteractionOp.Op3))
        assertEquals(leaf.id.toLong(), result.id)
        assertTrue(events.contains(result::class.java, result.id))
    }

    @Test fun `parent approach wins over generic approach default`() {
        events.subscribeSuspend<ProtectedAccess, NpcEvents.Ap1>(NpcEvents.Ap1::class.java, npc.id.toLong()) {}
        val result = assertInstanceOf(NpcEvents.Ap1::class.java, interactions.apTrigger(player, npc, InteractionOp.Op1))
        assertEquals(npc.id.toLong(), result.id)
    }

    @Test fun `shop fallback cannot replace an existing service approach at close range`() {
        events.subscribeSuspend<ProtectedAccess, NpcUnimplementedEvents.Op3>(NpcUnimplementedEvents.Op3::class.java, leaf.id.toLong()) {}
        events.subscribeSuspend<ProtectedAccess, NpcEvents.Ap3>(NpcEvents.Ap3::class.java, npc.id.toLong()) {}
        assertNull(interactions.opTrigger(player, npc, InteractionOp.Op3))
        val result = assertInstanceOf(NpcEvents.Ap3::class.java, interactions.apTrigger(player, npc, InteractionOp.Op3))
        assertEquals(npc.id.toLong(), result.id)
    }

    @Test fun `global operation default cannot replace an existing service approach`() {
        events.subscribeSuspend<ProtectedAccess, NpcEvents.Ap1>(NpcEvents.Ap1::class.java, npc.id.toLong()) {}
        assertNull(interactions.opTrigger(player, npc, InteractionOp.Op1))
        assertInstanceOf(NpcEvents.Ap1::class.java, interactions.apTrigger(player, npc, InteractionOp.Op1))
    }

    @Test fun `content approach takes priority over global operation default`() {
        val type = npc.type.copy(contentGroup = 12345)
        events.subscribeSuspend<ProtectedAccess, NpcContentEvents.Ap1>(NpcContentEvents.Ap1::class.java, type.contentGroup.toLong()) {}
        assertNull(interactions.opTrigger(player, npc, InteractionOp.Op1, type))
        val result = assertInstanceOf(NpcContentEvents.Ap1::class.java, interactions.apTrigger(player, npc, InteractionOp.Op1, type))
        assertEquals(type.contentGroup.toLong(), result.id)
    }

    @Test fun `visible approach retains priority and dispatches to its registered type`() {
        events.subscribeSuspend<ProtectedAccess, NpcEvents.Ap1>(NpcEvents.Ap1::class.java, npc.id.toLong()) {}
        events.subscribeSuspend<ProtectedAccess, NpcEvents.Ap1>(NpcEvents.Ap1::class.java, leaf.id.toLong()) {}
        val result = assertInstanceOf(NpcEvents.Ap1::class.java, interactions.apTrigger(player, npc, InteractionOp.Op1))
        assertEquals(leaf.id.toLong(), result.id)
        assertTrue(events.contains(result::class.java, result.id))
    }

    @Test fun `unhandled NPC keeps generic operation and approach defaults`() {
        assertInstanceOf(NpcDefaultEvents.Op1::class.java, interactions.opTrigger(player, npc, InteractionOp.Op1))
        assertInstanceOf(NpcDefaultEvents.Ap1::class.java, interactions.apTrigger(player, npc, InteractionOp.Op1))
    }

    @Test fun `counter fallback dispatches the visible type without replacing its adjacent operation`() {
        events.subscribeSuspend<ProtectedAccess, NpcUnimplementedEvents.Op3>(NpcUnimplementedEvents.Op3::class.java, leaf.id.toLong()) {}
        events.subscribeSuspend<ProtectedAccess, NpcUnimplementedEvents.Ap3>(NpcUnimplementedEvents.Ap3::class.java, leaf.id.toLong()) {}
        val approach = assertInstanceOf(NpcUnimplementedEvents.Ap3::class.java, interactions.apTrigger(player, npc, InteractionOp.Op3))
        assertEquals(leaf.id.toLong(), approach.id)
        assertInstanceOf(NpcUnimplementedEvents.Op3::class.java, interactions.opTrigger(player, npc, InteractionOp.Op3))
    }

    @Test fun `counter fallback cannot replace an existing parent service operation`() {
        events.subscribeSuspend<ProtectedAccess, NpcEvents.Op3>(NpcEvents.Op3::class.java, npc.id.toLong()) {}
        events.subscribeSuspend<ProtectedAccess, NpcUnimplementedEvents.Ap3>(NpcUnimplementedEvents.Ap3::class.java, leaf.id.toLong()) {}
        assertNull(interactions.apTrigger(player, npc, InteractionOp.Op3))
        assertInstanceOf(NpcEvents.Op3::class.java, interactions.opTrigger(player, npc, InteractionOp.Op3))
    }

    @Test fun `counter fallback cannot replace a content service operation`() {
        val type = npc.type.copy(contentGroup = 12345)
        events.subscribeSuspend<ProtectedAccess, NpcContentEvents.Op3>(NpcContentEvents.Op3::class.java, type.contentGroup.toLong()) {}
        events.subscribeSuspend<ProtectedAccess, NpcUnimplementedEvents.Ap3>(NpcUnimplementedEvents.Ap3::class.java, leaf.id.toLong()) {}
        assertNull(interactions.apTrigger(player, npc, InteractionOp.Op3, type))
        assertInstanceOf(NpcContentEvents.Op3::class.java, interactions.opTrigger(player, npc, InteractionOp.Op3, type))
    }

    @Test fun `counter fallback cannot replace an existing parent service approach`() {
        events.subscribeSuspend<ProtectedAccess, NpcEvents.Ap3>(NpcEvents.Ap3::class.java, npc.id.toLong()) {}
        events.subscribeSuspend<ProtectedAccess, NpcUnimplementedEvents.Ap3>(NpcUnimplementedEvents.Ap3::class.java, leaf.id.toLong()) {}
        val approach = assertInstanceOf(NpcEvents.Ap3::class.java, interactions.apTrigger(player, npc, InteractionOp.Op3))
        assertEquals(npc.id.toLong(), approach.id)
    }
}
