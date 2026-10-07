package org.rsmod.content.generic.npcs.ambient

import dev.openrune.definition.EntityOpsBuilder
import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcMode
import dev.openrune.types.NpcServerType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NpcWanderPolicyTest {
    @Test
    fun `caps wandering without expanding smaller ranges or changing combat leashes`() {
        val ordinary = NpcServerType(id = 1, wanderRange = 12, maxRange = 20, huntRange = 9)
        val local = NpcServerType(id = 2, wanderRange = 1)
        NpcWanderPolicy().apply(listOf(ordinary, local))
        assertEquals(4, ordinary.wanderRange)
        assertEquals(1, local.wanderRange)
        assertEquals(20, ordinary.maxRange)
        assertEquals(9, ordinary.huntRange)
    }

    @Test
    fun `shop options cover every slot and transformed shopkeepers`() {
        val leaf = NpcServerType(id = 1, actions = EntityOpsBuilder().op(4, "Trade-General-Store").build())
        val parent = NpcServerType(id = 2, transforms = mutableListOf(1, -1))
        val outerParent = NpcServerType(id = 3, multiDefault = 2)
        val shop = NpcServerType(id = 4, actions = EntityOpsBuilder().op(0, "Shop").build())
        val all = listOf(outerParent, parent, leaf, shop)
        NpcWanderPolicy().apply(all)
        assertEquals(listOf(3, 3, 3, 3), all.map { it.wanderRange })
    }

    @Test
    fun `stationary and scripted actors retain their movement settings`() {
        val stationary = NpcServerType(id = 1, wanderRange = 0)
        val noMove = NpcServerType(id = 2, wanderRange = 8, moveRestrict = MoveRestrict.NoMove)
        val scripted = NpcServerType(id = 3, wanderRange = 16, defaultMode = NpcMode.None)
        val all = listOf(stationary, noMove, scripted)
        NpcWanderPolicy().apply(all)
        assertEquals(listOf(0, 8, 16), all.map { it.wanderRange })
    }

    @Test
    fun `cyclic transforms do not invent a shop or prevent completion`() {
        val first = NpcServerType(id = 1, transforms = mutableListOf(2))
        val second = NpcServerType(id = 2, transforms = mutableListOf(1))
        NpcWanderPolicy().apply(listOf(first, second))
        assertEquals(4, first.wanderRange)
        assertEquals(4, second.wanderRange)
    }
}
