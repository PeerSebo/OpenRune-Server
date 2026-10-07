package org.rsmod.content.areas.misc.deathsdomain

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.ui.ifOpenTop
import org.rsmod.api.route.BoundValidator
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.NoopClient
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.loc.LocLayerConstants
import sun.misc.Unsafe

@ResourceLock("ServerCacheManager")
@OptIn(InternalApi::class)
class DeathsDomainReturnStateTest {
    @Test fun `enter and exit preserve exact origin including packed coordinate zero`() {
        for (origin in listOf(CoordGrid(0), CoordGrid(3238, 3194, 0))) {
            val f = Fixture(origin)
            f.operate("loc.death_office_access_grave")
            f.tick(6)
            assertEquals(Office, f.player.coords)
            assertTrue(Return in f.player.vars)
            assertEquals(origin.packed, f.player.vars[Return])
            f.operate("loc.death_office_exitportal")
            f.tick(5)
            assertEquals(origin, f.player.coords)
            assertFalse(Return in f.player.vars)
        }
    }

    @Test fun `relogging inside the office discards the origin and exits to the graveyard`() {
        val entered = Fixture(CoordGrid(3000, 3000))
        entered.operate("loc.death_office_access_grave")
        entered.tick(6)
        val restored = Fixture(Office)
        restored.player.vars.backing.putAll(entered.player.vars.backing.filterKeys {
            ServerCacheManager.getVarp(it)?.scope == VarpLifetime.Perm
        })
        assertFalse(Return in restored.player.vars)
        restored.operate("loc.death_office_exitportal")
        restored.tick(5)
        assertEquals(CoordGrid(3238, 3194, 0), restored.player.coords)
        assertFalse(Return in restored.player.vars)
        val type = checkNotNull(ServerCacheManager.getVarp(Return.asRSCM()))
        assertEquals(VarpLifetime.Temp, type.scope)
        assertEquals(VarpTransmitLevel.Never, type.transmit)
    }

    private class Fixture(origin: CoordGrid) {
        val player = Player().apply {
            client = NoopClient
            coords = origin
            currentMapClock = 100
            processedMapClock = 100
            ifOpenTop("interface.toplevel_osrs_stretch")
        }
        private val events = EventBus()
        private val collision = CollisionFlagMap().apply {
            for (coords in listOf(origin, Office, CoordGrid(3238, 3194, 0))) {
                allocateIfAbsent(coords.x, coords.z, coords.level)
            }
        }
        private val interactions = LocInteractions(BoundValidator(collision), events)
        private val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { events }, getCollision = { collision },
            getLocInteractions = { interactions },
            getTeleportValidator = { PlayerTeleportValidator(emptySet()) },
            getAreaChecker = { unused<AreaChecker>() },
        )
        private var coroutine = GameCoroutine()
        private var outcome: Result<Unit>? = null

        init {
            with(DeathsDomain(unused())) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        fun operate(symbol: String) {
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM()))
            val coords = player.coords
            val loc = BoundLocInfo(LocInfo(LocLayerConstants.of(10), coords, LocEntity(type.id, 10, 0)), type)
            val event = checkNotNull(interactions.opTrigger(player, loc, InteractionOp.Op1))
            coroutine = GameCoroutine()
            player.activeCoroutine = coroutine
            outcome = null
            val action: suspend () -> Unit = {
                assertTrue(events.publish(ProtectedAccess(player, coroutine, context), event))
            }
            action.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { outcome = result }
            })
            outcome?.getOrThrow()
        }

        fun tick(count: Int) = repeat(count) {
            player.currentMapClock++
            player.processedMapClock = player.currentMapClock
            coroutine.advance()
            outcome?.getOrThrow()
        }
    }

    companion object {
        private const val Return = "varp.deaths_domain_return_coord"
        private val Office = CoordGrid(3171, 5726, 0)

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
        private inline fun <reified T> unused(): T {
            val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").run { isAccessible = true; get(null) as Unsafe }
            return unsafe.allocateInstance(T::class.java) as T
        }
    }
}
