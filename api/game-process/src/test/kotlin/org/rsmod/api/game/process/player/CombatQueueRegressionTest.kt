package org.rsmod.api.game.process.player

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.combat.commons.player.combatRetaliate
import org.rsmod.api.combat.commons.player.queueCombatRetaliate
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.interact.PlayerInteractions
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.interact.InteractionNpcOp
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.movement.RouteRequestCoord
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

@ResourceLock("ServerCacheManager")
class CombatQueueRegressionTest {
    @Test
    fun `several attackers do not replace the first retaliation target`() {
        val f = Fixture()
        val first = f.npc()
        val second = f.npc()
        f.hit(first)
        f.hit(second)
        f.tick()
        assertSame(first, f.target())
        repeat(3) {
            f.hit(second)
            f.tick()
            assertSame(first, f.target())
        }
        assertEquals(5, f.deliveredHits)
    }

    @Test
    fun `manual target change takes precedence over incoming retaliation`() {
        val f = Fixture()
        val original = f.npc()
        val chosen = f.npc()
        f.attack(original)
        f.attack(chosen)
        f.hit(original)
        f.tick()
        assertSame(chosen, f.target())
    }

    @Test
    fun `incoming hits preserve an existing walking route`() {
        val f = Fixture()
        val destination = CoordGrid(3210, 3210)
        f.player.routeDestination.add(destination)
        f.hit(f.npc())
        f.tick()
        assertNull(f.player.interaction)
        assertEquals(listOf(destination), f.player.routeDestination.toList())
    }

    @Test
    fun `a walking click waiting for pathfinding also prevents retaliation`() {
        val f = Fixture()
        val request = RouteRequestCoord(CoordGrid(3210, 3210))
        f.player.routeRequest = request
        f.hit(f.npc())
        f.tick()
        assertNull(f.player.interaction)
        assertSame(request, f.player.routeRequest)
    }

    @Test
    fun `damage stops weak skilling only when it lands`() {
        val f = Fixture()
        val npc = f.npc()
        f.player.weakQueue("queue.generic_queue1", 20)
        f.hit(npc, delay = 3)
        f.tick()
        assertEquals(1, f.player.weakQueueList.size)
        assertNull(f.target())
        f.tick()
        f.tick()
        assertTrue(f.player.weakQueueList.isEmpty)
        assertSame(npc, f.target())
    }

    @Test
    fun `disabled auto retaliation still delivers hits`() {
        val f = Fixture()
        VarPlayerIntMapSetter.set(f.player, "varp.option_nodef", 1)
        f.hit(f.npc())
        f.tick()
        assertNull(f.player.interaction)
        assertEquals(1, f.deliveredHits)
    }

    @Test
    fun `a protected delay finishes before damage and death still stops combat`() {
        val f = Fixture()
        val npc = f.npc()
        f.attack(npc)
        f.player.delay = 2
        f.hit(npc)
        f.tick()
        assertEquals(0, f.deliveredHits)
        assertSame(npc, f.target())
        f.tick()
        assertEquals(1, f.deliveredHits)
        f.player.strongQueue("queue.death", 1)
        f.tick()
        assertNull(f.player.interaction)
        assertTrue(f.player.routeDestination.isEmpty())
    }

    private class Fixture {
        val events = EventBus()
        val npcs = NpcList()
        val flags = CollisionFlagMap()
        val registry = NpcRegistry(npcs, flags, events)
        val interactions = NpcInteractions(events)
        val player = Player().apply {
            coords = CoordGrid(3200, 3200)
            currentMapClock = 0
            processedMapClock = 0
        }
        var deliveredHits = 0
        private val context = ProtectedAccessContextFactory.empty().copy(
            getNpcList = { npcs },
            getEventBus = { events },
            getNpcInteractions = { interactions },
            getPlayerInteractions = { PlayerInteractions(events) },
        )
        private val processor = PlayerQueueProcessor(events) { target, event ->
            assertTrue(ProtectedAccessLauncher.withProtectedAccess(target, context) {
                when (event.id.toInt()) {
                    "queue.com_retaliate_npc".asRSCM() -> combatRetaliate(event.args as NpcUid, 1)
                    "queue.com_retaliate_player".asRSCM() -> combatRetaliate(event.args as PlayerUid, 1)
                    "queue.hit".asRSCM() -> deliveredHits++
                    "queue.death".asRSCM() -> stopAction()
                    else -> error("Unexpected queue $event")
                }
            })
        }

        fun npc(): Npc {
            val coords = player.coords.translateX(1)
            flags.allocateIfAbsent(coords.x, coords.z, coords.level)
            return Npc("npc.icewarrior", coords).also(registry::add)
        }

        fun attack(npc: Npc) = interactions.interact(player, npc, InteractionOp.Op2)
        fun target(): Npc? = (player.interaction as? InteractionNpcOp)?.target
        fun hit(npc: Npc, delay: Int = 1) {
            player.queueCombatRetaliate(npc, delay)
            player.strongQueue("queue.hit", delay)
        }

        fun tick() {
            player.currentMapClock++
            player.processedMapClock++
            processor.process(player)
        }
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
