package org.rsmod.content.areas.city.draynor

import dev.openrune.ServerCacheManager
import dev.openrune.map.area.MapAreaDecoder
import dev.openrune.map.area.MapAreaDefinition
import dev.openrune.map.area.MapAreaDefinitions
import dev.openrune.map.util.InlineByteBuf
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.varp.VarpLifetime
import dev.openrune.types.varp.VarpTransmitLevel
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.game.process.player.PlayerQueueProcessor
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.events.interact.WornObjEvents
import org.rsmod.api.player.hook.GroundItemDropResolver
import org.rsmod.api.player.hook.PlayerRestrictions
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.input.DialogInput
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.HeldInteractions
import org.rsmod.api.player.interact.HeldUInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.recovery.loginExitCoord
import org.rsmod.api.player.ui.closeSubs
import org.rsmod.api.player.worn.HeldEquipOp
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.content.areas.city.draynor.Chronicle.Companion.charges
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.events.SuspendEvent
import org.rsmod.game.MapClock
import org.rsmod.game.area.AreaIndex
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.interact.HeldOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.game.seq.EntitySeq
import org.rsmod.game.spot.EntitySpotanim
import org.rsmod.game.ui.Component
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class DiangoItemsTest {
    @Test fun `adding cards consumes exactly the remaining capacity and preserves excess cards`() {
        val f = Fixture()
        f.player.charges = 997
        f.give(Chronicle.Book)
        f.give(Chronicle.Card, 10)
        assertEquals(3, Chronicle.charge(f.player))
        assertEquals(1000, f.player.charges)
        assertEquals(7, f.player.inv.physicalCount(Chronicle.Card))
        assertEquals(0, Chronicle.charge(f.player))
        assertEquals(7, f.player.inv.physicalCount(Chronicle.Card))
    }

    @Test fun `charging requires a book and consumes every unnoted card without requiring free slots`() {
        val f = Fixture()
        f.give(Chronicle.Card, 10)
        assertEquals(0, Chronicle.charge(f.player))
        f.give(Chronicle.Book)
        repeat(f.player.inv.freeSpace()) { f.give("obj.spade") }
        assertEquals(10, Chronicle.charge(f.player))
        assertEquals(10, f.player.charges)
        assertEquals(0, f.player.inv.physicalCount(Chronicle.Card))
        assertEquals(1, f.player.inv.physicalCount(Chronicle.Book))
    }

    @Test fun `charges survive book destruction replacement and permanent-only reload`() {
        val f = Fixture()
        f.give(Chronicle.Book)
        f.give(Chronicle.Card, 4)
        assertEquals(4, Chronicle.charge(f.player))
        for (slot in f.player.inv.indices) f.player.inv[slot] = null
        val restored = Player().apply {
            vars.backing.putAll(f.player.vars.backing.filterKeys {
                ServerCacheManager.getVarp(it)?.scope == VarpLifetime.Perm
            })
        }
        assertEquals(4, restored.charges)
        val varp = checkNotNull(ServerCacheManager.getVarp(Chronicle.ChargeVar.asRSCM()))
        assertEquals(VarpLifetime.Perm, varp.scope)
        assertEquals(VarpTransmitLevel.Never, varp.transmit)
    }

    @Test fun `teleport denials and empty book do not spend a charge or queue a landing`() {
        for (charge in listOf(0, 7)) {
            val f = Fixture(if (charge == 0) null else "You cannot leave this fight.")
            f.give(Chronicle.Book)
            f.player.charges = charge
            f.teleport()
            assertEquals(charge, f.player.charges)
            assertFalse(Chronicle.Landing in f.player.queueList)
            assertEquals(CoordGrid(3080, 3250), f.player.coords)
        }
    }

    @Test fun `teleport uses Chronicle validation and queues one paid landing despite repeated clicks`() {
        val f = Fixture()
        f.give(Chronicle.Book)
        f.player.charges = 3
        f.teleport()
        assertEquals(TeleportType.Chronicle, f.validated)
        assertEquals(2, f.player.charges)
        assertEquals(1, f.player.queueList.count(Chronicle.Landing))
        f.teleport()
        assertEquals(2, f.player.charges)
        assertEquals(1, f.player.queueList.count(Chronicle.Landing))
        f.player.activeCoroutine?.cancel()
        assertEquals(1, f.player.queueList.count(Chronicle.Landing))
        assertEquals("seq.human_castteleport".asRSCM(), f.player.pendingSequence.id)
        val spot = EntitySpotanim(f.player.pendingSpotanims.getLong(0))
        assertEquals("spotanim.teleport_casting".asRSCM(), spot.id)
        assertEquals(92, spot.height)
        f.processQueue()
        repeat(2) {
            f.tick()
            assertEquals(CoordGrid(3080, 3250), f.player.coords)
        }
        f.tick()
        assertEquals(Chronicle.Destination, f.player.coords)
        assertEquals(2, f.player.charges)
        assertFalse(Chronicle.Landing in f.player.queueList)
    }

    @Test fun `equipped Chronicle uses the same teleport destination and one charge`() {
        val f = Fixture()
        f.player.worn[5] = InvObj(checkNotNull(ServerCacheManager.getItem(Chronicle.Book.asRSCM())), 1)
        f.player.charges = 4
        f.invoke(WornObjEvents.Op3(5, checkNotNull(f.player.worn[5])))
        f.processQueue()
        repeat(3) { f.tick() }
        assertEquals(Chronicle.Destination, f.player.coords)
        assertEquals(3, f.player.charges)
    }

    @Test fun `using either book on card or card on book dispatches the registered charging action`() {
        for (reversed in listOf(false, true)) {
            val f = Fixture()
            f.give(Chronicle.Book)
            f.give(Chronicle.Card, 2)
            val book = checkNotNull(ServerCacheManager.getItem(Chronicle.Book.asRSCM()))
            val card = checkNotNull(ServerCacheManager.getItem(Chronicle.Card.asRSCM()))
            f.run {
                HeldUInteractions(f.events).interact(this, inv,
                    if (reversed) card else book, if (reversed) 1 else 0,
                    if (reversed) book else card, if (reversed) 0 else 1)
            }
            assertEquals(2, f.player.charges)
            assertEquals(0, f.player.inv.physicalCount(Chronicle.Card))
            assertEquals(1, f.player.inv.physicalCount(Chronicle.Book))
        }
    }

    @Test fun `every animated toy uses a native sequence matching both body types`() {
        val toys = DiangoToys.toys
        assertEquals(10, toys.size)
        for (toy in toys) {
            assertEquals("Play-with", if (toy.item.startsWith("obj.horsey") || toy.item.startsWith("obj.wwf"))
                checkNotNull(ServerCacheManager.getItem(toy.item.asRSCM())).interfaceOptions[0] else "Play-with")
            for (seq in listOfNotNull(toy.animation, toy.animationB, toy.finish, toy.finishB)) {
                assertTrue(checkNotNull(ServerCacheManager.getAnim(seq.asRSCM())).tickDuration > 0, seq)
            }
        }
        val plate = toys.single { it.item == "obj.spinning_plate" }
        assertNotEquals(plate.animation, plate.animationB)
        assertEquals("seq.stop_spinning_plate_f", plate.finishB)
        assertEquals("synth.PLATE_SPIN", plate.sound)
    }

    @Test fun `every equipable Diango shop item accepts its native second operation`() {
        val items = listOf("beachparty_boxinggloves_yellow", "beachparty_boxinggloves_purple",
            "goblinraid_warhammer", "goblinraid_bow", "goblinraid_staff", "wreath", "goblin_rpg",
            "summer2015_mask", "charity_banner", "chronicle", "skillcape_cabbage", "eoc_book",
            "midsum_helm16", "pride17_scarf", "midsummer_fan", "easter18_handegg_light",
            "easter18_handegg_balance", "easter18_handegg_chaos")
        for (name in items) {
            val f = Fixture()
            val symbol = "obj.$name"
            val type = checkNotNull(ServerCacheManager.getItem(symbol.asRSCM()))
            f.give(symbol)
            f.equip()
            assertEquals(type.id, f.player.worn[type.wearpos1]?.id, name)
            assertEquals(0, f.player.inv.physicalCount(symbol), name)
        }
    }

    @Test fun `Disk rejects use outside the mines without moving or taking an item`() {
        val f = Fixture(disk = true)
        f.give(DiskOfReturning.Disk)
        f.disk(blackout = false)
        assertNull(f.player.attr[DiskOfReturning.RoomKey])
        assertEquals(CoordGrid(3080, 3250), f.player.coords)
        assertEquals(1, f.player.inv.physicalCount(DiskOfReturning.Disk))
    }

    @Test fun `packed Disk access includes the western activation tile and original mine only underground`() {
        val f = Fixture()
        for (coord in listOf(DiskOfReturning.Exit, CoordGrid(3054, 9783), CoordGrid(3040, 9750))) {
            assertTrue(f.areas.inArea("area.diango_disk_mines", coord), coord.toString())
        }
        for (coord in listOf(CoordGrid(2984, 3411), CoordGrid(2984, 9811, 1), CoordGrid(3100, 9811), CoordGrid(2984, 9750))) {
            assertFalse(f.areas.inArea("area.diango_disk_mines", coord), coord.toString())
        }
    }

    @Test fun `Oculus is temporarily unavailable without opening the camera or consuming the orb`() {
        val f = Fixture(toys = true)
        f.give("obj.oculus")
        val destination = CoordGrid(3081, 3250)
        f.player.routeDestination.add(destination)
        val sequence = f.player.pendingSequence

        f.held1()

        assertFalse(f.player.ui.containsModal("interface.oculus"))
        assertFalse(f.client.messages.any { it.toString() == "CamMode(mode=1)" })
        assertTrue(f.client.messages.any { "The scrying orb is temporarily unavailable." in it.toString() })
        assertEquals(1, f.player.inv.physicalCount("obj.oculus"))
        assertEquals(destination, f.player.routeDestination.peekFirst())
        assertEquals(sequence, f.player.pendingSequence)
        f.events.publish(DialogInput(f.player, 1))
        assertEquals(0, f.player.vars["varp.diango_oculus_reset"])
    }

    @Test fun `an already open Oculus retains its controls and normal camera cleanup`() {
        val f = Fixture(toys = true)
        f.run { ifOpenMain("interface.oculus") }
        f.events.publish(DialogInput(f.player, 1))
        assertEquals(1, f.player.vars["varp.diango_oculus_reset"])
        f.events.publish(DialogInput(f.player, 0))
        assertEquals(0, f.player.vars["varp.diango_oculus_reset"])
        assertFalse(f.player.ui.containsModal("interface.oculus"))
        assertTrue(f.client.messages.any { it.toString() == "CamMode(mode=0)" })
        val renderSelf = RunClientScript("clientscript.[clientscript,set_renderself]".asRSCM(), listOf(1))
        assertTrue(f.client.messages.any { it.toString() == renderSelf.toString() })
        assertEquals("seq.qip_watchtower_stop_reading_scroll".asRSCM(), f.player.pendingSequence.id)
        f.events.publish(DialogInput(f.player, 1))
        assertEquals(0, f.player.vars["varp.diango_oculus_reset"])
    }

    @Test fun `scarf changes preserve the same inventory slot and quantity`() {
        val f = Fixture(toys = true)
        val initial = DiangoScarf.styles.first().first
        f.give(initial.internalName)
        f.start { assertTrue(f.events.publish(this,
            HeldObjEvents.Op3(0, checkNotNull(inv[0]), initial, inv))) }
        f.select(3)
        assertEquals(DiangoScarf.styles[3].first.id, f.player.inv[0]?.id)
        assertEquals(1, f.player.inv[0]?.count)
        assertEquals(1, f.player.inv.objs.count { it != null })
    }

    @Test fun `both Blackholes allocate private rooms after the full cast and recover on logout`() {
        for (blackout in listOf(false, true)) {
            val f = Fixture(disk = true)
            f.player.coords = DiskOfReturning.Exit
            f.give(DiskOfReturning.Disk)
            f.disk(blackout)
            repeat(2) {
                f.tick()
                assertEquals(DiskOfReturning.Exit, f.player.coords)
                assertNull(f.player.attr[DiskOfReturning.RoomKey])
            }
            f.tick()
            val room = checkNotNull(f.player.attr[DiskOfReturning.RoomKey])
            assertTrue(room.contains(f.player.coords))
            assertEquals(if (blackout) 0 else 2, f.player.coords.level)
            if (blackout) {
                assertEquals(room.region.normal[CoordGrid(1625, 4697)], f.player.coords)
                assertNull(room.npc)
            } else {
                assertEquals(f.player.coords.translate(-2, 0), checkNotNull(room.npc).coords)
            }
            assertEquals(DiskOfReturning.Exit, f.player.loginExitCoord)
            assertNotNull(BlackholeTeleportHook().validate(f.player, TeleportType.Chronicle, f.areas))
            f.objects.add("obj.coins", f.player.coords, 100, count = 42)
            val oldZone = ZoneKey.from(f.player.coords)
            assertTrue(f.objects.findAll(oldZone).any())
            f.events.publish(SessionStateEvent.Logout(f.player))
            assertNull(f.player.attr[DiskOfReturning.RoomKey])
            assertEquals(DiskOfReturning.Exit, f.player.loginExitCoord)
            assertFalse(f.objects.findAll(oldZone).any())
            assertEquals(0, f.npcList.count())
        }
    }

    @Test fun `cancelled Disk departure leaves the player and inventory outside`() {
        val f = Fixture(disk = true)
        f.player.coords = DiskOfReturning.Exit
        f.give(DiskOfReturning.Disk)
        f.disk(false)
        f.cancel()
        repeat(4) { f.tick() }
        assertEquals(DiskOfReturning.Exit, f.player.coords)
        assertNull(f.player.attr[DiskOfReturning.RoomKey])
        assertNull(f.player.loginExitCoord)
        assertEquals(1, f.player.inv.physicalCount(DiskOfReturning.Disk))
    }

    @Test fun `Disk room switch and normal exit survive queued menu closure and remove old room items`() {
        val f = Fixture(disk = true)
        f.enterBlackhole()
        val first = checkNotNull(f.player.attr[DiskOfReturning.RoomKey])
        f.objects.add("obj.coins", f.player.coords, 100, count = 42)
        val oldZone = ZoneKey.from(f.player.coords)
        f.disk(true)
        f.select(0)
        repeat(3) { f.tick() }
        val second = checkNotNull(f.player.attr[DiskOfReturning.RoomKey])
        assertNotEquals(first.region.uid, second.region.uid)
        assertEquals(0, f.player.coords.level)
        assertFalse(f.objects.findAll(oldZone).any())
        assertEquals(0, f.npcList.count())
        f.disk(false)
        f.select(0)
        repeat(3) { f.tick() }
        assertEquals(2, f.player.coords.level)
        f.disk(false)
        f.select(0)
        repeat(3) { f.tick() }
        assertEquals(DiskOfReturning.Exit, f.player.coords)
        assertNull(f.player.attr[DiskOfReturning.RoomKey])
        assertNull(f.player.loginExitCoord)
        assertEquals(0, f.npcList.count())
    }

    @Test fun `Light exit works after losing the Disk in the blacker Blackhole`() {
        val f = Fixture(disk = true)
        f.enterBlackhole(blackout = true)
        f.player.inv[0] = null
        f.lightExit()
        f.select(0)
        repeat(3) { f.tick() }
        assertEquals(DiskOfReturning.Exit, f.player.coords)
        assertNull(f.player.attr[DiskOfReturning.RoomKey])
        assertNull(f.player.loginExitCoord)
    }

    @Test fun `Thordur rescues a player who has lost the Disk`() {
        val f = Fixture(disk = true)
        f.enterBlackhole()
        f.player.inv[0] = null
        val npc = checkNotNull(f.player.attr[DiskOfReturning.RoomKey]?.npc)
        f.start { assertTrue(f.events.publish(this, NpcEvents.Op1(npc))) }
        f.dialogue()
        f.dialogue(3)
        f.dialogue()
        f.dialogue()
        repeat(3) { f.tick() }
        assertEquals(DiskOfReturning.Exit, f.player.coords)
        assertNull(f.player.attr[DiskOfReturning.RoomKey])
        assertNull(f.player.loginExitCoord)
        assertEquals(0, f.npcList.count())
    }

    @Test fun `Disk drop keeps a declined item and drops one item before its native dance`() {
        val f = Fixture(disk = true)
        f.enterBlackhole()
        f.dropDisk()
        f.select(1)
        assertEquals(1, f.player.inv.physicalCount(DiskOfReturning.Disk))
        assertFalse(f.objects.findAll(ZoneKey.from(f.player.coords)).any())
        f.continueMessage()
        f.dropDisk()
        f.select(0)
        assertEquals(0, f.player.inv.physicalCount(DiskOfReturning.Disk))
        assertEquals(1, f.objects.findAll(ZoneKey.from(f.player.coords)).count())
        assertEquals("seq.partyroom_dance".asRSCM(), f.player.pendingSequence.id)
        f.continueMessage()
        assertEquals(EntitySeq.ZERO, f.player.pendingSequence)
    }

    private class Fixture(denial: String? = null, disk: Boolean = false, toys: Boolean = false) {
        val client = SilentClient()
        val player = Player().apply {
            coords = CoordGrid(3080, 3250)
            currentMapClock = 100
            processedMapClock = 100
            inv = Inventory.create("inv.inv")
            worn = Inventory.create("inv.worn")
            client = this@Fixture.client
            uuid = 1
            observerUUID = 1
        }
        val events = EventBus()
        private val collision = CollisionFlagMap().apply {
            for (coords in listOf(player.coords, Chronicle.Destination, DiskOfReturning.Exit)) {
                allocateIfAbsent(coords.x, coords.z, coords.level)
            }
            for (level in 0..2) for (x in 1600..1663 step 8) for (z in 4672..4735 step 8) {
                allocateIfAbsent(x, z, level)
            }
        }
        private val storage = LocZoneStorage()
        private val clock = MapClock(100)
        private val updates = ZoneUpdateMap()
        val npcList = NpcList()
        private val npcRegistry = NpcRegistry(npcList, collision, events)
        val objects = ObjRepository(clock, ObjRegistry(updates))
        private val marketPrices = object : MarketPrices {
            override fun get(type: dev.openrune.types.ItemServerType): Int? = null
        }
        private val held by lazy {
            val dropClass = Class.forName("org.rsmod.api.player.interact.HeldDropOp")
            val drop = dropClass.declaredConstructors.single().apply { isAccessible = true }
                .newInstance(events, objects, marketPrices, GroundItemDropResolver(emptySet()))
            HeldInteractions::class.java.declaredConstructors.single().apply { isAccessible = true }
                .newInstance(events, marketPrices, drop, HeldEquipOp(events, PlayerRestrictions(emptySet()))) as HeldInteractions
        }
        private val regions = RegionRegistry(RegionListSmall(), RegionListLarge(),
            LocRegistryNormal(updates, collision, storage), collision, storage,
            npcRegistry,
            ControllerRegistry(MapClock(100), ControllerList()), ZonePlayerActivityBitSet())
        val areas = AreaChecker(regions, AreaIndex().apply {
            for ((square, area) in mineAreas) MapAreaDefinitions.putAreas(this, square, area)
        })
        var validated: TeleportType? = null
        private val validator = PlayerTeleportValidator(setOf(PlayerTeleportValidateHook { _, type, _ ->
            validated = type
            denial
        }))
        private val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { events }, getAreaChecker = { areas }, getTeleportValidator = { validator },
            getCollision = { collision },
            getAlignment = { TextAlignment() }, getHeldInteractions = { held },
            getRandom = { object : GameRandom {
                override fun of(maxExclusive: Int) = 0
                override fun of(minInclusive: Int, maxInclusive: Int) = minInclusive
                override fun randomDouble() = 0.0
            } },
        )
        private val queues = PlayerQueueProcessor::class.java.getDeclaredConstructor(
            EventBus::class.java, kotlin.jvm.functions.Function2::class.java,
        ).newInstance(events, { target: Player, event: org.rsmod.api.player.events.PlayerQueueEvents.Protected<*> ->
            ProtectedAccessLauncher.withProtectedAccess(target, context) { events.publish(this, event) }
            Unit
        })

        init {
            val scripts = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(Chronicle(validator, areas)) { scripts.startup() }
            if (disk) with(DiskOfReturning(RegionRepository(regions),
                NpcRepository(clock, npcRegistry, npcList), objects, areas, validator)) { scripts.startup() }
            if (toys) {
                with(DiangoToys(areas)) { scripts.startup() }
                with(DiangoOculus(events)) { scripts.startup() }
                with(DiangoScarf()) { scripts.startup() }
            }
        }
        private var completion: Result<Unit>? = null
        private var coroutine: GameCoroutine? = null

        fun give(item: String, count: Int = 1) {
            val type = checkNotNull(ServerCacheManager.getItem(item.asRSCM()))
            repeat(if (type.isStackable) 1 else count) {
                player.inv[player.inv.indices.first { player.inv[it] == null }] =
                    InvObj(type, if (type.isStackable) count else 1)
            }
        }

        fun teleport() {
            val slot = player.inv.indices.first { player.inv[it]?.id == Chronicle.Book.asRSCM() }
            val obj = checkNotNull(player.inv[slot])
            val type = checkNotNull(ServerCacheManager.getItem(obj.id))
            invoke(HeldObjEvents.Op3(slot, obj, type, player.inv))
        }

        fun invoke(event: SuspendEvent<ProtectedAccess>) {
            run { assertTrue(events.publish(this, event)) }
        }

        fun processQueue() { queues.process(player) }
        fun tick() {
            clock.tick()
            player.currentMapClock = clock.cycle
            player.processedMapClock = player.currentMapClock
            coroutine?.advance()
            completion?.getOrThrow()
            queues.process(player)
        }

        fun disk(blackout: Boolean) {
            val obj = checkNotNull(player.inv[0])
            val type = checkNotNull(ServerCacheManager.getItem(obj.id))
            start { assertTrue(events.publish(this,
                if (blackout) HeldObjEvents.Op3(0, obj, type, inv)
                else HeldObjEvents.Op1(0, obj, type, inv))) }
        }

        fun held1() {
            val obj = checkNotNull(player.inv[0])
            invoke(HeldObjEvents.Op1(0, obj, checkNotNull(ServerCacheManager.getItem(obj.id)), player.inv))
        }

        fun equip() { run { held.interact(this, inv, 0, HeldOp.Op2) } }

        @OptIn(org.rsmod.annotations.InternalApi::class)
        fun select(option: Int) {
            val chat = player.ui.containsModal("interface.chatmenu")
            player.ui.queueClose(Component((if (chat) "component.chatbox:chatmodal"
                else "component.toplevel_osrs_stretch:mainmodal").asRSCM()))
            checkNotNull(coroutine).resumeWith(ResumePauseButtonInput(
                if (chat) "component.chatmenu:options" else "component.menu:lj_layer1",
                if (chat) option + 1 else option))
            for (packed in player.ui.closeQueue.intIterator()) player.closeSubs(Component(packed), events)
            player.ui.closeQueue.clear()
            completion?.getOrThrow()
        }

        fun enterBlackhole(blackout: Boolean = false) {
            player.coords = DiskOfReturning.Exit
            give(DiskOfReturning.Disk)
            disk(blackout)
            repeat(3) { tick() }
            assertNotNull(player.attr[DiskOfReturning.RoomKey])
        }

        fun dropDisk() {
            val item = checkNotNull(player.inv[0])
            val type = checkNotNull(ServerCacheManager.getItem(item.id))
            start { assertTrue(events.publish(this, HeldObjEvents.Op5(0, item, type, inv))) }
        }

        fun continueMessage() {
            checkNotNull(coroutine).resumeWith(ResumePauseButtonInput("component.messagebox:continue", -1))
            completion?.getOrThrow()
        }

        fun dialogue(option: Int = -1) {
            val component = when {
                player.ui.containsModal("interface.chatmenu") -> "component.chatmenu:options"
                player.ui.containsModal("interface.chat_left") -> "component.chat_left:continue"
                player.ui.containsModal("interface.chat_right") -> "component.chat_right:continue"
                else -> error("Unexpected dialogue")
            }
            checkNotNull(coroutine).resumeWith(ResumePauseButtonInput(component, option))
            completion?.getOrThrow()
        }

        fun lightExit() {
            val room = checkNotNull(player.attr[DiskOfReturning.RoomKey])
            val type = checkNotNull(ServerCacheManager.getObject("loc.blackhole_exit".asRSCM()))
            val loc = BoundLocInfo(LocInfo(2, room.region.normal[CoordGrid(1624, 4696)],
                LocEntity(type.id, 10, 0)), type)
            start { assertTrue(events.publish(this, LocEvents.Op1(loc, loc, type))) }
        }

        fun cancel() { coroutine?.cancel(); completion = null; coroutine = null }

        fun run(block: suspend ProtectedAccess.() -> Unit) {
            start(block)
            checkNotNull(completion).getOrThrow()
        }

        fun start(block: suspend ProtectedAccess.() -> Unit) {
            val coroutine = GameCoroutine()
            this.coroutine = coroutine
            player.activeCoroutine = coroutine
            val access = ProtectedAccess(player, coroutine, context)
            completion = null
            val action: suspend () -> Unit = { access.block() }
            action.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { completion = result }
            })
            completion?.getOrThrow()
        }
    }

    private class SilentClient : Client<Any, Any> {
        val messages = mutableListOf<Any>()
        override fun write(message: Any) { messages += message }
        override fun close() {}
        override fun read(player: Player) {}
        override fun flush() {}
        override fun flushHighPriority() {}
        override fun unregister(service: Any, player: Player) {}
    }

    companion object {
        private lateinit var mineAreas: Map<MapSquareKey, MapAreaDefinition>
        private val restore = mutableListOf<() -> Unit>()

        @JvmStatic @BeforeAll fun cache() {
            val cache = ServerCacheManager.init(241)
            try {
                mineAreas = (listOf(MapSquareKey(46, 153)) + (151..153).map { MapSquareKey(47, it) })
                    .associateWith { square -> MapAreaDecoder.decode(InlineByteBuf(
                        checkNotNull(cache.data(5, (square.x shl 8) or square.z, 7)))) }
            } finally { cache.close() }
            for ((owner, name) in listOf(
                "org.rsmod.api.invtx.InvTransactionsScriptKt" to "cachedInventoryTransactions",
                "org.rsmod.api.invtx.VirtualInvTransactionsKt" to "cachedPlayerItemStorage",
            )) {
                val field = Class.forName(owner).getDeclaredField(name).apply { isAccessible = true }
                val previous = field.get(null)
                restore += { field.set(null, previous) }
            }
            val previous = InvVirtualStorageHolder.instance
            restore += { InvVirtualStorageHolder.instance = previous }
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
                ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        @JvmStatic @AfterAll fun cleanup() { restore.asReversed().forEach { it() } }
    }
}
