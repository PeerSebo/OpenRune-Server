package org.rsmod.api.net.rsprot.handlers

import dev.openrune.ServerCacheManager
import net.rsprot.protocol.game.incoming.misc.user.MoveGameClick
import net.rsprot.protocol.game.incoming.misc.user.MoveMinimapClick
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.events.PlayerMoveClick
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.realm.Realm
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.movement.RouteRequestCoord
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import sun.misc.Unsafe

@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MoveClickEventTest {
    @BeforeAll fun cache() { ServerCacheManager.init(241).close() }

    @Test fun `game and minimap clicks reach restraint subscribers even while walking is frozen`() {
        val f = Fixture()
        f.player.frozen = true
        f.events.subscribeUnbound(PlayerMoveClick::class.java) { f.clicks += this; consumed = true }
        f.game.handle(f.player, MoveGameClick(3210, 3220, 0))
        f.minimap.handle(f.player, MoveMinimapClick(3211, 3221, 0, 0, 0, 0, 0, 0))
        assertEquals(listOf(CoordGrid(3210, 3220), CoordGrid(3211, 3221)), f.clicks.map { it.destination })
        assertTrue(f.clicks.all { it.player === f.player })
        assertNull(f.player.routeRequest)
    }

    @Test fun `without a restraint subscriber ordinary movement clicks still create normal routes`() {
        val f = Fixture()
        f.game.handle(f.player, MoveGameClick(3210, 3220, 0))
        assertNotNull(f.player.routeRequest)
        f.player.routeRequest = null
        f.minimap.handle(f.player, MoveMinimapClick(3211, 3221, 0, 0, 0, 0, 0, 0))
        assertNotNull(f.player.routeRequest)
    }

    @Test fun `consuming one players click cannot redirect or cancel another players route`() {
        val f = Fixture()
        val other = Player()
        val route = RouteRequestCoord(CoordGrid(3212, 3222), clientRequest = true)
        other.routeRequest = route
        f.events.subscribeUnbound(PlayerMoveClick::class.java) { if (player === f.player) consumed = true }
        f.game.handle(f.player, MoveGameClick(3210, 3220, 0))
        assertSame(route, other.routeRequest)
        assertNull(f.player.routeRequest)
    }

    private class Fixture {
        val player = Player().apply { coords = CoordGrid(3200, 3200) }
        val events = EventBus()
        val clicks = mutableListOf<PlayerMoveClick>()
        private val factory: ProtectedAccessContextFactory = run {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
            (field.get(null) as Unsafe).allocateInstance(ProtectedAccessContextFactory::class.java) as ProtectedAccessContextFactory
        }
        val game = MoveGameClickHandler(Realm("test"), events, CollisionFlagMap(), factory)
        val minimap = MoveMinimapClickHandler(Realm("test"), events, CollisionFlagMap(), factory)
    }
}
