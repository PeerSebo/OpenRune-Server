package org.rsmod.content.minigames.giantsfoundry

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import net.rsprot.protocol.game.outgoing.zone.payload.LocAnim
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.queue.WorldQueueList

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class FoundryMachineryTest {
    @Test
    fun `the native public hammer strikes every five ticks without a player operating it`() {
        val events = EventBus()
        val game = FoundryGame(FoundryInterfaces(events), events, XpModifiers(emptySet()))
        val world = FoundryQuestTestWorld(game, events)
        world.loadMap()
        val updates = ZoneUpdateMap()
        val queues = WorldQueueList()
        val process = WorldQueueListProcess(queues)
        val clock = MapClock(100)
        val script = FoundryMachineryScript(world.locs, WorldRepository(updates), queues, clock)
        fun animations() = updates.updatedZones.values.flatMap { it.toList() }.filterIsInstance<LocAnim>()
        script.pulse()
        val expected = List(2) { "seq.giants_foundry_hammer01".asRSCM() }
        assertEquals(expected, animations().map { it.id })
        updates.clear()
        repeat(2) {
            repeat(4) { clock.tick(); process.process() }
            assertTrue(animations().isEmpty())
            clock.tick()
            process.process()
            assertEquals(expected, animations().map { it.id })
            assertEquals(1, queues.size)
            updates.clear()
        }
    }

    companion object {
        @JvmStatic @BeforeAll
        fun cache() { ServerCacheManager.init(241).close() }
    }
}
