package org.rsmod.content.skills.sailing.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.filesystem.Cache
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import io.netty.buffer.Unpooled
import java.nio.file.Paths
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.player.events.PlayerWorldEntityLocOpEvent
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.loc.LocRegistryRegion
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.worldentity.WorldEntityRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.worldentity.WorldEntityRepository
import org.rsmod.content.skills.sailing.BoatDeckService
import org.rsmod.content.skills.sailing.BoatHullEntity
import org.rsmod.content.skills.sailing.BoatWorldEntityService
import org.rsmod.content.skills.sailing.ON_PLAYER_BOAT_ABOARD
import org.rsmod.content.skills.sailing.SailingBoatVars
import org.rsmod.content.skills.sailing.SailingNavigationService
import org.rsmod.content.skills.sailing.SailingSeaMap
import org.rsmod.content.skills.sailing.boardedBoat
import org.rsmod.content.skills.sailing.boardedBoatType
import org.rsmod.content.skills.sailing.boardedBoatWorld
import org.rsmod.content.skills.sailing.cargoHold
import org.rsmod.content.skills.sailing.configs.SailingDocks
import org.rsmod.content.skills.sailing.isOnPlayerBoat
import org.rsmod.content.skills.sailing.readSailingVar
import org.rsmod.content.skills.sailing.sailingFacilityLockedIn
import org.rsmod.content.skills.sailing.sailingLastDock
import org.rsmod.content.skills.sailing.sailingOnPlayerBoat
import org.rsmod.content.skills.sailing.sailingSelectedBoatSlot
import org.rsmod.content.skills.sailing.writeSailingVar
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.area.AreaIndex
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.WorldEntity
import org.rsmod.game.entity.WorldEntityList
import org.rsmod.game.entity.worldentity.WorldEntityStateEvents
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag
import org.rsmod.routefinder.loc.LocLayerConstants

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SailingBoardingLifecycleTest {
    @BeforeAll
    fun loadPackedCache() {
        ServerCacheManager.init(241).close()
    }

    @Test
    fun `dock without a berth cannot set aboard state or open the panel`() {
        val fixture = Fixture()
        val dock = SailingDocks.ALL.first { !it.canSpawnBoat }
        fixture.player.writeSailingVar(SailingBoatVars.port(1), dock.row.dockId)
        val task = fixture.boardTask().copy(dockDbRow = dock.dbrow, boatPort = dock.row.dockId)
        fixture.finishBoard(task)
        fixture.assertAshore()
    }

    @Test
    fun `avatar allocation failure cannot set aboard state or open the panel`() {
        val fixture = Fixture()
        fixture.eventBus.subscribeUnbound(WorldEntityStateEvents.Create::class.java) {
            error("Simulated avatar allocation failure")
        }
        fixture.finishBoard()
        fixture.assertAshore()
    }

    @Test
    fun `refused deck teleport releases the unused hull and retains ashore state`() {
        val fixture = Fixture()
        fixture.teleportDenied = true
        fixture.finishBoard()
        fixture.assertAshore()
    }

    @Test
    fun `successful boarding commits the actual deck and panel together`() {
        val fixture = Fixture()
        fixture.finishBoard()
        val entity = checkNotNull(fixture.boats.boarded(fixture.player))
        fixture.assertAboard(entity)
        assertEquals(fixture.boats.deckArrivalCoords(entity), fixture.player.coords)
        assertEquals(1, fixture.player.readSailingVar(SailingSidepanel.VAR_VISIBLE))
        assertTrue(SailingSidepanel.INTERFACE in fixture.player.ui)
    }

    @Test
    fun `queued boarding ignores a player who moved before it completed`() {
        val fixture = Fixture()
        val task = fixture.boardTask()
        fixture.player.coords = fixture.player.coords.translate(1, 0)
        fixture.finishBoard(task)
        fixture.assertAshore(checkPosition = false)
    }

    @Test
    fun `queued boarding ignores a changed selected boat or mooring`() {
        for (changeSelection in listOf(false, true)) {
            val fixture = Fixture()
            val task = fixture.boardTask()
            if (changeSelection) {
                fixture.player.sailingSelectedBoatSlot = 2
            } else {
                fixture.player.writeSailingVar(SailingBoatVars.port(1), 1)
            }
            fixture.finishBoard(task)
            fixture.assertAshore()
        }
    }

    @Test
    fun `refused disembark preserves the occupied hull and boarding flags`() {
        val fixture = Fixture()
        val entity = fixture.boardDirectly()
        fixture.teleportDenied = true
        fixture.finishDisembark(fixture.disembarkTask())
        fixture.assertAboard(entity)
    }

    @Test
    fun `missing shore collision preserves the occupied hull on disembark`() {
        val fixture = Fixture(loadShore = false)
        val entity = fixture.boardDirectly()
        fixture.finishDisembark(fixture.disembarkTask())
        fixture.assertAboard(entity)
    }

    @Test
    fun `successful disembark reaches shore before releasing the hull and keeps cargo`() {
        val fixture = Fixture()
        fixture.boardDirectly()
        fixture.addCargo()
        fixture.finishDisembark(fixture.disembarkTask())
        fixture.assertAshore()
        fixture.assertCargoPresent()
    }

    @Test
    fun `refused escape preserves cargo and the occupied hull`() {
        val fixture = Fixture()
        val entity = fixture.boardDirectly()
        fixture.addCargo()
        fixture.teleportDenied = true
        fixture.finishEscape(fixture.escapeTask())
        fixture.assertAboard(entity)
        fixture.assertCargoPresent()
        assertEquals(1, fixture.player.sailingFacilityLockedIn)
    }

    @Test
    fun `missing shore collision cannot destroy escape cargo`() {
        val fixture = Fixture(loadShore = false)
        val entity = fixture.boardDirectly()
        fixture.addCargo()
        fixture.finishEscape(fixture.escapeTask())
        fixture.assertAboard(entity)
        fixture.assertCargoPresent()
    }

    @Test
    fun `successful escape from a moving boat removes only expendable cargo after arrival`() {
        val fixture = Fixture()
        val entity = fixture.boardDirectly()
        fixture.addCargo()
        val task = fixture.escapeTask()
        entity.rootCoords = entity.rootCoords.translate(0, 1)
        fixture.finishEscape(task)
        fixture.assertAshore()
        assertNull(fixture.player.inv[0])
        assertNull(fixture.player.cargoHold(1)[0])
        assertEquals("obj.coins".asRSCM(), fixture.player.inv[1]?.id)
        assertEquals(0, fixture.player.sailingFacilityLockedIn)
    }

    @Test
    fun `stale exits cannot release a replacement hull or wipe its cargo`() {
        val fixture = Fixture()
        fixture.boardDirectly()
        fixture.addCargo()
        val disembark = fixture.disembarkTask()
        val escape = fixture.escapeTask()
        fixture.player.coords = fixture.shore
        assertTrue(fixture.boats.despawn(fixture.player))
        val replacement = fixture.boardDirectly()
        fixture.finishDisembark(disembark)
        fixture.finishEscape(escape)
        fixture.assertAboard(replacement)
        fixture.assertCargoPresent()
    }

    @Test
    fun `invalid owned slot or type cannot replace a live hull`() {
        val fixture = Fixture()
        val entity = fixture.boardDirectly()
        val root = entity.rootCoords
        assertNull(fixture.boats.spawn(fixture.player, 0, fixture.hull.typeId, root))
        assertNull(fixture.boats.spawn(fixture.player, 2, fixture.hull.typeId, root))
        fixture.player.writeSailingVar(SailingBoatVars.owned(2), 1)
        fixture.player.writeSailingVar(SailingBoatVars.field(2, "type"), fixture.hull.typeId)
        assertNull(fixture.boats.spawn(fixture.player, 2, BoatHullEntity.SKIFF.typeId, root))
        fixture.assertAboard(entity)
    }

    @Test
    fun `queued escape cannot use a different dock anchor or boat slot`() {
        for (changeSelection in listOf(false, true)) {
            val fixture = Fixture()
            val entity = fixture.boardDirectly()
            fixture.addCargo()
            val task = fixture.escapeTask()
            if (changeSelection) {
                fixture.player.writeSailingVar(SailingBoatVars.owned(2), 1)
                fixture.player.sailingSelectedBoatSlot = 2
            } else {
                fixture.player.sailingLastDock = 1
            }
            fixture.finishEscape(task)
            fixture.assertAboard(entity)
            fixture.assertCargoPresent()
        }
    }

    @Test
    fun `stale aboard flags cannot allow an escape from outside the deck`() {
        val fixture = Fixture()
        fixture.boardDirectly()
        fixture.addCargo()
        val task = fixture.escapeTask()
        fixture.player.coords = fixture.shore
        fixture.finishEscape(task)
        fixture.assertCargoPresent()
        assertEquals(fixture.shore, fixture.player.coords)
        assertEquals(1, fixture.registry.count())
    }

    @Test
    fun `logout still relocates off the deck before releasing the session hull`() {
        val fixture = Fixture()
        fixture.boardDirectly()
        fixture.addCargo()
        fixture.logout()
        fixture.assertAshore()
        fixture.assertCargoPresent()
    }

    @Test
    fun `root dock approval only accepts nearby op1 targets on the owner's live hull`() {
        val fixture = Fixture()
        val entity = fixture.boardDirectly()
        assertTrue(fixture.rootRequest(entity).also(fixture::approveRootOp).accepted)
        val remote = fixture.rootRequest(entity, coords = entity.rootCoords.translate(20, 0))
        assertFalse(remote.also(fixture::approveRootOp).accepted)
        val otherOp = fixture.rootRequest(entity, op = InteractionOp.Op2)
        assertFalse(otherOp.also(fixture::approveRootOp).accepted)
        val wrongLevel = fixture.rootRequest(entity, coords = CoordGrid(entity.rootCoords.x, entity.rootCoords.z, 1))
        assertFalse(wrongLevel.also(fixture::approveRootOp).accepted)
        entity.ownerSlot = fixture.player.slotId + 1
        assertFalse(fixture.rootRequest(entity).also(fixture::approveRootOp).accepted)
    }

    @Test
    fun `buoy docking updates selected boat mooring only for an aboard player in range`() {
        val fixture = Fixture()
        val entity = fixture.boardDirectly()
        fixture.player.writeSailingVar(SailingBoatVars.port(1), 1)
        fixture.buoyDock(fixture.rootRequest(entity, coords = entity.rootCoords.translate(20, 0)).loc)
        assertEquals(1, fixture.player.readSailingVar(SailingBoatVars.port(1)))
        fixture.buoyDock(fixture.rootRequest(entity).loc)
        assertEquals(fixture.dock.row.dockId, fixture.player.readSailingVar(SailingBoatVars.port(1)))
        assertEquals(fixture.dock.row.dockId, fixture.player.sailingLastDock)
        fixture.assertAboard(entity)
    }

    @Test
    fun `board previous prefers exact selected slot over another boat of the same type`() {
        val fixture = Fixture()
        fixture.player.writeSailingVar(SailingBoatVars.owned(2), 1)
        fixture.player.writeSailingVar(SailingBoatVars.field(2, "type"), BoatHullEntity.RAFT.typeId)
        fixture.player.sailingSelectedBoatSlot = 2
        assertEquals(2, fixture.previousSlot(listOf(1, 2)))
        assertEquals(1, fixture.previousSlot(listOf(1)))
    }

    @Test
    fun `stale boat login relocates missing instance deck to authored dock`() {
        val fixture = Fixture()
        fixture.player.coords = CoordGrid(6403, 6402, 1)
        fixture.player.sailingOnPlayerBoat = ON_PLAYER_BOAT_ABOARD
        fixture.player.boardedBoat = 1
        fixture.player.boardedBoatWorld = 19
        fixture.player.sailingFacilityLockedIn = 1
        fixture.login()
        fixture.assertAshore()
        assertEquals(0, fixture.player.sailingFacilityLockedIn)
    }

    @Test
    fun `stale boat login preserves coordinates already ashore`() {
        val fixture = Fixture()
        val ashore = fixture.shore.translate(3, 0)
        fixture.player.coords = ashore
        fixture.player.sailingOnPlayerBoat = ON_PLAYER_BOAT_ABOARD
        fixture.login()
        assertEquals(ashore, fixture.player.coords)
        fixture.assertAshore(checkPosition = false)
    }

    @Test
    fun `every authored dock permits all hull sizes to board and disembark`() {
        val gangplanks = mapOf(
            SailingDocks.PORT_SARIM to CoordGrid(3051, 3193),
            SailingDocks.THE_PANDEMONIUM to CoordGrid(3070, 2987),
            SailingDocks.PORT_KHAZARD to CoordGrid(2686, 3162),
            SailingDocks.CATHERBY to CoordGrid(2796, 3412),
        )
        for ((dock, coords) in gangplanks) {
            for (hull in BoatHullEntity.entries) {
                val fixture = Fixture(dock = dock, hull = hull)
                fixture.finishBoard()
                val entity = checkNotNull(fixture.boats.boarded(fixture.player))
                fixture.assertAboard(entity)
                assertEquals(dock.seaCoordsFor(hull.typeId), entity.rootCoords)
                val type = checkNotNull(ServerCacheManager.getObject(checkNotNull(dock.gangplank).asRSCM()))
                val loc = BoundLocInfo(LocInfo(2, coords, LocEntity(type.id, 10, 0)), type)
                val request = PlayerWorldEntityLocOpEvent(fixture.player, entity, loc, InteractionOp.Op1)
                fixture.approveRootOp(request)
                assertTrue(request.accepted, "${dock.dbrow} ${hull.name} cannot reach its gangplank")
                fixture.finishDisembark(fixture.disembarkTask())
                fixture.assertAshore()
            }
        }
    }

    @Test
    fun `shipyard gangplank boards an owned hull and returns to the workshop`() {
        val fixture = Fixture()
        val shore = CoordGrid(2086, 2724)
        fixture.collision.allocateIfAbsent(shore.x, shore.z, shore.level)
        fixture.player.coords = shore
        val savedPort = fixture.player.readSailingVar(SailingBoatVars.port(1))
        fixture.finishBoard(SailingBoardTask.capture(fixture.player, null, 1))
        val entity = checkNotNull(fixture.boats.boarded(fixture.player))
        fixture.assertAboard(entity)
        val type = checkNotNull(ServerCacheManager.getObject("loc.sailing_gangplank_shipyard".asRSCM()))
        val loc = BoundLocInfo(LocInfo(2, CoordGrid(2087, 2723), LocEntity(type.id, 10, 0)), type)
        val request = PlayerWorldEntityLocOpEvent(fixture.player, entity, loc, InteractionOp.Op1)
        fixture.approveRootOp(request)
        assertTrue(request.accepted)
        fixture.finishDisembark(SailingDisembarkTask(null, checkNotNull(SailingBoatDeparture.capture(fixture.player, fixture.boats))))
        assertEquals(shore, fixture.player.coords)
        assertNull(fixture.boats.boarded(fixture.player))
        assertEquals(savedPort, fixture.player.readSailingVar(SailingBoatVars.port(1)))
    }

    @Test
    fun `null dock cannot board a shipyard hull from a different area`() {
        val fixture = Fixture()
        fixture.finishBoard(SailingBoardTask.capture(fixture.player, null, 1))
        fixture.assertAshore()
    }

    internal class Fixture(
        loadShore: Boolean = true,
        val dock: SailingDocks.SailingDock = SailingDocks.PORT_SARIM,
        val hull: BoatHullEntity = BoatHullEntity.RAFT,
        val collision: CollisionFlagMap = CollisionFlagMap(),
        seaMap: SailingSeaMap = SailingSeaMap(collision),
    ) {
        val shore = checkNotNull(dock.landCoords)
        val clock = MapClock(100)
        val eventBus = EventBus()
        private val storage = LocZoneStorage()
        private val updates = ZoneUpdateMap()
        private val normalLocs = LocRegistryNormal(updates, collision, storage)
        val regions = RegionRegistry(
            RegionListSmall(), RegionListLarge(),
            normalLocs, collision, storage,
            NpcRegistry(NpcList(), collision, eventBus),
            ControllerRegistry(clock, ControllerList()), ZonePlayerActivityBitSet(),
        )
        val locRegistry = LocRegistry(storage, normalLocs, LocRegistryRegion(updates, collision, storage, regions))
        val locs = LocRepository(clock, locRegistry, regions)
        val registry = WorldEntityRegistry(WorldEntityList(), eventBus)
        val boats = BoatWorldEntityService(
            WorldEntityRepository(registry, RegionRepository(regions)), clock, eventBus, BoatDeckService(locs),
        )
        val navigation = SailingNavigationService(boats, seaMap, clock)
        private val gangplank = GangplankScript(boats, collision, navigation)
        private val helm = HelmScript(eventBus, boats, navigation)
        var teleportDenied = false
        val player = Player().apply {
            slotId = 1
            currentMapClock = clock.cycle
            coords = shore
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
            statMap.setBaseLevel("stat.sailing", 99)
            writeSailingVar(SailingBoatVars.owned(1), 1)
            writeSailingVar(SailingBoatVars.field(1, "type"), hull.typeId)
            writeSailingVar(SailingBoatVars.port(1), dock.row.dockId)
            sailingSelectedBoatSlot = 1
        }

        init {
            val source = hull.deckSource.southWestZone
            for (level in 0..3) {
                for (x in 0 until hull.deckSource.zoneSizeX) {
                    for (z in 0 until hull.deckSource.zoneSizeZ) {
                        collision.allocateIfAbsent((source.x + x) * 8, (source.z + z) * 8, level)
                    }
                }
            }
            for (loc in nativeDeckLocs) {
                val zone = ZoneKey.from(loc.coords)
                if (zone.x in source.x until source.x + hull.deckSource.zoneSizeX &&
                    zone.z in source.z until source.z + hull.deckSource.zoneSizeZ) {
                    normalLocs.add(loc)
                }
            }
            for (coords in nativeBlockedDeckTiles) {
                val zone = ZoneKey.from(coords)
                if (zone.x in source.x until source.x + hull.deckSource.zoneSizeX &&
                    zone.z in source.z until source.z + hull.deckSource.zoneSizeZ) {
                    collision.add(coords.x, coords.z, coords.level, CollisionFlag.BLOCK_WALK)
                }
            }
            if (loadShore) {
                collision.allocateIfAbsent(shore.x, shore.z, shore.level)
            }
        }

        fun access() = ProtectedAccess(
            player, GameCoroutine(),
            ProtectedAccessContext(
                getRandom = { error("unused") },
                getEventBus = { eventBus },
                getNpcList = { error("unused") },
                getPlayerList = { error("unused") },
                getCollision = { collision },
                getAreaChecker = { AreaChecker(regions, AreaIndex()) },
                getAlignment = { error("unused") },
                getLocInteractions = { error("unused") },
                getNpcInteractions = { error("unused") },
                getPlayerInteractions = { error("unused") },
                getHeldInteractions = { error("unused") },
                getWornInteractions = { error("unused") },
                getMusicPlayer = { error("unused") },
                getMarketPrices = { error("unused") },
                getInstantHitProcessor = { error("unused") },
                getTeleportValidator = {
                    PlayerTeleportValidator(setOf(PlayerTeleportValidateHook { _, _, _ ->
                        if (teleportDenied) "Test teleport refused." else null
                    }))
                },
                getWorldRepo = { error("unused") },
                getHitModifier = { error("unused") },
            ),
        )

        fun boardTask() = SailingBoardTask.capture(player, dock.dbrow, 1)

        fun disembarkTask() = SailingDisembarkTask(
            dock.dbrow, checkNotNull(SailingBoatDeparture.capture(player, boats)),
        )

        fun escapeTask() = SailingEscapeTask(
            dock.dbrow, checkNotNull(SailingBoatDeparture.capture(player, boats)),
        )

        fun finishBoard(task: SailingBoardTask = boardTask()) {
            boardCommit.invoke(gangplank, access(), task)
        }

        fun finishDisembark(task: SailingDisembarkTask) {
            disembarkCommit.invoke(gangplank, access(), task)
        }

        fun finishEscape(task: SailingEscapeTask) {
            escapeCommit.invoke(helm, access(), task)
        }

        fun logout() {
            logoutCommit.invoke(gangplank, player)
        }

        fun login() {
            loginCommit.invoke(gangplank, player)
        }

        fun previousSlot(candidates: List<Int>): Int? = previousSlotMethod.invoke(gangplank, access(), candidates) as Int?

        fun rootRequest(
            entity: WorldEntity,
            coords: CoordGrid = entity.rootCoords,
            op: InteractionOp = InteractionOp.Op1,
        ): PlayerWorldEntityLocOpEvent {
            val type = checkNotNull(ServerCacheManager.getObject("loc.sailing_docking_buoy_port_sarim".asRSCM()))
            val loc = BoundLocInfo(LocInfo(2, coords, LocEntity(type.id, 10, 0)), type)
            return PlayerWorldEntityLocOpEvent(player, entity, loc, op)
        }

        fun approveRootOp(request: PlayerWorldEntityLocOpEvent) {
            approveRootOpMethod.invoke(gangplank, request)
        }

        fun buoyDock(loc: BoundLocInfo) {
            buoyDockMethod.invoke(gangplank, access(), dock, loc)
        }

        fun boardDirectly(): WorldEntity {
            val entity = checkNotNull(boats.spawn(player, 1, hull.typeId, checkNotNull(dock.seaCoordsFor(hull.typeId))))
            player.coords = boats.deckArrivalCoords(entity)
            player.sailingOnPlayerBoat = ON_PLAYER_BOAT_ABOARD
            player.boardedBoat = 1
            player.boardedBoatType = hull.typeId
            player.boardedBoatWorld = entity.slotId
            player.sailingLastDock = dock.row.dockId
            player.sailingFacilityLockedIn = 1
            return entity
        }

        fun addCargo() {
            // Native courier cargo, cache-documented in SailingCargoWipe (category 2294).
            val cargo = InvObj(checkNotNull(ServerCacheManager.getItem(32435)))
            player.inv[0] = cargo
            player.cargoHold(1)[0] = cargo
            player.inv[1] = InvObj(checkNotNull(ServerCacheManager.getItem("obj.coins".asRSCM())))
        }

        fun assertCargoPresent() {
            assertEquals(32435, player.inv[0]?.id)
            assertEquals(32435, player.cargoHold(1)[0]?.id)
            assertEquals("obj.coins".asRSCM(), player.inv[1]?.id)
        }

        fun assertAboard(entity: WorldEntity) {
            assertSame(entity, boats.boarded(player))
            assertEquals(1, boats.boardedSlot(player))
            assertTrue(player.coords in entity)
            assertTrue(player.isOnPlayerBoat())
            assertEquals(1, player.boardedBoat)
            assertEquals(entity.slotId, player.boardedBoatWorld)
            assertEquals(1, registry.count())
        }

        fun assertAshore(checkPosition: Boolean = true) {
            if (checkPosition) assertEquals(shore, player.coords)
            assertFalse(player.isOnPlayerBoat())
            assertEquals(0, player.boardedBoat)
            assertEquals(0, player.boardedBoatWorld)
            assertNull(boats.boarded(player))
            assertNull(boats.boardedSlot(player))
            assertEquals(0, registry.count())
            assertEquals(0, player.readSailingVar(SailingSidepanel.VAR_VISIBLE))
            assertFalse(SailingSidepanel.INTERFACE in player.ui)
        }
    }

    private companion object {
        val nativeBlockedDeckTiles: List<CoordGrid> by lazy {
            val cache = Cache.load(Paths.get(".data/cache/SERVER"))
            try {
                val buffer = Unpooled.wrappedBuffer(checkNotNull(cache.data(5, 15460, 0)))
                try {
                    buildList {
                        for (level in 0..3) for (x in 0..63) for (z in 0..63) {
                            var flags = 0
                            while (true) {
                                val opcode = buffer.readUnsignedShort()
                                if (opcode == 0) break
                                if (opcode == 1) { buffer.readUnsignedByte(); break }
                                if (opcode <= 49) buffer.readUnsignedShort()
                                else if (opcode <= 81) flags = opcode - 49
                            }
                            if (flags and 1 != 0) add(CoordGrid(3840 + x, 6400 + z, level))
                        }
                    }
                } finally { buffer.release() }
            } finally { cache.close() }
        }

        /** Actual packed source-map locs, so absent hull layers cannot pass on an empty mock map. */
        val nativeDeckLocs: List<LocInfo> by lazy {
            val cache = Cache.load(Paths.get(".data/cache/SERVER"))
            try {
                val bytes = checkNotNull(cache.data(5, 15460, 1))
                val buffer = Unpooled.wrappedBuffer(bytes)
                try {
                    fun smart(): Int = if (buffer.getUnsignedByte(buffer.readerIndex()).toInt() < 128) {
                        buffer.readUnsignedByte().toInt()
                    } else buffer.readUnsignedShort() - 32768
                    buildList {
                        var id = -1
                        while (buffer.isReadable) {
                            var delta = 0
                            do {
                                val part = smart()
                                delta += part
                            } while (part == 32767)
                            if (delta == 0) break
                            id += delta
                            var packed = 0
                            while (buffer.isReadable) {
                                val diff = smart()
                                if (diff == 0) break
                                packed += diff - 1
                                val attributes = buffer.readUnsignedByte().toInt()
                                val coords = CoordGrid(3840 + ((packed shr 6) and 63), 6400 + (packed and 63), packed shr 12)
                                val shape = attributes shr 2
                                add(LocInfo(LocLayerConstants.of(shape), coords, LocEntity(id, shape, attributes and 3)))
                            }
                        }
                    }
                } finally { buffer.release() }
            } finally { cache.close() }
        }

        val boardCommit = GangplankScript::class.java.declaredMethods.single {
            it.name == "finishBoard"
        }.apply { isAccessible = true }
        val disembarkCommit = GangplankScript::class.java.declaredMethods.single {
            it.name == "finishDisembark"
        }.apply { isAccessible = true }
        val escapeCommit = HelmScript::class.java.declaredMethods.single {
            it.name == "finishEscape"
        }.apply { isAccessible = true }
        val logoutCommit = GangplankScript::class.java.declaredMethods.single {
            it.name == "leaveBoatOnLogout"
        }.apply { isAccessible = true }
        val loginCommit = GangplankScript::class.java.declaredMethods.single {
            it.name == "recoverFromStaleBoarding"
        }.apply { isAccessible = true }
        val previousSlotMethod = GangplankScript::class.java.declaredMethods.single {
            it.name == "previousBoatSlot"
        }.apply { isAccessible = true }
        val approveRootOpMethod = GangplankScript::class.java.declaredMethods.single {
            it.name == "approveRootDockOp"
        }.apply { isAccessible = true }
        val buoyDockMethod = GangplankScript::class.java.declaredMethods.single {
            it.name == "onBuoyDock"
        }.apply { isAccessible = true }
    }
}
