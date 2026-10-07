package org.rsmod.content.skills.sailing.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.map.GameMapBuilder
import dev.openrune.map.GameMapDecoder
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.util.InlineByteBuf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.route.BoundValidator
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.skills.sailing.BoatBuildService
import org.rsmod.content.skills.sailing.BoatOwnershipService
import org.rsmod.content.skills.sailing.SailingBoatVars
import org.rsmod.content.skills.sailing.SailingSessionState.shipyardReturn
import org.rsmod.content.skills.sailing.configs.SailingDocks
import org.rsmod.content.skills.sailing.writeSailingVar
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.interact.InteractionOp
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
class ShipyardPortalTest {
    private val collision = CollisionFlagMap()
    private lateinit var portal: BoundLocInfo

    @BeforeAll
    fun loadNativeShipyardAndPorts() {
        val cache = ServerCacheManager.init(241)
        try {
            val builder = GameMapBuilder()
            val squares = listOf(
                MapSquareKey.from(CoordGrid(2083, 2731)),
                MapSquareKey.from(checkNotNull(SailingDocks.PORT_SARIM.landCoords)),
                MapSquareKey.from(checkNotNull(SailingDocks.CATHERBY.landCoords)),
            )
            for (square in squares) {
                val tiles = MapTileDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, square.id, 0))))
                val locs = MapLocListDecoder.decode(InlineByteBuf(checkNotNull(cache.data(MAPS, square.id, 1))))
                for (level in 0..3) for (x in square.x * 64 until (square.x + 1) * 64 step 8)
                    for (z in square.z * 64 until (square.z + 1) * 64 step 8) collision.allocateIfAbsent(x, z, level)
                GameMapDecoder.putMaps(collision, square, tiles)
                GameMapDecoder.putLocs(builder, collision, square, tiles, locs)
            }
            portal = buildList {
                for ((packed, zone) in builder.zoneBuilders) {
                    val base = ZoneKey(packed).toCoords()
                    for (entry in zone.build().byte2IntEntrySet()) {
                        val key = LocZoneKey(entry.byteKey)
                        val entity = LocEntity(entry.intValue)
                        if (entity.id != 59722) continue
                        val info = LocInfo(key.layer, base.translate(key.x, key.z), entity)
                        add(BoundLocInfo(info, checkNotNull(ServerCacheManager.getObject(entity.id))))
                    }
                }
            }.single()
            assertEquals(CoordGrid(2083, 2731), portal.coords)
            assertEquals("Exit", checkNotNull(ServerCacheManager.getObject(portal.id)).actions.getOpOrNull(0))
        } finally {
            cache.close()
        }
    }

    @Test
    fun `customising at two ports and using the native portal returns to each entry location after saving`() = runBlocking {
        for ((dock, shipwright) in listOf(
            SailingDocks.PORT_SARIM to "npc.sailing_shipwright_port_sarim",
            SailingDocks.CATHERBY to "npc.sailing_shipwright_catherby",
        )) {
            val fixture = createFixture(dock)
            val origin = fixture.player.coords
            enterShipyard(fixture, shipwright)
            assertEquals(CoordGrid(2068, 2721), fixture.player.coords)
            assertEquals(0, collision[fixture.player.x, fixture.player.z, fixture.player.level] and CollisionFlag.BLOCK_WALK)
            val saved = fixture.player.vars.backing.filterKeys {
                ServerCacheManager.getVarp(it)?.scope == dev.openrune.types.varp.VarpLifetime.Perm
            }
            fixture.player.vars.backing.clear()
            fixture.player.vars.backing.putAll(saved)
            fixture.player.attr.clear()
            exitPortal(fixture)
            assertEquals(origin, fixture.player.coords)
            assertNull(fixture.player.shipyardReturn)
        }
    }

    @Test
    fun `a player already stranded in the shipyard can exit without a boat or saved entry`() = runBlocking {
        val fixture = createFixture()
        fixture.player.writeSailingVar(SailingBoatVars.owned(1), 0)
        exitPortal(fixture)
        assertEquals(SailingDocks.PORT_SARIM.landCoords, fixture.player.coords)
    }

    @Test
    fun `denied portal teleport retains the return destination until exit succeeds`() = runBlocking {
        val fixture = createFixture(SailingDocks.CATHERBY)
        val origin = fixture.player.coords
        enterShipyard(fixture, "npc.sailing_shipwright_catherby")
        val saved = fixture.player.shipyardReturn
        fixture.teleportDenied = true
        exitPortal(fixture)
        assertEquals(MapSquareKey.from(portal.coords), MapSquareKey.from(fixture.player.coords))
        assertEquals(saved, fixture.player.shipyardReturn)
        fixture.teleportDenied = false
        exitPortal(fixture)
        assertEquals(origin, fixture.player.coords)
        assertNull(fixture.player.shipyardReturn)
    }

    @Test
    fun `denied shipwright entry does not leave a saved return coordinate`() = runBlocking {
        val fixture = createFixture()
        val origin = fixture.player.coords
        fixture.teleportDenied = true
        enterShipyard(fixture, "npc.sailing_shipwright_port_sarim")
        assertEquals(origin, fixture.player.coords)
        assertNull(fixture.player.shipyardReturn)
    }

    private fun createFixture(dock: SailingDocks.SailingDock = SailingDocks.PORT_SARIM): SailingBoardingLifecycleTest.Fixture {
        val fixture = SailingBoardingLifecycleTest.Fixture(dock = dock, collision = collision)
        val context = ScriptContext(fixture.eventBus, CheatCommandMap(), EngineQueueCache())
        val interactions = LocInteractions(BoundValidator(collision), fixture.eventBus)
        with(GenericPassageScript(fixture.locs, collision, interactions)) { context.startup() }
        with(ShipyardScript(BoatBuildService(BoatOwnershipService()))) { context.startup() }
        return fixture
    }

    private suspend fun enterShipyard(fixture: SailingBoardingLifecycleTest.Fixture, symbol: String) {
        val npc = Npc(symbol, fixture.player.coords)
        assertEquals("Customise-boat", npc.type.actions.getOpOrNull(3))
        val event = checkNotNull(NpcInteractions(fixture.eventBus).opTrigger(fixture.player, npc, InteractionOp.Op4))
        assertInstanceOf(NpcEvents.Op4::class.java, event)
        assertTrue(fixture.eventBus.publish(fixture.access(), event))
    }

    private suspend fun exitPortal(fixture: SailingBoardingLifecycleTest.Fixture) {
        val validator = BoundValidator(collision)
        val approach = (-1..3).asSequence().flatMap { x -> (-1..1).asSequence().map { z -> portal.coords.translate(x, z) } }
            .first { coords ->
                fixture.player.coords = coords
                collision[coords.x, coords.z, coords.level] and CollisionFlag.BLOCK_WALK == 0 &&
                    validator.touches(fixture.player.avatar, portal)
            }
        assertEquals(approach, fixture.player.coords)
        val interactions = LocInteractions(validator, fixture.eventBus)
        assertTrue(interactions.hasOp(portal, checkNotNull(ServerCacheManager.getObject(portal.id)), fixture.player.vars, InteractionOp.Op1))
        val event = checkNotNull(interactions.opTrigger(fixture.player, portal, InteractionOp.Op1))
        assertInstanceOf(LocEvents.Op1::class.java, event, "The exit portal fell through to generic passages")
        assertTrue(fixture.eventBus.publish(fixture.access(), event))
    }
}
