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
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.route.BoundValidator
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.skills.sailing.SHIPBUILDING_HAMMER
import org.rsmod.content.skills.sailing.SHIPBUILDING_SAW
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorage
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocZoneKey
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SailingShipyardToolsTest {
    private val collision = CollisionFlagMap()
    private lateinit var crates: Map<Int, BoundLocInfo>
    private val transactionField by lazy {
        Class.forName("org.rsmod.api.invtx.InvTransactionsScriptKt")
            .getDeclaredField("cachedInventoryTransactions").apply { isAccessible = true }
    }
    private val storageField by lazy {
        Class.forName("org.rsmod.api.invtx.VirtualInvTransactionsKt")
            .getDeclaredField("cachedPlayerItemStorage").apply { isAccessible = true }
    }
    private var oldTransactions: Any? = null
    private var oldStorage: Any? = null
    private var oldVirtualStorage: InvVirtualStorage? = null

    @BeforeAll
    fun loadNativeCache() {
        val cache = ServerCacheManager.init(241)
        try {
            val square = MapSquareKey.from(CoordGrid(2083, 2731))
            val tiles = MapTileDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, square.id, 0))))
            val locs = MapLocListDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, square.id, 1))))
            for (level in 0..3) for (x in square.x * 64 until (square.x + 1) * 64 step 8)
                for (z in square.z * 64 until (square.z + 1) * 64 step 8) collision.allocateIfAbsent(x, z, level)
            val builder = GameMapBuilder()
            GameMapDecoder.putMaps(collision, square, tiles)
            GameMapDecoder.putLocs(builder, collision, square, tiles, locs)
            crates = buildList {
                for ((packed, zone) in builder.zoneBuilders) {
                    val base = ZoneKey(packed).toCoords()
                    for (entry in zone.build().byte2IntEntrySet()) {
                        val key = LocZoneKey(entry.byteKey)
                        val entity = LocEntity(entry.intValue)
                        if (tools.none { it.first == entity.id }) continue
                        val info = LocInfo(key.layer, base.translate(key.x, key.z), entity)
                        add(BoundLocInfo(info, checkNotNull(ServerCacheManager.getObject(entity.id))))
                    }
                }
            }.associateBy { it.id }
            assertEquals(tools.map { it.first }.toSet(), crates.keys)
        } finally {
            cache.close()
        }
        oldTransactions = transactionField.get(null)
        oldStorage = storageField.get(null)
        oldVirtualStorage = InvVirtualStorageHolder.instance
        with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
            ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
        }
    }

    @AfterAll
    fun restoreTransactions() {
        transactionField.set(null, oldTransactions)
        storageField.set(null, oldStorage)
        InvVirtualStorageHolder.instance = oldVirtualStorage
    }

    @Test
    fun `native hammer and saw Take-from operations give their corresponding tools`() = runBlocking {
        for ((crateId, tool) in tools) {
            val fixture = Fixture(checkNotNull(crates[crateId]), collision)
            assertEquals("Take-from", fixture.type.actions.getOpOrNull(0))
            assertTrue(fixture.interactions.hasOp(fixture.crate, fixture.type, fixture.game.player.vars, InteractionOp.Op1))
            assertFalse(fixture.interactions.hasOp(fixture.crate, fixture.type, fixture.game.player.vars, InteractionOp.Op2))
            fixture.take()
            assertEquals(1, fixture.game.player.inv.count(tool))
            assertEquals(1, fixture.game.player.inv.objs.count { it != null })
            assertEquals(0, fixture.game.player.statMap.getFineXP("stat.construction"))
        }
    }

    @Test
    fun `repeated Take-from gives one unstacked tool per action and stops at capacity`() = runBlocking {
        for ((crateId, tool) in tools) {
            val fixture = Fixture(checkNotNull(crates[crateId]), collision)
            repeat(28) { index ->
                fixture.take()
                assertEquals(index + 1, fixture.game.player.inv.count(tool))
            }
            val full = fixture.game.player.inv.objs.toList()
            repeat(5) { fixture.take() }
            assertEquals(full, fixture.game.player.inv.objs.toList())
            assertTrue(fixture.game.player.inv.objs.all { it?.count == 1 })
        }
    }

    @Test
    fun `a full inventory is unchanged by either tool crate`() = runBlocking {
        for ((crateId, _) in tools) {
            val fixture = Fixture(checkNotNull(crates[crateId]), collision)
            val blocker = checkNotNull(ServerCacheManager.getItem("obj.bones".asRSCM()))
            for (slot in fixture.game.player.inv.indices) fixture.game.player.inv[slot] = InvObj(blocker)
            val before = fixture.game.player.inv.objs.toList()
            fixture.take()
            assertEquals(before, fixture.game.player.inv.objs.toList())
        }
    }

    private class Fixture(val crate: BoundLocInfo, collision: CollisionFlagMap) {
        val game = SailingBoardingLifecycleTest.Fixture(collision = collision)
        val interactions = LocInteractions(BoundValidator(game.collision), game.eventBus)
        val type = checkNotNull(ServerCacheManager.getObject(crate.id))

        init {
            val validator = BoundValidator(collision)
            val approach = (-1..1).asSequence().flatMap { x -> (-1..1).asSequence().map { z -> crate.coords.translate(x, z) } }
                .first { coords ->
                    game.player.coords = coords
                    collision[coords.x, coords.z, coords.level] and CollisionFlag.BLOCK_WALK == 0 &&
                        validator.touches(game.player.avatar, crate)
                }
            assertEquals(approach, game.player.coords)
            val context = ScriptContext(game.eventBus, CheatCommandMap(), EngineQueueCache())
            with(GenericPassageScript(game.locs, game.collision, interactions)) { context.startup() }
            with(SailingShipyardToolsScript()) { context.startup() }
        }

        suspend fun take() {
            assertTrue(BoundValidator(game.collision).touches(game.player.avatar, crate))
            val operation = checkNotNull(interactions.opTrigger(game.player, crate, InteractionOp.Op1))
            assertTrue(operation is LocEvents.Op1, "The supply crate fell through to a generic handler")
            assertTrue(game.eventBus.publish(game.access(), operation))
        }
    }

    private companion object {
        val tools = listOf(59732 to SHIPBUILDING_HAMMER, 59733 to SHIPBUILDING_SAW)
    }
}
