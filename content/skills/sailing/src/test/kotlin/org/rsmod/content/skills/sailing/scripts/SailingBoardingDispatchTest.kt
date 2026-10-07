package org.rsmod.content.skills.sailing.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.GameMapBuilder
import dev.openrune.map.GameMapDecoder
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM.asRSCM
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.events.PlayerQueueEvents
import org.rsmod.api.player.events.PlayerWorldEntityLocOpEvent
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.route.BoundValidator
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.skills.sailing.BoatHullEntity
import org.rsmod.content.skills.sailing.configs.SailingConstants
import org.rsmod.content.skills.sailing.configs.SailingDocks
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SailingBoardingDispatchTest {
    private lateinit var gangplanks: Map<SailingDocks.SailingDock, BoundLocInfo>
    private val dockCollision = CollisionFlagMap()

    @BeforeAll
    fun loadNativeGangplanks() {
        val cache = ServerCacheManager.init(241)
        try {
            val builder = GameMapBuilder()
            for (square in SailingDocks.AUTHORED.map { MapSquareKey.from(checkNotNull(it.landCoords)) }.distinct()) {
                val tiles = MapTileDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, square.id, 0))))
                val spawns = MapLocListDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, square.id, 1))))
                for (level in 0..3) for (x in square.x * 64 until (square.x + 1) * 64 step 8)
                    for (z in square.z * 64 until (square.z + 1) * 64 step 8) dockCollision.allocateIfAbsent(x, z, level)
                // LINK_BELOW sinks runtime pier locs one plane below landscape placement.
                GameMapDecoder.putMaps(dockCollision, square, tiles)
                GameMapDecoder.putLocs(builder, dockCollision, square, tiles, spawns)
            }
            val locs = buildList {
                for ((packed, zone) in builder.zoneBuilders) {
                    val base = ZoneKey(packed).toCoords()
                    for (entry in zone.build().byte2IntEntrySet()) {
                        val key = LocZoneKey(entry.byteKey)
                        val entity = LocEntity(entry.intValue)
                        val info = LocInfo(key.layer, base.translate(key.x, key.z), entity)
                        add(BoundLocInfo(info, checkNotNull(ServerCacheManager.getObject(entity.id))))
                    }
                }
            }
            gangplanks = SailingDocks.AUTHORED.associateWith { dock ->
                val locId = checkNotNull(dock.gangplank).asRSCM()
                locs.single { it.id == locId }
            }
        } finally {
            cache.close()
        }
    }

    @Test
    fun `native Board clicks allocate every supported hull and Disembark returns to the same dock`() = runBlocking {
        for (dock in SailingDocks.AUTHORED) {
            for (hull in BoatHullEntity.entries) {
                boardAndDisembark(dock, hull, InteractionOp.Op1)
            }
        }
    }

    @Test
    fun `native Board-previous clicks retain the selected hull with generic passages loaded`() = runBlocking {
        for (dock in SailingDocks.AUTHORED) {
            boardAndDisembark(dock, BoatHullEntity.RAFT, InteractionOp.Op2)
        }
    }

    private suspend fun boardAndDisembark(dock: SailingDocks.SailingDock, hull: BoatHullEntity, op: InteractionOp) {
        val fixture = SailingBoardingLifecycleTest.Fixture(dock = dock, hull = hull)
        val interactions = LocInteractions(BoundValidator(fixture.collision), fixture.eventBus)
        val context = ScriptContext(fixture.eventBus, CheatCommandMap(), EngineQueueCache())
        with(GenericPassageScript(fixture.locs, fixture.collision, interactions)) { context.startup() }
        with(GangplankScript(fixture.boats, fixture.collision, fixture.navigation)) { context.startup() }

        val gangplank = checkNotNull(gangplanks[dock])
        val type = checkNotNull(ServerCacheManager.getObject(gangplank.id))
        assertTrue(BoundValidator(dockCollision).touches(fixture.player.avatar, gangplank), "No native dock approach for ${dock.dbrow}")
        assertTrue(interactions.hasOp(gangplank, type, fixture.player.vars, op))
        assertNotEquals(gangplank.id, checkNotNull(interactions.multiLoc(gangplank, type, fixture.player.vars)).id)
        val board = checkNotNull(interactions.opTrigger(fixture.player, gangplank, op))
        assertInstanceOf(LocEvents.Op::class.java, board, "${dock.dbrow} was swallowed by a generic handler")
        assertTrue(fixture.eventBus.publish(fixture.access(), board))
        assertEquals(1, fixture.player.queueList.count(SailingConstants.QUEUE_BOARD))
        fixture.assertAshore()
        dispatchQueued(fixture)
        val entity = checkNotNull(fixture.boats.boarded(fixture.player))
        fixture.assertAboard(entity)

        val request = PlayerWorldEntityLocOpEvent(fixture.player, entity, gangplank, InteractionOp.Op1)
        assertTrue(fixture.eventBus.publish(request))
        assertTrue(request.accepted, "${dock.dbrow}: the boarded hull cannot reach its gangplank")
        val disembark = checkNotNull(interactions.opTrigger(fixture.player, gangplank, InteractionOp.Op1))
        assertInstanceOf(LocEvents.Op1::class.java, disembark)
        assertTrue(fixture.eventBus.publish(fixture.access(), disembark))
        assertEquals(1, fixture.player.queueList.count(SailingConstants.QUEUE_DISEMBARK))
        dispatchQueued(fixture)
        fixture.assertAshore()
    }

    private suspend fun dispatchQueued(fixture: SailingBoardingLifecycleTest.Fixture) {
        val iterator = checkNotNull(fixture.player.queueList.iterator())
        val queue = iterator.next()
        iterator.remove()
        iterator.cleanUp()
        repeat(queue.remainingCycles) {
            fixture.clock.tick()
            fixture.player.currentMapClock = fixture.clock.cycle
        }
        assertTrue(fixture.eventBus.publish(fixture.access(), PlayerQueueEvents.Protected(queue.args, queue.id)))
    }
}
