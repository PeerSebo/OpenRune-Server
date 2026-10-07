package org.rsmod.content.raids.theatreofblood

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.game.client.Client
import org.rsmod.game.client.NoopClient
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo

@ResourceLock("ServerCacheManager")
class TheatreRoomExitTest {
    @Test fun `both exit options refuse an uncleared room without starting or changing the encounter`() {
        for (stage in listOf(TheatreManager.LOBBY, TheatreManager.WAITING, TheatreManager.ACTIVE)) {
            for (symbol in EXITS) for (option in 1..2) {
                val world = TheatreEncounterIntegrationTest.Fixture()
                val run = world.party(1, lobby = true)
                run.stage = stage
                if (stage == TheatreManager.ACTIVE) world.encounters.start(run)
                val player = run.members.single()
                val messages = mutableListOf<Any>()
                player.client = object : Client<Any, Any> by NoopClient {
                    override fun write(message: Any) { messages += message }
                }
                val boss = run.boss
                val tile = player.coords
                useExit(world, run, symbol, option)
                assertEquals(1, run.room)
                assertEquals(stage, run.stage)
                assertSame(boss, run.boss)
                assertEquals(tile, player.coords)
                world.advanceQueues()
                assertEquals(0, run.tick)
                assertFalse(player.activeCoroutine?.isSuspended == true)
                assertEquals(listOf("Defeat the inhabitants of this room first."),
                    messages.filterIsInstance<MessageGame>().map { it.message })
            }
        }
    }

    @Test fun `both exit options advance a cleared room to the next waiting area without starting its boss`() {
        for (option in 1..2) {
            val world = TheatreEncounterIntegrationTest.Fixture()
            val run = world.party(1)
            world.encounters.start(run)
            world.manager.roomCleared(run)
            useExit(world, run, EXITS.first(), option)
            assertEquals(2, run.room)
            assertEquals(TheatreManager.WAITING, run.stage)
            assertNull(run.boss)
            assertEquals(run.roomrow.arrival, world.manager.source(run, run.members.single().coords))
        }
    }

    private fun useExit(world: TheatreEncounterIntegrationTest.Fixture, run: TheatreRun,
        symbol: String, option: Int) {
        val player = run.members.single()
        val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM(RSCMType.LOC)))
        val loc = BoundLocInfo(LocInfo(2, player.coords, LocEntity(type.id, 10, 0)), type)
        val context = ProtectedAccessContextFactory.empty().copy(getEventBus = { world.events })
        player.launch {
            val access = ProtectedAccess(player, this, context)
            if (option == 1) world.events.publish(access, LocEvents.Op1(loc, loc, type))
            else world.events.publish(access, LocEvents.Op2(loc, loc, type))
        }
    }

    companion object {
        private val EXITS = listOf("loc.tob_dungeon_walkway_exit_clickbox", "loc.tob_dungeon_xarpus_arena_door_exit")

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
