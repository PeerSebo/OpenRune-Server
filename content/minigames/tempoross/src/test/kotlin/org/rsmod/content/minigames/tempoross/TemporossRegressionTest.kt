package org.rsmod.content.minigames.tempoross

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
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.region.InstanceAreaResolver
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactions
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.route.BoundValidator
import org.rsmod.content.minigames.tempoross.configs.TemporossConstants as C
import org.rsmod.content.minigames.tempoross.scripts.TemporossArenaScript
import org.rsmod.content.minigames.tempoross.scripts.TemporossLobbyScript
import org.rsmod.content.skills.fishing.configs.FishingEquipment
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.area.AreaIndex
import org.rsmod.game.entity.ControllerList
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.routefinder.collision.CollisionFlagMap

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TemporossRegressionTest {
    @BeforeAll fun loadCache() { ServerCacheManager.init(241).close() }
    private val restore = mutableListOf<() -> Unit>()

    @BeforeEach fun installInventoryServices() {
        replaceStatic("org.rsmod.api.invtx.InvTransactionsScriptKt", "cachedInventoryTransactions", InvTransactions.from())
        replaceStatic("org.rsmod.api.invtx.VirtualInvTransactionsKt", "cachedPlayerItemStorage", PlayerItemStorage(emptySet()))
    }

    @AfterEach fun restoreInventoryServices() { restore.asReversed().forEach { it() }; restore.clear() }
    private fun replaceStatic(owner: String, name: String, value: Any) {
        val field = Class.forName(owner).getDeclaredField(name).apply { isAccessible = true }
        val previous = field.get(null)
        restore += { field.set(null, previous) }
        field.set(null, value)
    }

    @Test fun `repeated solo start produces exactly one arena`() {
        val f = Fixture()
        f.manager.startSolo(f.player, 100)
        f.manager.startSolo(f.player, 101)
        assertTrue(f.manager.isCastingOff(f.player))
        f.manager.tick(107)
        f.manager.tick(108)
        assertEquals(1, f.instances.serverOwnedSessions(C.INSTANCE_KEY).size)
        assertNotNull(f.manager.gameForPlayer(f.player))
        assertFalse(f.manager.isCastingOff(f.player))
        assertFalse("interface.fade_overlay" in f.player.ui)
    }

    @Test fun `abandoned departure closes fade without pulling player back`() {
        val f = Fixture()
        f.manager.startSolo(f.player, 100)
        f.player.coords = C.RETURN_COORD
        f.manager.tick(107)
        assertEquals(C.RETURN_COORD, f.player.coords)
        assertFalse("interface.fade_overlay" in f.player.ui)
        assertTrue(f.instances.serverOwnedSessions(C.INSTANCE_KEY).isEmpty())
    }

    @Test fun `reconnecting UUID cannot inherit old departure`() {
        val f = Fixture()
        f.manager.startSolo(f.player, 100)
        val replacement = Player().apply { uuid = f.player.uuid; slotId = 1; coords = C.PIER_COORD }
        f.players[1] = replacement
        f.manager.tick(107)
        assertEquals(C.PIER_COORD, replacement.coords)
        assertTrue(f.instances.serverOwnedSessions(C.INSTANCE_KEY).isEmpty())
        assertFalse("interface.fade_overlay" in f.player.ui)
    }

    @Test fun `departing player cannot join another deck lobby`() {
        val f = Fixture()
        f.manager.startSolo(f.player, 100)
        f.manager.boardDeck(f.player, 101)
        assertFalse(f.manager.isOnDeck(f.player))
    }

    @Test fun `fresh round deletes leftover encounter fish but preserves tools`() {
        val f = Fixture()
        f.item(0, C.RAW_HARPOONFISH)
        f.item(1, C.COOKED_HARPOONFISH)
        f.item(2, C.HARPOON)
        f.start()
        assertNull(f.player.inv[0])
        assertNull(f.player.inv[1])
        assertEquals(C.HARPOON.asRSCM(), f.player.inv[2]?.id)
    }

    @Test fun `full inventory cannot consume a reward permit or grant XP`() {
        for (coins in listOf(false, true)) {
            val f = Fixture(randomValue = if (coins) 0 else 1)
            f.fillInventory()
            f.permits(5)
            f.collect(5)
            assertEquals(5, f.player.vars[C.VARBIT_PERMITS])
            assertEquals(0, f.player.statMap.getXP("stat.fishing"))
            assertEquals(28, f.player.inv.count { it != null })
        }
    }

    @Test fun `big search commits only rewards that fit and retains remaining permits`() {
        val f = Fixture()
        f.fillInventory()
        f.player.inv[27] = null
        f.permits(5)
        f.collect(5)
        assertEquals(4, f.player.vars[C.VARBIT_PERMITS])
        assertEquals(10, f.player.statMap.getXP("stat.fishing"))
        assertEquals("obj.dragon_harpoon".asRSCM(), f.player.inv[27]?.id)
    }

    @Test fun `existing bait stack can receive a reward in a full inventory`() {
        val f = Fixture(randomValue = 999)
        f.fillInventory()
        f.item(0, "obj.fishing_bait", 10)
        f.permits(1)
        f.collect(1)
        assertEquals(0, f.player.vars[C.VARBIT_PERMITS])
        assertEquals(810, f.player.inv[0]?.count)
        assertEquals(10, f.player.statMap.getXP("stat.fishing"))
    }

    @Test fun `zero permits never generate rewards`() {
        val f = Fixture()
        f.collect(5)
        assertTrue(f.player.inv.isEmpty())
        assertEquals(0, f.player.statMap.getXP("stat.fishing"))
    }

    @Test fun `cooking exchanges fish atomically even in a full inventory and awards cooking XP`() {
        val f = Fixture()
        val game = f.start()
        f.fillInventory()
        f.item(0, C.RAW_HARPOONFISH)
        f.due()
        f.cook()
        assertEquals(C.COOKED_HARPOONFISH.asRSCM(), f.player.inv[0]?.id)
        assertEquals(10, f.player.statMap.getXP("stat.cooking"))
        assertEquals(10, game.pointsFor(1L))
        assertEquals(0, f.player.statMap.getXP("stat.fishing"))
    }

    @Test fun `cooking after encounter victory cannot farm points or XP`() {
        val f = Fixture()
        val game = f.start()
        game.phase = TemporossPhase.WON
        f.item(0, C.RAW_HARPOONFISH)
        f.due()
        f.cook()
        assertEquals(C.RAW_HARPOONFISH.asRSCM(), f.player.inv[0]?.id)
        assertEquals(0, game.pointsFor(1L))
        assertEquals(0, f.player.statMap.getXP("stat.cooking"))
    }

    @Test fun `refused forfeit preserves membership and encounter inventory`() {
        val f = Fixture()
        val game = f.start()
        f.item(0, C.RAW_HARPOONFISH)
        val before = f.player.coords
        f.teleportDenied = true
        f.forfeit()
        assertSame(game, f.manager.gameForPlayer(f.player))
        assertEquals(before, f.player.coords)
        assertEquals(C.RAW_HARPOONFISH.asRSCM(), f.player.inv[0]?.id)
    }

    @Test fun `successful forfeit reaches shore and removes encounter fish`() {
        val f = Fixture()
        f.start()
        f.item(0, C.RAW_HARPOONFISH)
        f.item(1, C.HARPOON)
        f.forfeit()
        assertNull(f.manager.gameForPlayer(f.player))
        assertEquals(C.RETURN_COORD, f.player.coords)
        assertNull(f.player.inv[0])
        assertEquals(C.HARPOON.asRSCM(), f.player.inv[1]?.id)
    }

    @Test fun `points are capped and stale encounters cannot award points`() {
        val f = Fixture()
        val game = f.start()
        f.manager.awardPoints(f.player, game, 31_999)
        f.manager.awardPoints(f.player, game, Int.MAX_VALUE)
        assertEquals(32_000, game.pointsFor(1L))
        assertEquals(32_000, f.player.vars[C.VARBIT_POINTS])
        val stale = TemporossEncounter(game.instanceId)
        f.manager.awardPoints(f.player, stale, 100)
        assertEquals(0, stale.pointsFor(1L))
    }

    @Test fun `energy and essence can only change in their corresponding phases`() {
        val f = Fixture()
        val game = f.start()
        f.manager.drainEssence(game, 12)
        assertEquals(game.essenceMax, game.essence)
        game.phase = TemporossPhase.VULNERABLE
        f.manager.depositEnergy(game, 10)
        assertEquals(game.energyMax, game.energy)
        f.manager.drainEssence(game, -12)
        assertEquals(game.essenceMax, game.essence)
    }

    @Test fun `worn dragon harpoon is usable and respects Fishing requirement`() {
        val f = Fixture()
        f.player.worn[3] = InvObj(checkNotNull(ServerCacheManager.getItem("obj.dragon_harpoon".asRSCM())))
        assertNotNull(FishingEquipment.findHarpoon(f.player))
        f.player.statMap.setCurrentLevel("stat.fishing", 35)
        assertNull(FishingEquipment.findHarpoon(f.player))
        f.item(0, C.HARPOON)
        assertEquals(C.HARPOON.asRSCM(), FishingEquipment.findHarpoon(f.player)?.id)
    }

    @Test fun `victory awards threshold permits once and respects storage limit`() {
        val f = Fixture()
        val game = f.start()
        f.manager.awardPoints(f.player, game, 2_700)
        f.permits(7_999)
        game.phase = TemporossPhase.VULNERABLE
        game.essence = 0
        f.manager.tick(108)
        assertEquals(TemporossPhase.WON, game.phase)
        assertEquals(8_000, f.player.vars[C.VARBIT_PERMITS])
        f.manager.tick(109)
        assertEquals(8_000, f.player.vars[C.VARBIT_PERMITS])
    }

    @Test fun `victory below reward threshold grants no permits`() {
        val f = Fixture()
        val game = f.start()
        f.manager.awardPoints(f.player, game, 1_999)
        game.phase = TemporossPhase.VULNERABLE
        game.essence = 0
        f.manager.tick(108)
        assertEquals(0, f.player.vars[C.VARBIT_PERMITS])
    }

    @Test fun `scheduled return clears encounter items and frees arena NPCs`() {
        val f = Fixture()
        val game = f.start()
        f.item(0, C.RAW_HARPOONFISH)
        f.item(1, C.COOKED_HARPOONFISH)
        f.item(2, C.HARPOON)
        game.phase = TemporossPhase.WON
        game.scheduledCycle = 108
        f.manager.tick(108)
        assertEquals(C.RETURN_COORD, f.player.coords)
        assertNull(f.manager.gameForPlayer(f.player))
        assertTrue(f.instances.serverOwnedSessions(C.INSTANCE_KEY).isEmpty())
        assertNull(f.player.inv[0])
        assertNull(f.player.inv[1])
        assertEquals(C.HARPOON.asRSCM(), f.player.inv[2]?.id)
        assertFalse(C.HUD_INTERFACE in f.player.ui)
        assertTrue(f.npcs.none())
    }

    @Test fun `foreign arena NPC cannot be used for this encounter`() {
        val f = Fixture()
        val game = f.start()
        assertTrue(f.manager.ownsNpc(game, checkNotNull(game.boss)))
        val other = TemporossEncounter(org.rsmod.api.instances.InstanceId(game.instanceId.value + 1))
        assertFalse(f.manager.ownsNpc(other, checkNotNull(game.boss)))
    }

    @Test fun `spirit pool scatters every four ticks and repeat clicks cannot accelerate it`() {
        val f = Fixture()
        val game = f.start()
        game.phase = TemporossPhase.VULNERABLE
        f.item(0, C.HARPOON)
        f.player.runEnergy = 0
        val awards = mutableListOf<Int>()
        for (tick in 107..119) {
            f.player.currentMapClock = tick
            val before = game.pointsFor(1L)
            repeat(2) { f.harpoonPool(game) }
            if (game.pointsFor(1L) != before) awards += tick
        }
        assertEquals(listOf(111, 115, 119), awards)
        assertEquals(165, game.pointsFor(1L))
        assertEquals(330, f.player.statMap.getXP("stat.fishing"))
    }

    @Test fun `reward net actions stay three ticks apart under repeated clicking`() {
        val f = Fixture()
        f.item(0, C.SMALL_NET)
        f.permits(10)
        val awards = mutableListOf<Int>()
        for (tick in 100..109) {
            f.player.currentMapClock = tick
            val before = f.player.vars[C.VARBIT_PERMITS]
            repeat(2) { f.search(1) }
            if (f.player.vars[C.VARBIT_PERMITS] != before) awards += tick
        }
        assertEquals(listOf(103, 106, 109), awards)
        assertEquals(7, f.player.vars[C.VARBIT_PERMITS])
        assertEquals(30, f.player.statMap.getXP("stat.fishing"))
    }

    @Test fun `missing net is supplied without spending reward permits`() {
        val f = Fixture()
        f.permits(2)
        f.search(1)
        assertEquals(C.SMALL_NET.asRSCM(), f.player.inv[0]?.id)
        assertEquals(2, f.player.vars[C.VARBIT_PERMITS])
        assertEquals(0, f.player.statMap.getXP("stat.fishing"))
    }

    private class Fixture(randomValue: Int = 1) {
        val clock = MapClock(100)
        val eventBus = EventBus()
        val collision = CollisionFlagMap()
        val players = PlayerList()
        val npcs = NpcList()
        val npcRegistry = NpcRegistry(npcs, collision, eventBus)
        val storage = LocZoneStorage()
        val updates = ZoneUpdateMap()
        val normalLocs = LocRegistryNormal(updates, collision, storage)
        val regions = RegionRegistry(RegionListSmall(), RegionListLarge(),
            normalLocs, collision, storage,
            npcRegistry, ControllerRegistry(clock, ControllerList()), ZonePlayerActivityBitSet())
        val repo = NpcRepository(clock, npcRegistry, npcs)
        val instances = InstanceManager(RegionRepository(regions), repo, players, eventBus,
            InstanceAreaResolver(), clock, collision)
        val random = object : GameRandom {
            override fun of(maxExclusive: Int) = randomValue.coerceAtMost(maxExclusive - 1)
            override fun of(minInclusive: Int, maxInclusive: Int) = minInclusive
            override fun randomDouble() = 0.0
        }
        val locRepo = org.rsmod.api.repo.loc.LocRepository(clock,
            org.rsmod.api.registry.loc.LocRegistry(storage, normalLocs,
                org.rsmod.api.registry.loc.LocRegistryRegion(updates, collision, storage, regions)), regions)
        val worldRepo = org.rsmod.api.repo.world.WorldRepository(updates)
        val manager = TemporossEncounterManager(instances, repo, players, eventBus, random, locRepo, worldRepo, collision)
        val lobby = TemporossLobbyScript(manager, clock, random)
        val arena = TemporossArenaScript(manager, clock, random, org.rsmod.api.stats.xpmod.XpModifiers(setOf(org.rsmod.content.skills.fishing.SpiritAnglerWornSetXpModifiers())))
        var teleportDenied = false
        val player = Player().apply {
            uuid = 1L; slotId = 1; currentMapClock = 100; coords = C.PIER_COORD
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
            invMap["inv.bank"] = Inventory(InventoryServerType(size = 800), arrayOfNulls(800))
            invMap["inv.collection_transmit"] = Inventory(InventoryServerType(size = 2000), arrayOfNulls(2000))
            statMap.setBaseLevel("stat.fishing", 99)
            statMap.setCurrentLevel("stat.fishing", 99)
        }
        init {
            players[1] = player
            for (level in 0..3) for (x in 3008 until 3072 step 8) for (z in 2816 until 2880 step 8) {
                collision.allocateIfAbsent(x, z, level)
            }
            collision.allocateIfAbsent(C.RETURN_COORD.x, C.RETURN_COORD.z, 0)
        }
        fun start(): TemporossEncounter {
            manager.startSolo(player, 100)
            manager.tick(107)
            player.currentMapClock = 107
            return checkNotNull(manager.gameForPlayer(player))
        }
        fun permits(count: Int) = VarPlayerIntMapSetter.set(player, C.VARBIT_PERMITS, count)
        fun item(slot: Int, type: String, count: Int = 1) {
            player.inv[slot] = InvObj(checkNotNull(ServerCacheManager.getItem(type.asRSCM())), count)
        }
        fun fillInventory() { for (slot in 0 until 28) item(slot, C.HARPOON) }
        fun due() { player.actionDelay = player.currentMapClock; player.skillAnimDelay = player.currentMapClock }
        fun access() = ProtectedAccess(player, GameCoroutine(), ProtectedAccessContext(
            getRandom = { random }, getEventBus = { eventBus }, getNpcList = { npcs }, getPlayerList = { players },
            getCollision = { collision }, getAreaChecker = { AreaChecker(regions, AreaIndex()) },
            getAlignment = { error("unused") }, getLocInteractions = { LocInteractions(BoundValidator(collision), eventBus) },
            getNpcInteractions = { NpcInteractions(eventBus) }, getPlayerInteractions = { error("unused") },
            getHeldInteractions = { error("unused") }, getWornInteractions = { error("unused") },
            getMusicPlayer = { error("unused") }, getMarketPrices = { error("unused") },
            getInstantHitProcessor = { error("unused") }, getTeleportValidator = {
                PlayerTeleportValidator(setOf(PlayerTeleportValidateHook { _, _, _ -> if (teleportDenied) "Denied" else null }))
            }, getWorldRepo = { error("unused") }, getHitModifier = { error("unused") },
        ))
        fun collect(batch: Int) = invoke(lobby, "collectRewards", access(), batch)
        fun forfeit() = invoke(arena, "forfeit", access())
        fun harpoonPool(game: TemporossEncounter) = invoke(arena, "harpoonPool", access(), game.spiritPools.first())
        fun search(batch: Int) {
            val type = checkNotNull(ServerCacheManager.getObject(C.REWARD_POOL.asRSCM()))
            val loc = BoundLocInfo(LocInfo(1, player.coords, LocEntity(type.id, 10, 0)), type)
            invoke(lobby, "searchRewardPool", access(), loc, batch)
        }
        fun cook() {
            val type = checkNotNull(ServerCacheManager.getObject(C.SHRINE.asRSCM()))
            val loc = BoundLocInfo(LocInfo(1, player.coords, LocEntity(type.id, 10, 0)), type)
            invoke(arena, "cookHarpoonfish", access(), loc)
        }
        private fun invoke(target: Any, name: String, vararg args: Any): Any? =
            target.javaClass.declaredMethods.single { it.name == name }.apply { isAccessible = true }.invoke(target, *args)
    }
}
