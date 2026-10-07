package org.rsmod.api.player.interact

import dev.openrune.ServerCacheManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.events.interact.LocCategoryEvents
import org.rsmod.api.player.events.interact.LocContentEvents
import org.rsmod.api.player.events.interact.LocDefaultEvents
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.events.interact.LocUnimplementedEvents
import org.rsmod.api.player.events.interact.OpDefaultEvent
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.route.BoundValidator
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LocInteractionsTest {
    private lateinit var events: EventBus
    private lateinit var interactions: LocInteractions
    private lateinit var player: Player
    private lateinit var parent: BoundLocInfo
    private lateinit var leaf: BoundLocInfo

    @BeforeAll
    fun loadCache() {
        ServerCacheManager.init(241).close()
    }

    @BeforeEach
    fun createInteraction() {
        events = EventBus()
        interactions = LocInteractions(BoundValidator(CollisionFlagMap()), events)
        player = Player()
        val type = checkNotNull(ServerCacheManager.getObject(59835))
        parent = BoundLocInfo(LocInfo(2, CoordGrid(3051, 3193), LocEntity(type.id, 10, 0)), type)
        leaf = checkNotNull(interactions.multiLoc(parent, type, player.vars))
        assertEquals(59832, leaf.id)
        assertNotEquals(parent.id, leaf.id)
        events.subscribeSuspend<ProtectedAccess, LocDefaultEvents.Op1>(LocDefaultEvents.Op1::class.java, OpDefaultEvent.ID.toLong()) {}
        events.subscribeSuspend<ProtectedAccess, LocDefaultEvents.Ap1>(LocDefaultEvents.Ap1::class.java, leaf.id.toLong()) {}
    }

    @Test
    fun `parent operation wins over visible unimplemented and generic defaults`() {
        events.subscribeSuspend<ProtectedAccess, LocEvents.Op1>(LocEvents.Op1::class.java, parent.id.toLong()) {}
        events.subscribeSuspend<ProtectedAccess, LocUnimplementedEvents.Op1>(LocUnimplementedEvents.Op1::class.java, leaf.id.toLong()) {}
        val event = assertInstanceOf(LocEvents.Op1::class.java, interactions.opTrigger(player, parent, InteractionOp.Op1))
        assertEquals(parent.id, event.id.toInt())
        assertEquals(parent, event.loc)
    }

    @Test
    fun `visible operation retains priority over parent operation`() {
        events.subscribeSuspend<ProtectedAccess, LocEvents.Op1>(LocEvents.Op1::class.java, parent.id.toLong()) {}
        events.subscribeSuspend<ProtectedAccess, LocEvents.Op1>(LocEvents.Op1::class.java, leaf.id.toLong()) {}
        val event = assertInstanceOf(LocEvents.Op1::class.java, interactions.opTrigger(player, parent, InteractionOp.Op1))
        assertEquals(leaf.id, event.id.toInt())
        assertEquals(parent, event.loc)
        assertEquals(leaf, event.vis)
    }

    @Test
    fun `parent content operation wins over generic default`() {
        val type = checkNotNull(ServerCacheManager.getObject(parent.id)).copy(contentGroup = 12345)
        events.subscribeSuspend<ProtectedAccess, LocContentEvents.Op1>(LocContentEvents.Op1::class.java, type.contentGroup.toLong()) {}
        val event = assertInstanceOf(LocContentEvents.Op1::class.java, interactions.opTrigger(player, parent, InteractionOp.Op1, type))
        assertEquals(parent.id, event.type.id)
    }

    @Test
    fun `parent category operation wins over generic default`() {
        val type = checkNotNull(ServerCacheManager.getObject(parent.id)).copy(category = 12345)
        events.subscribeSuspend<ProtectedAccess, LocCategoryEvents.Op1>(LocCategoryEvents.Op1::class.java, type.category.toLong()) {}
        val event = assertInstanceOf(LocCategoryEvents.Op1::class.java, interactions.opTrigger(player, parent, InteractionOp.Op1, type))
        assertEquals(parent.id, event.type.id)
    }

    @Test
    fun `parent approach wins over generic default`() {
        events.subscribeSuspend<ProtectedAccess, LocEvents.Ap1>(LocEvents.Ap1::class.java, parent.id.toLong()) {}
        val event = assertInstanceOf(LocEvents.Ap1::class.java, interactions.apTrigger(player, parent, InteractionOp.Op1))
        assertEquals(parent.id, event.id.toInt())
        assertEquals(parent, event.loc)
    }

    @Test
    fun `visible approach retains priority over parent approach`() {
        events.subscribeSuspend<ProtectedAccess, LocEvents.Ap1>(LocEvents.Ap1::class.java, parent.id.toLong()) {}
        events.subscribeSuspend<ProtectedAccess, LocEvents.Ap1>(LocEvents.Ap1::class.java, leaf.id.toLong()) {}
        val event = assertInstanceOf(LocEvents.Ap1::class.java, interactions.apTrigger(player, parent, InteractionOp.Op1))
        assertEquals(leaf.id, event.id.toInt())
        assertEquals(parent, event.loc)
        assertEquals(leaf, event.vis)
    }

    @Test
    fun `unhandled operations and approaches preserve the visible type for fallback scripts`() {
        val operation = assertInstanceOf(LocDefaultEvents.Op1::class.java, interactions.opTrigger(player, parent, InteractionOp.Op1))
        assertEquals(parent, operation.loc)
        assertEquals(leaf, operation.vis)
        assertEquals(leaf.id, operation.type.id)
        val approach = assertInstanceOf(LocDefaultEvents.Ap1::class.java, interactions.apTrigger(player, parent, InteractionOp.Op1))
        assertEquals(parent, approach.loc)
        assertEquals(leaf, approach.vis)
        assertEquals(leaf.id, approach.type.id)
    }
}
