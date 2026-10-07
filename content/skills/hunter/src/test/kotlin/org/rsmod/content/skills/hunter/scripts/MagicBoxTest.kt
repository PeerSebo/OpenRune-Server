@file:OptIn(org.rsmod.annotations.InternalApi::class)

package org.rsmod.content.skills.hunter.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.combat.weapon.WeaponSpeeds
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactions
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.LocRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.loc.LocRegistryRegion
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.player.PlayerRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.player.PlayerRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.interfaces.bank.scripts.BankInvScript
import org.rsmod.content.skills.hunter.HunterTrapManager
import org.rsmod.content.skills.hunter.MagicBoxService
import org.rsmod.content.skills.hunter.configs.HunterParams
import org.rsmod.content.skills.hunter.scripts.HuntsmansKitScript.Companion.kit
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.area.AreaIndex
import org.rsmod.game.entity.*
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MagicBoxTest {
    private val restore = mutableListOf<() -> Unit>()

    @BeforeAll fun cache() { ServerCacheManager.init(241).close() }

    @BeforeEach fun inventoryServices() {
        replaceStatic("org.rsmod.api.invtx.InvTransactionsScriptKt", "cachedInventoryTransactions", InvTransactions.from())
        replaceStatic("org.rsmod.api.invtx.VirtualInvTransactionsKt", "cachedPlayerItemStorage", PlayerItemStorage(emptySet()))
    }

    @AfterEach fun restoreServices() { restore.asReversed().forEach { it() }; restore.clear() }

    @Test fun `native kit listeners and permanent thirty two type capacity are present`() {
        assertEquals(3625, ServerCacheManager.getInterface(478)?.components?.get(0)?.onLoad?.first())
        assertEquals(4988, ServerCacheManager.getInterface(871)?.components?.get(0)?.onLoad?.first())
        assertEquals(5000, ServerCacheManager.getInterface(872)?.components?.get(0)?.onLoad?.first())
        val inventory = checkNotNull(ServerCacheManager.getInventory(855))
        assertEquals(32, inventory.size)
        assertEquals(dev.openrune.types.InvStackType.Always, inventory.stack)
        assertEquals(dev.openrune.types.InvScope.Perm, inventory.scope)
    }

    @Test fun `interrupted laying refunds its reservation without creating or consuming any boxes`() {
        val f = Fixture()
        val trap = checkNotNull(f.boxes.begin(f.player))
        assertEquals(1, f.manager.ownedTrapCount(f.player))
        assertFalse(f.manager.reserveExternalTrap(f.player.coords))
        f.player.coords = f.player.coords.translate(1, 0)
        assertFalse(f.boxes.finish(trap))
        f.boxes.cancel(trap)
        assertEquals(1, f.player.inv.count(MagicBoxService.EMPTY))
        assertEquals(0, f.manager.ownedTrapCount(f.player))
        assertFalse(f.manager.isTrapTileReserved(trap.coords))
        assertNull(f.locs.findExact(trap.coords, LocShape.CentrepieceStraight))
    }

    @Test fun `magic boxes require level seventy one and cannot activate in Puro Puro`() {
        val f = Fixture()
        f.player.statMap.setCurrentLevel("stat.hunter", 70)
        assertNull(f.boxes.begin(f.player))
        f.player.statMap.setCurrentLevel("stat.hunter", 99)
        f.player.coords = CoordGrid(2590, 4310)
        assertNull(f.boxes.begin(f.player))
        assertEquals(1, f.player.inv.count(MagicBoxService.EMPTY))
    }

    @Test fun `standing on trap prevents catch then retrieval pays exactly one box and 450 XP`() {
        val f = Fixture()
        val trap = f.lay()
        val imp = f.imp(trap.coords.translate(1, 0))
        f.advance(HunterParams.ATTEMPT_INTERVAL_TICKS)
        f.boxes.tick()
        assertEquals(MagicBoxService.State.ARMED, trap.state)
        assertFalse(imp.hidden)
        f.move(trap.coords.translate(-1, 0))
        f.advance(HunterParams.ATTEMPT_INTERVAL_TICKS)
        f.boxes.tick()
        assertEquals(MagicBoxService.State.TRAPPING, trap.state)
        assertTrue(imp.hidden)
        assertEquals(0, f.player.statMap.getFineXP("stat.hunter"))
        f.advance(HunterParams.TRANSITION_TICKS)
        f.boxes.tick()
        val loc = f.bound(trap)
        assertTrue(f.boxes.retrieve(f.player, loc))
        assertFalse(f.boxes.retrieve(f.player, loc))
        assertEquals(1, f.player.inv.count(MagicBoxService.FULL))
        assertEquals(4500, f.player.statMap.getFineXP("stat.hunter"))
        assertEquals(0, f.manager.ownedTrapCount(f.player))
    }

    @Test fun `failed imp remains in world and only owner can deactivate trap`() {
        val f = Fixture()
        f.random.roll = .999
        val trap = f.lay()
        val imp = f.imp(trap.coords.translate(1, 0))
        f.move(trap.coords.translate(-1, 0))
        f.advance(HunterParams.ATTEMPT_INTERVAL_TICKS)
        f.boxes.tick()
        assertEquals(MagicBoxService.State.FAILED, trap.state)
        assertFalse(imp.hidden)
        val stranger = Player().apply { coords = f.player.coords }
        assertFalse(f.boxes.retrieve(stranger, f.bound(trap)))
        assertTrue(f.boxes.retrieve(f.player, f.bound(trap)))
        assertEquals(1, f.player.inv.count(MagicBoxService.EMPTY))
        assertEquals(0, f.player.statMap.getFineXP("stat.hunter"))
    }

    @Test fun `bead is consumed once and expiry drops one recoverable box`() {
        val f = Fixture()
        val trap = f.lay()
        f.player.inv[1] = f.item("obj.red_bead", 2)
        assertTrue(f.boxes.bait(f.player, f.bound(trap), "obj.red_bead"))
        assertFalse(f.boxes.bait(f.player, f.bound(trap), "obj.red_bead"))
        assertEquals(1, f.player.inv.count("obj.red_bead"))
        f.advance(HunterParams.SET_LIFETIME_TICKS)
        f.boxes.tick()
        f.boxes.cleanup(f.player)
        assertEquals(1, f.objRepo.findAll(trap.coords).sumOf { it.count })
        assertEquals(0, f.manager.ownedTrapCount(f.player))
        assertNull(f.locs.findExact(trap.coords, LocShape.CentrepieceStraight))
    }

    @Test fun `banking full stacks uses two charges and a replay of the old box does nothing`() {
        val f = Fixture()
        f.player.inv[0] = f.item(MagicBoxService.FULL)
        f.player.inv[1] = f.item("obj.coins", 12345)
        f.player.inv[2] = f.item("obj.logs")
        val full = MagicBoxService.FULL.asRSCM()
        assertTrue(f.deposit(0, full, 1))
        assertEquals(12345, f.access.bank.count("obj.coins"))
        assertEquals(1, f.player.inv.count(MagicBoxService.HALF))
        assertFalse(f.deposit(0, full, 2))
        assertEquals(1, f.player.inv.count("obj.logs"))
        assertTrue(f.deposit(0, MagicBoxService.HALF.asRSCM(), 2))
        assertEquals(1, f.access.bank.count("obj.logs"))
        assertEquals(1, f.player.inv.count(MagicBoxService.EMPTY))
    }

    @Test fun `a full bank leaves the selected stack and both imp trips intact`() {
        val f = Fixture()
        VarPlayerIntMapSetter.set(f.player, "varbit.bank_capacity", 1)
        f.access.bank[0] = f.item("obj.logs")
        VarPlayerIntMapSetter.set(f.player, "varbit.bank_tab_main", 1)
        f.player.inv[0] = f.item(MagicBoxService.FULL)
        f.player.inv[1] = f.item("obj.coins", 100)
        assertFalse(f.deposit(0, MagicBoxService.FULL.asRSCM(), 1))
        assertEquals(100, f.player.inv.count("obj.coins"))
        assertEquals(1, f.player.inv.count(MagicBoxService.FULL))
    }

    @Test fun `bags self banking teleblock raids and PvP worlds cannot spend a trip`() {
        val f = Fixture()
        f.player.inv[0] = f.item(MagicBoxService.FULL)
        f.player.inv[1] = f.item("obj.looting_bag")
        assertFalse(f.deposit(0, MagicBoxService.FULL.asRSCM(), 0))
        assertFalse(f.deposit(0, MagicBoxService.FULL.asRSCM(), 1))
        f.player.inv[1] = f.item("obj.coins", 5)
        for (key in listOf("varbit.teleblock_cycles", "varbit.this_is_a_pvp_world", "varbit.raids_client_indungeon", "varbit.tob_client_partystatus")) {
            VarPlayerIntMapSetter.set(f.player, key, if (key == "varbit.tob_client_partystatus") 2 else 1)
            assertFalse(f.deposit(0, MagicBoxService.FULL.asRSCM(), 1), key)
            VarPlayerIntMapSetter.set(f.player, key, 0)
        }
        assertEquals(5, f.player.inv.count("obj.coins"))
        assertEquals(1, f.player.inv.count(MagicBoxService.FULL))
    }

    @Test fun `kit stacks charged boxes but excludes notes birdhouses and unrelated tools`() {
        val f = Fixture()
        val kit = HuntsmansKitScript()
        f.player.inv[0] = f.item(HuntsmansKitScript.KIT)
        f.player.inv[1] = f.item(MagicBoxService.FULL)
        f.player.inv[2] = f.item(MagicBoxService.FULL)
        f.player.inv[3] = f.item("obj.knife")
        f.player.inv[4] = f.item("obj.cert_magic_imp_box", 10)
        with(kit) { f.access.fill() }
        assertEquals(2, f.player.kit.count(MagicBoxService.FULL))
        assertEquals(1, f.player.kit.occupiedSpace())
        assertEquals(1, f.player.inv.count("obj.knife"))
        assertEquals(10, f.player.inv.count("obj.cert_magic_imp_box"))
        assertEquals(0, f.player.inv.count(MagicBoxService.FULL), "Storage does not make charged boxes usable while inside")
    }

    @Test fun `full kit accepts existing types while rejecting a thirty third type`() {
        val f = Fixture()
        val script = HuntsmansKitScript()
        f.player.inv[0] = f.item(HuntsmansKitScript.KIT)
        val types = HuntsmansKitScript.STORABLE.toList()
        assertEquals(32, f.player.kit.size)
        for (slot in 0 until 32) f.player.kit[slot] = f.item(types[slot])
        f.player.inv[1] = f.item(types[32])
        f.player.inv[2] = f.item(types[0])
        with(script) {
            assertFalse(f.access.deposit(1, 1))
            assertTrue(f.access.deposit(2, 1))
        }
        assertEquals(32, f.player.kit.occupiedSpace())
        assertEquals(2, f.player.kit.count(types[0]))
        assertEquals(1, f.player.inv.count(types[32]))
    }

    @Test fun `kit empty starts at last slot and partial withdrawal leaves the remainder safe`() {
        val f = Fixture()
        val script = HuntsmansKitScript()
        f.player.inv[0] = f.item(HuntsmansKitScript.KIT)
        for (slot in 1..26) f.player.inv[slot] = f.item("obj.logs")
        f.player.kit[0] = f.item("obj.noose_wand")
        f.player.kit[31] = f.item(MagicBoxService.FULL, 5)
        with(script) { f.access.empty(); f.access.empty() }
        assertEquals(1, f.player.inv.count(MagicBoxService.FULL))
        assertEquals(4, f.player.kit.count(MagicBoxService.FULL))
        assertEquals(1, f.player.kit.count("obj.noose_wand"))
        assertEquals(28, f.player.inv.occupiedSpace())
    }

    @Test fun `stored kit items remain inaccessible after kit leaves inventory and return on reclaim`() {
        val f = Fixture()
        val script = HuntsmansKitScript()
        f.player.kit[0] = f.item("obj.gryphon_feather", 100)
        with(script) { assertFalse(f.access.withdraw(0, 100)) }
        assertEquals(100, f.player.kit.count("obj.gryphon_feather"))
        f.player.inv[0] = f.item(HuntsmansKitScript.KIT)
        with(script) { assertTrue(f.access.withdraw(0, 100)) }
        assertEquals(100, f.player.inv.count("obj.gryphon_feather"))
        assertTrue(f.player.kit.isEmpty())
    }

    private class Fixture {
        val clock = MapClock(100)
        val events = EventBus()
        val updates = ZoneUpdateMap()
        val storage = LocZoneStorage()
        val collision = CollisionFlagMap().apply { for (x in 3192..3216 step 8) for (z in 3192..3216 step 8) allocateIfAbsent(x, z, 0) }
        val random = RollRandom()
        val player = Player().apply {
            uuid = 1L; observerUUID = 1L; slotId = 1; assignUid()
            coords = CoordGrid(3200, 3200)
            currentMapClock = 100; processedMapClock = 100
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
            statMap.setBaseLevel("stat.hunter", 99); statMap.setCurrentLevel("stat.hunter", 99)
            statMap.setBaseLevel("stat.hitpoints", 10); statMap.setCurrentLevel("stat.hitpoints", 10)
            VarPlayerIntMapSetter.set(this, "varbit.bank_capacity", 800)
        }
        val players = PlayerList().apply { this[1] = player }
        val npcs = NpcList()
        val npcRegistry = NpcRegistry(npcs, collision, events)
        val normal = LocRegistryNormal(updates, collision, storage)
        val regions = RegionRegistry(RegionListSmall(), RegionListLarge(), normal, collision, storage, npcRegistry, ControllerRegistry(clock, ControllerList()), ZonePlayerActivityBitSet())
        val areas = AreaChecker(regions, AreaIndex())
        val locs = LocRepository(clock, LocRegistry(storage, normal, LocRegistryRegion(updates, collision, storage, regions)), regions)
        val objRepo = ObjRepository(clock, ObjRegistry(updates))
        val world = WorldRepository(updates)
        val npcRepo = NpcRepository(clock, npcRegistry, npcs)
        val playerRegistry = PlayerRegistry(players, collision, ZonePlayerActivityBitSet(), events).apply { change(player, org.rsmod.map.zone.ZoneKey.NULL, org.rsmod.map.zone.ZoneKey.from(player.coords)) }
        val playerRepo = PlayerRepository(playerRegistry)
        val manager = HunterTrapManager(locs, npcRepo, objRepo, playerRepo, world, players, clock, random, areas, org.rsmod.api.route.RayCastValidator(collision))
        val boxes = MagicBoxService(manager, clock, locs, npcRepo, objRepo, playerRepo, collision, random, XpModifiers(emptySet()), events)
        val script = MagicBoxScript(boxes, BankInvScript(events, WornBonuses(), WeaponSpeeds(AttackStyles())), areas)
        val access = ProtectedAccess(player, GameCoroutine(), ProtectedAccessContext(
            getRandom = { random }, getEventBus = { events }, getNpcList = { npcs }, getPlayerList = { players },
            getCollision = { collision }, getAreaChecker = { areas }, getAlignment = { error("unused") },
            getLocInteractions = { error("unused") }, getNpcInteractions = { error("unused") }, getPlayerInteractions = { error("unused") },
            getHeldInteractions = { error("unused") }, getWornInteractions = { error("unused") }, getMusicPlayer = { error("unused") },
            getMarketPrices = { error("unused") }, getInstantHitProcessor = { error("unused") }, getTeleportValidator = { error("unused") },
            getWorldRepo = { world }, getHitModifier = { error("unused") },
        ))
        init { player.inv[0] = item(MagicBoxService.EMPTY) }
        fun item(symbol: String, count: Int = 1) = InvObj(checkNotNull(ServerCacheManager.getItem(symbol.asRSCM())), count)
        fun lay() = checkNotNull(boxes.begin(player)).also { assertTrue(boxes.finish(it)) }
        fun bound(trap: MagicBoxService.Trap) = BoundLocInfo(trap.coords, LocEntity(checkNotNull(trap.loc).id, 10, 0), 1, 1, 0, 0)
        fun imp(coords: CoordGrid) = Npc("npc.imp", coords).apply { hitpoints = 10; npcRepo.add(this, Int.MAX_VALUE) }
        fun move(coords: CoordGrid) {
            val from = org.rsmod.map.zone.ZoneKey.from(player.coords)
            player.coords = coords
            playerRegistry.change(player, from, org.rsmod.map.zone.ZoneKey.from(coords))
        }
        fun advance(cycles: Int) { clock.cycle += cycles; player.currentMapClock = clock.cycle; player.processedMapClock = clock.cycle }
        fun deposit(box: Int, expectedBox: Int, slot: Int) = with(script) { access.deposit(box, expectedBox, slot, checkNotNull(player.inv[slot]).id) }
    }
    private class RollRandom : GameRandom {
        var roll = 0.0
        override fun of(maxExclusive: Int) = 0
        override fun of(minInclusive: Int, maxInclusive: Int) = minInclusive
        override fun randomDouble() = roll
    }
    private fun replaceStatic(owner: String, name: String, value: Any) {
        val field = Class.forName(owner).getDeclaredField(name).apply { isAccessible = true }
        val previous = field.get(null)
        restore += { field.set(null, previous) }; field.set(null, value)
    }
}
