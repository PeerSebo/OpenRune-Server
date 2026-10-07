package org.rsmod.content.generic.npcs.ambient

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.map.Direction
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@ResourceLock("ServerCacheManager")
class EdgevilleBankFacingTest {
    @Test fun `startup and respawns face each Edgeville teller without changing other bankers`() {
        val events = EventBus()
        for (symbol in listOf("npc.banker1", "npc.banker2")) {
            events.subscribeKeyed(NpcStateEvents.Spawn::class.java, symbol.asRSCM().toLong()) {}
        }
        val list = NpcList()
        val npcs = NpcRepository(MapClock(100), NpcRegistry(list, CollisionFlagMap(), events), list)
        with(EdgevilleBankFacing(npcs)) {
            ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
        }
        val tellers = listOf(
            Triple("npc.banker1", CoordGrid(3096, 3489), Direction.West),
            Triple("npc.banker2", CoordGrid(3096, 3491), Direction.West),
            Triple("npc.banker2", CoordGrid(3096, 3492), Direction.North),
            Triple("npc.banker1", CoordGrid(3098, 3492), Direction.North),
        ).map { (symbol, coords, direction) ->
            Npc(symbol, coords).also {
                npcs.add(it, Int.MAX_VALUE)
                assertEquals(direction, it.respawnDir)
            } to direction
        }
        val elsewhere = Npc("npc.banker1", CoordGrid(3095, 3492)).apply {
            respawnDirection(Direction.South)
        }
        npcs.add(elsewhere, Int.MAX_VALUE)
        tellers.forEach { (npc, _) -> npc.respawnDirection(Direction.South) }
        events.publish(GameLifecycle.Startup)
        tellers.forEach { (npc, direction) -> assertEquals(direction, npc.respawnDir) }
        assertEquals(Direction.South, elsewhere.respawnDir)
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
