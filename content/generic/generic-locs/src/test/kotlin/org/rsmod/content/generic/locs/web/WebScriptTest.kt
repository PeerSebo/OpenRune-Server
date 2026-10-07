package org.rsmod.content.generic.locs.web

import dev.openrune.ServerCacheManager
import dev.openrune.cache.MAPS
import dev.openrune.definition.codec.ItemCodec
import dev.openrune.definition.codec.SequenceCodec
import dev.openrune.map.GameMapBuilder
import dev.openrune.map.GameMapDecoder
import dev.openrune.map.loc.MapLocListDecoder
import dev.openrune.map.tile.MapTileDecoder
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import dev.openrune.util.Wearpos
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.game.process.player.PlayerMovementProcessor
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.interact.LocUInteractions
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.righthand
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.loc.LocRegistryRegion
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.route.BoundValidator
import org.rsmod.api.route.RouteFactory
import org.rsmod.api.route.StepFactory
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.*
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.map.collision.addLoc
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.RouteFinding
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class WebScriptTest {
    @Test fun `all native web families remove collision and restore their original shape and angle`() {
        for ((intact, slashed) in WebScript.Webs) for (angle in 0..3) {
            val f = Fixture()
            val web = f.addWeb(intact, angle)
            val original = f.flags(web.coords)
            f.wield("obj.wilderness_sword_easy")
            f.click(web)
            assertTrue(f.player.isAccessProtected)
            assertEquals(390, f.player.pendingSequence.id)
            assertEquals(original, f.flags(web.coords))
            f.tick(2)
            assertFalse(f.player.isAccessProtected)
            val cut = checkNotNull(f.locs.findExact(web.coords, web.shape))
            assertEquals(slashed.asRSCM(), cut.id)
            assertEquals(web.angle, cut.angle)
            assertNotEquals(original, f.flags(web.coords))
            f.tick(WebScript.RegrowthTicks - 1)
            assertEquals(cut, f.locs.findExact(web.coords, web.shape))
            f.tick()
            assertEquals(web.entity, f.locs.findExact(web.coords, web.shape)?.entity)
            assertEquals(original, f.flags(web.coords))
        }
    }

    @Test fun `inventory knife displays its native held model without changing equipment`() {
        assertEquals("obj.knife".asRSCM() + 512, knifeHandItem)
        assertTrue(knifeWornModels.all { it >= 0 })
        for (weapon in listOf<String?>(null, "obj.shortbow")) {
            for (useItem in listOf(false, true)) {
                val f = Fixture()
                val web = f.addWeb()
                if (weapon != null) f.wield(weapon)
                f.player.inv[7] = InvObj("obj.knife")
                f.click(web, if (useItem) 7 else null)
                assertEquals(3747, f.player.pendingSequence.id)
                assertEquals(weapon?.asRSCM(), f.player.righthand?.id)
                f.tick(2)
                assertEquals("loc.bigweb_slashed".asRSCM(), f.locs.findExact(web.coords, web.shape)?.id)
                assertEquals("obj.knife".asRSCM(), f.player.inv[7]?.id)
                assertEquals(weapon?.asRSCM(), f.player.righthand?.id)
            }
        }
    }

    @Test fun `carried weapons must be equipped before either web action can use them`() {
        for (symbol in listOf("obj.wilderness_sword_easy", "obj.bronze_scimitar")) for (useItem in listOf(false, true)) {
            val f = Fixture()
            val web = f.addWeb()
            f.player.inv[0] = InvObj(symbol)
            f.click(web, if (useItem) 0 else null)
            f.tick(2)
            assertEquals(web.entity, f.locs.findExact(web.coords, web.shape)?.entity)
            assertTrue(f.client.messages.any { "equipped slash weapon" in it.toString() })
            assertEquals(symbol.asRSCM(), f.player.inv[0]?.id)
        }
    }

    @Test fun `missing blade and jewellery never open a web while failed cuts preserve blocking`() {
        for (symbol in listOf<String?>(null, "obj.amulet_of_power")) {
            val f = Fixture()
            val web = f.addWeb()
            if (symbol != null) f.player.inv[0] = InvObj(symbol)
            val initialSequence = f.player.pendingSequence
            f.click(web, if (symbol == null) null else 0)
            assertFalse(f.player.isAccessProtected)
            assertEquals(initialSequence, f.player.pendingSequence)
            assertTrue(f.client.messages.any { "equipped slash weapon" in it.toString() })
            assertEquals(web.entity, f.locs.findExact(web.coords, web.shape)?.entity)
        }
        val f = Fixture(roll = 99)
        val web = f.addWeb()
        val original = f.flags(web.coords)
        f.player.inv[0] = InvObj("obj.knife")
        f.click(web)
        f.tick(2)
        assertEquals(original, f.flags(web.coords))
        assertEquals(web.entity, f.locs.findExact(web.coords, web.shape)?.entity)
        assertTrue(f.client.messages.any { "fail to cut" in it.toString() })
    }

    @Test fun `weapon success rates use only weapon slash and preserve sword and knife guarantees`() {
        fun chance(symbol: String) = WebScript.successChance(ServerCacheManager.getItem(symbol.asRSCM()))
        assertEquals(50, chance("obj.knife"))
        assertEquals(50, chance("obj.bronze_scimitar"))
        assertEquals(20, chance("obj.bronze_dagger"))
        assertEquals(0, chance("obj.amulet_of_power"))
        assertEquals(0, chance("obj.cert_bronze_scimitar"))
        assertEquals(100, chance("obj.wilderness_sword_easy"))
        assertEquals(100, chance("obj.wilderness_sword_elite"))
    }

    @Test fun `slash ignores stronger unequipped weapons and can select an inventory knife`() {
        for (withKnife in listOf(false, true)) {
            val f = Fixture(roll = 49)
            val web = f.addWeb()
            f.wield("obj.bronze_dagger")
            if (withKnife) f.player.inv[0] = InvObj("obj.knife")
            f.player.inv[1] = InvObj("obj.wilderness_sword_easy")
            f.click(web)
            assertEquals(if (withKnife) 3747 else 390, f.player.pendingSequence.id)
            f.tick(2)
            val expected = if (withKnife) "loc.bigweb_slashed" else "loc.bigweb_slashable"
            assertEquals(expected.asRSCM(), f.locs.findExact(web.coords, web.shape)?.id)
        }
    }

    @Test fun `interrupted cutting leaves the web intact`() {
        val f = Fixture()
        val web = f.addWeb()
        f.wield("obj.wilderness_sword_easy")
        f.click(web)
        f.tick()
        f.player.cancelActiveCoroutine()
        f.tick(3)
        assertEquals(web.entity, f.locs.findExact(web.coords, web.shape)?.entity)
        assertFalse(f.player.isAccessProtected)
    }

    @Test fun `aranea boots cannot bypass blade requirements and stale clicks do not extend openings`() {
        val f = Fixture(roll = 99)
        val web = f.addWeb()
        f.player.worn[Wearpos.Feet.slot] = InvObj("obj.aranea_boots")
        f.click(web)
        f.tick(2)
        assertEquals(web.entity, f.locs.findExact(web.coords, web.shape)?.entity)
        f.wield("obj.wilderness_sword_easy")
        f.click(web)
        f.tick(2)
        assertEquals("loc.bigweb_slashed".asRSCM(), f.locs.findExact(web.coords, web.shape)?.id)
        f.tick(20)
        f.click(web)
        f.tick(WebScript.RegrowthTicks - 20)
        assertEquals(web.entity, f.locs.findExact(web.coords, web.shape)?.entity)
    }

    @Test fun `native Wilderness webs open real walking steps at the Keep Mage Arena and Lava Maze`() {
        val f = Fixture()
        f.nativeMaps()
        f.wield("obj.wilderness_sword_easy")
        val ids = WebScript.Webs.keys.map { it.asRSCM() }.toSet()
        val webs = f.nativeZones.flatMap { zone -> f.locs.findAll(zone).toList() }
            .filter { it.coords.level == 0 && it.id in ids }
        assertTrue(webs.size >= 5, "Expected mapped Wilderness webs, found ${webs.size}")
        val steps = StepFactory(f.collision)
        for (info in webs) {
            val web = BoundLocInfo(info, checkNotNull(ServerCacheManager.getObject(info.id)))
            val before = f.flags(web.coords)
            val candidates = (-1..1).flatMap { x -> (-1..1).flatMap { z ->
                val start = web.coords.translate(x, z)
                listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1).map { (dx, dz) -> start to start.translate(dx, dz) }
            } }.mapNotNull { (start, end) ->
                val opposite = if (f.floorOpen(end)) end else end.translate(end.x - start.x, end.z - start.z)
                if (f.floorOpen(start) && f.floorOpen(opposite) && steps.validated(start, end) == CoordGrid.NULL)
                    Triple(start, end, opposite) else null
            }
            f.player.coords = web.coords.translate(-1, 0)
            f.click(web)
            f.tick(2)
            val crossing = candidates.firstOrNull { (start, end) -> steps.validated(start, end) == end }
            assertNotNull(crossing, "No walking edge opened by native web $info")
            val (start, end, opposite) = checkNotNull(crossing)
            f.player.coords = start
            f.player.routeDestination.add(end)
            f.movement.process(f.player)
            assertEquals(end, f.player.coords, "Normal movement still blocked at $info")
            f.tick(WebScript.RegrowthTicks)
            assertEquals(before, f.flags(web.coords), "Native collision not restored at $info")
            for (side in listOf(start, opposite)) {
                val approach = RouteFinding(f.collision).findRoute(level = side.level,
                    srcX = side.x, srcZ = side.z, destX = web.x, destZ = web.z,
                    destWidth = web.width, destLength = web.length, locShape = web.shapeId,
                    locAngle = web.angleId, blockAccessFlags = web.forceApproachFlags, moveNear = false)
                assertTrue(approach.success, "Cannot approach native web from $side: $info")
            }
        }
    }

    private class Fixture(roll: Int = 0) {
        val events = EventBus()
        val client = RecordingClient()
        val player = Player().apply {
            this.client = this@Fixture.client
            coords = CoordGrid(3157, 3951)
            currentMapClock = 100; processedMapClock = 100
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
        }
        val collision = CollisionFlagMap().apply {
            for (x in 3148..3168) for (z in 3940..3960) allocateIfAbsent(x, z, 0)
        }
        val storage = LocZoneStorage()
        val nativeZones = mutableListOf<ZoneKey>()
        private val updates = ZoneUpdateMap()
        private val clock = MapClock(100)
        private val normal = LocRegistryNormal(updates, collision, storage)
        private val regions = RegionRegistry(RegionListSmall(), RegionListLarge(), normal, collision, storage,
            NpcRegistry(NpcList(), collision, events), ControllerRegistry(clock, ControllerList()), ZonePlayerActivityBitSet())
        val locs = LocRepository(clock, LocRegistry(storage, normal, LocRegistryRegion(updates, collision, storage, regions)), regions)
        val movement = PlayerMovementProcessor(collision, RouteFactory(collision), StepFactory(collision), events)
        private val access = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { events }, getCollision = { collision },
            getRandom = { object : GameRandom {
                override fun of(maxExclusive: Int) = roll.coerceAtMost(maxExclusive - 1)
                override fun of(minInclusive: Int, maxInclusive: Int) = minInclusive
                override fun randomDouble() = 0.0
            } },
        )
        init { with(WebScript(locs)) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() } }

        fun wield(symbol: String) { player.worn[Wearpos.RightHand.slot] = InvObj(symbol) }

        fun addWeb(symbol: String = "loc.bigweb_slashable", angle: Int = 0): BoundLocInfo {
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM()))
            val loc = LocInfo(0, CoordGrid(3158, 3951), LocEntity(type.id, if ("shape1" in symbol) 1 else 0, angle))
            storage.mapLocs[ZoneKey.from(loc.coords), LocZoneKey(ZoneGrid.from(loc.coords), loc.layer)] = loc.entity
            collision.addLoc(loc, type)
            return BoundLocInfo(loc, type)
        }

        fun click(loc: BoundLocInfo, slot: Int? = null) {
            assertTrue(ProtectedAccessLauncher.withProtectedAccess(player, access) {
                val event = if (slot == null) LocInteractions(BoundValidator(collision), events).opTrigger(player, loc, InteractionOp.Op1)
                else {
                    val interactions = LocUInteractions::class.java.getDeclaredConstructor(EventBus::class.java)
                        .apply { isAccessible = true }.newInstance(events)
                    with(interactions) { opTrigger(loc, loc, checkNotNull(ServerCacheManager.getObject(loc.id)),
                        checkNotNull(ServerCacheManager.getItem(player.inv[slot]!!.id)), slot) }
                }
                assertTrue(events.publish(this, checkNotNull(event)))
            })
        }

        fun tick(count: Int = 1) = repeat(count) {
            clock.tick()
            player.currentMapClock = clock.cycle; player.processedMapClock = clock.cycle
            player.pendingSequence = EntitySeq.NULL
            player.activeCoroutine?.advance()
            val process = LocRepository::class.java.declaredMethods.single { it.name.startsWith("processDurations") }
            process.isAccessible = true
            process.invoke(locs)
        }

        fun flags(coords: CoordGrid) = (-2..2).flatMap { x -> (-2..2).map { z ->
            collision[coords.x + x, coords.z + z, coords.level] and
                (CollisionFlag.BLOCK_NPCS or CollisionFlag.BLOCK_PLAYERS).inv()
        } }

        fun floorOpen(coords: CoordGrid): Boolean = collision[coords.x, coords.z, coords.level] and
            (CollisionFlag.LOC or CollisionFlag.BLOCK_WALK or CollisionFlag.GROUND_DECOR) == 0

        fun nativeMaps() {
            val builder = GameMapBuilder()
            for ((key, data) in maps) {
                val tiles = MapTileDecoder.decode(InlineByteBuf(data.first))
                val scenery = MapLocListDecoder.decode(InlineByteBuf(data.second))
                for (level in 0..3) for (x in key.x * 64 until (key.x + 1) * 64 step 8)
                    for (z in key.z * 64 until (key.z + 1) * 64 step 8) collision.allocateIfAbsent(x, z, level)
                GameMapDecoder.putMaps(collision, key, tiles)
                GameMapDecoder.putLocs(builder, collision, key, tiles, scenery)
            }
            for ((zone, data) in builder.zoneBuilders) {
                storage.mapLocs[zone] = data.build()
                nativeZones += ZoneKey(zone)
            }
        }
    }

    private class RecordingClient : Client<Any, Any> {
        val messages = mutableListOf<Any>()
        override fun write(message: Any) { messages += message }
        override fun close() {}
        override fun read(player: Player) {}
        override fun flush() {}
        override fun flushHighPriority() {}
        override fun unregister(service: Any, player: Player) {}
    }

    companion object {
        private val maps = mutableMapOf<MapSquareKey, Pair<ByteArray, ByteArray>>()
        private var knifeHandItem = -1
        private var knifeWornModels = emptyList<Int>()

        @JvmStatic @BeforeAll fun cache() {
            val cache = ServerCacheManager.init(241)
            try {
                val seqId = "seq.human_knife_slash".asRSCM()
                knifeHandItem = SequenceCodec(240).loadData(seqId, checkNotNull(cache.data(2, 12, seqId))).rightHandItem
                val knifeId = "obj.knife".asRSCM()
                val knife = ItemCodec(240).loadData(knifeId, checkNotNull(cache.data(2, 10, knifeId)))
                knifeWornModels = listOf(knife.maleModel0, knife.femaleModel0)
                for ((mx, mz) in listOf(49 to 61, 48 to 61, 47 to 60)) {
                    val group = (mx shl 8) or mz
                    maps[MapSquareKey(mx, mz)] = checkNotNull(cache.data(MAPS, group, 0)) to checkNotNull(cache.data(MAPS, group, 1))
                }
            } finally { cache.close() }
        }
    }
}
