package org.rsmod.content.raids.theatreofblood

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo

@ResourceLock("ServerCacheManager")
class TheatrePartyBoardTest {
    @Test fun `creating a party at the noticeboard leaves the player on the surface`() {
        val world = TheatreEncounterIntegrationTest.Fixture()
        val player = world.player()
        player.coords = TheatreData.rules.exit
        val type = checkNotNull(ServerCacheManager.getObject("loc.tob_surface_notice_board".asRSCM(RSCMType.LOC)))
        val board = BoundLocInfo(LocInfo(2, player.coords, LocEntity(type.id, 10, 0)), type)
        val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { world.events }, getAlignment = { TextAlignment() }, getCollision = { world.flags },
        )
        player.processedMapClock = player.currentMapClock
        player.launch { world.events.publish(ProtectedAccess(player, this, context), LocEvents.Op1(board, board, type)) }
        assertNull(world.parties.get(player))
        player.resumeActiveCoroutine(ResumePauseButtonInput("component.chatmenu:options", 1))
        val party = checkNotNull(world.parties.get(player))
        assertEquals(listOf(player), party.members)
        assertSame(player, party.leader)
        assertNull(party.session)
        assertNull(world.manager.run(player))
        assertEquals(TheatreData.rules.exit, player.coords)
    }

    @Test fun `entrance cannot allocate a raid before a party is formed`() {
        val world = TheatreEncounterIntegrationTest.Fixture()
        val player = world.player()
        player.coords = TheatreData.rules.exit
        val context = ProtectedAccessContextFactory.empty().copy(getCollision = { world.flags }, getEventBus = { world.events })
        player.launch { world.manager.enter(ProtectedAccess(player, this, context)) }
        assertNull(world.manager.run(player))
        assertNull(world.parties.get(player))
        assertEquals(TheatreData.rules.exit, player.coords)
        assertNull(player.activeCoroutine)
    }

    @Test fun `party leadership transfers and completed rosters do not retain player references`() {
        val world = TheatreEncounterIntegrationTest.Fixture()
        val leader = world.player().apply { displayName = "Leader" }
        val follower = world.player().apply { displayName = "Follower" }
        assertTrue(world.parties.create(leader))
        assertTrue(world.parties.join(follower, "Leader"))
        val party = checkNotNull(world.parties.get(leader))
        world.parties.remove(leader)
        assertNull(world.parties.get(leader))
        assertSame(follower, party.leader)
        assertFalse(world.parties.create(follower))
        world.parties.remove(follower)
        assertTrue(party.members.isEmpty())
        assertNull(world.parties.get(follower))
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
