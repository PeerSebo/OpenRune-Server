package org.rsmod.content.minigames.gotr

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import java.lang.reflect.InvocationTargetException
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
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
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.ironman.PlayerGamemode
import org.rsmod.api.player.ironman.setGamemode
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.*
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.BoundValidator
import org.rsmod.api.route.RayCastValidator
import org.rsmod.api.route.RouteFactory
import org.rsmod.content.interfaces.bank.scripts.BankInvScript
import org.rsmod.content.minigames.gotr.scripts.GotrPortalScript
import org.rsmod.content.minigames.gotr.scripts.GotrScript
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.area.AreaIndex
import org.rsmod.game.entity.*
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GotrPortalActionTest {
    @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    private val restore = mutableListOf<() -> Unit>()

    @BeforeEach fun inventoryServices() {
        replaceStatic("org.rsmod.api.invtx.InvTransactionsScriptKt", "cachedInventoryTransactions", InvTransactions.from())
        replaceStatic("org.rsmod.api.invtx.VirtualInvTransactionsKt", "cachedPlayerItemStorage", PlayerItemStorage(emptySet()))
    }

    @AfterEach fun restoreServices() { restore.asReversed().forEach { it() }; restore.clear() }

    private fun replaceStatic(owner: String, name: String, value: Any) {
        val field = Class.forName(owner).getDeclaredField(name).apply { isAccessible = true }
        val previous = field.get(null)
        restore += { field.set(null, previous) }
        field.set(null, value)
    }

    @Test fun `matching talisman opens inactive altar and spends exactly one`() {
        val f = Fixture()
        f.beginAttack()
        assertFalse(f.game.isPortalOpen("air"))
        f.item(0, "obj.gotr_portal_talisman_air", 2)
        f.enterAltar("air", AIR_ALTAR)
        assertEquals(AIR_ALTAR, f.player.coords)
        assertEquals(1, f.player.inv[0]?.count)
        f.tick(302)
        assertTrue(f.game.isMember(f.player), "A real altar trip retains round membership.")
        assertEquals(1, f.player.vars["varbit.gotr_is_playing"])
    }

    @Test fun `naturally active altar preserves its talisman`() {
        val f = Fixture()
        f.beginAttack()
        val rune = checkNotNull(GotrHudState.normalize(f.game.currentElementalRune()))
        f.item(0, "obj.gotr_portal_talisman_$rune", 2)
        f.enterAltar(rune, AIR_ALTAR)
        assertEquals(AIR_ALTAR, f.player.coords)
        assertEquals(2, f.player.inv[0]?.count)
    }

    @Test fun `unmatched talisman cannot enter an inactive guardian`() {
        val f = Fixture()
        f.beginAttack()
        f.item(0, "obj.gotr_portal_talisman_fire")
        val before = f.player.coords
        f.enterAltar("air", AIR_ALTAR)
        assertEquals(before, f.player.coords)
        assertEquals(1, f.player.inv[0]?.count)
    }

    @Test fun `preparation and nonmember cannot spend a talisman to bypass portals`() {
        for (member in listOf(false, true)) {
            val f = Fixture()
            if (member) f.join()
            f.item(0, "obj.gotr_portal_talisman_air")
            val before = f.player.coords
            f.enterAltar("air", AIR_ALTAR)
            assertEquals(before, f.player.coords)
            assertEquals(1, f.player.inv[0]?.count)
        }
    }

    @Test fun `missing altar destination never consumes a talisman`() {
        val f = Fixture()
        f.beginAttack()
        f.item(0, "obj.gotr_portal_talisman_air")
        val before = f.player.coords
        f.enterAltar("air", null)
        assertEquals(before, f.player.coords)
        assertEquals(1, f.player.inv[0]?.count)
    }

    @Test fun `rejected teleports refund the exact talisman stack and do not record an altar trip`() {
        for (denied in listOf(false, true)) {
            val f = Fixture()
            f.beginAttack()
            f.teleportDenied = denied
            f.item(0, "obj.gotr_portal_talisman_air", 2)
            val before = f.player.coords
            f.enterAltar("air", if (denied) AIR_ALTAR else CoordGrid(1000, 1000))
            assertEquals(before, f.player.coords)
            assertEquals(2, f.player.inv[0]?.count)
            f.player.coords = AIR_ALTAR
            f.tick(302)
            assertFalse(f.game.isMember(f.player), "A denied teleport cannot authorize an unrelated altar visit.")
        }
    }

    @Test fun `talisman toggle changes the native receiving preference`() {
        val f = Fixture()
        f.action("toggleTalisman", "air")
        assertEquals(1, f.player.vars["varbit.gotr_talisman_toggle_air"])
        f.action("toggleTalisman", "air")
        assertEquals(0, f.player.vars["varbit.gotr_talisman_toggle_air"])
    }

    @Test fun `portal preference suppresses the actual colour fade without preventing entry`() {
        for (disabled in listOf(false, true)) {
            val f = Fixture()
            f.beginAttack()
            VarPlayerIntMapSetter.set(f.player, "varbit.gotr_colourful_fade_disabled", if (disabled) 1 else 0)
            val rune = checkNotNull(GotrHudState.normalize(f.game.currentElementalRune()))
            f.enterAltar(rune, AIR_ALTAR)
            assertEquals(AIR_ALTAR, f.player.coords)
            assertEquals(!disabled, f.player.ui.containsOverlay("interface.fade_overlay"))
        }
    }

    @Test fun `gate visual effects operation toggles the native saved preference both ways`() {
        val f = Fixture()
        f.toggleGateEffects()
        assertEquals(1, f.player.vars["varbit.gotr_colourful_fade_disabled"])
        f.toggleGateEffects()
        assertEquals(0, f.player.vars["varbit.gotr_colourful_fade_disabled"])
    }

    @Test fun `deposit items opens native box without moving supplies or losing item vars`() {
        val f = Fixture()
        val retained = listOf("obj.airrune", "obj.chisel", "obj.bronze_pickaxe",
            "obj.gotr_guardian_fragment", "obj.gotr_guardian_essence",
            "obj.gotr_cell_tier1", "obj.gotr_portal_talisman_air")
        retained.forEachIndexed { index, item -> f.item(index, item) }
        f.item(10, "obj.crystal_harpoon", vars = 1234)
        f.action("depositItems")
        assertTrue("interface.bank_depositbox" in f.player.ui)
        retained.forEachIndexed { index, item -> assertEquals(item.asRSCM(), f.player.inv[index]?.id) }
        assertEquals(1234, f.player.inv[10]?.vars)
        assertTrue(f.bank.isEmpty())
    }

    @Test fun `full bank leaves unbankable rune stack untouched`() {
        val f = Fixture()
        VarPlayerIntMapSetter.set(f.player, "varbit.bank_capacity", 1)
        VarPlayerIntMapSetter.set(f.player, "varbit.bank_tab_main", 1)
        f.bank[0] = InvObj("obj.coins", 1)
        f.item(0, "obj.airrune", 50)
        f.action("depositRunes")
        assertEquals(50, f.player.inv[0]?.count)
        assertEquals(InvObj("obj.coins", 1), f.bank[0])
        assertEquals(1, f.bank.count { it != null })
    }

    @Test fun `ultimate ironman cannot bank using either pool operation`() {
        val f = Fixture()
        f.player.setGamemode(PlayerGamemode.ULTIMATE_IRONMAN)
        f.item(0, "obj.airrune", 50)
        f.item(1, "obj.coins", 100)
        f.action("depositRunes")
        f.action("depositItems")
        assertEquals(50, f.player.inv[0]?.count)
        assertEquals(100, f.player.inv[1]?.count)
        assertTrue(f.bank.isEmpty())
        assertFalse("interface.bank_depositbox" in f.player.ui)
    }

    private class Fixture {
        val clock = MapClock(100)
        val events = EventBus()
        val collision = CollisionFlagMap()
        val players = PlayerList()
        val npcs = NpcList()
        val npcRegistry = NpcRegistry(npcs, collision, events)
        val storage = LocZoneStorage()
        val updates = ZoneUpdateMap()
        val normal = LocRegistryNormal(updates, collision, storage)
        val regions = RegionRegistry(RegionListSmall(), RegionListLarge(), normal, collision, storage,
            npcRegistry, ControllerRegistry(clock, ControllerList()), ZonePlayerActivityBitSet())
        val npcRepo = NpcRepository(clock, npcRegistry, npcs)
        val locRepo = LocRepository(clock, LocRegistry(storage, normal, LocRegistryRegion(updates, collision, storage, regions)), regions)
        val world = WorldRepository(updates)
        val random = object : GameRandom {
            override fun of(maxExclusive: Int) = 0
            override fun of(minInclusive: Int, maxInclusive: Int) = minInclusive
            override fun randomDouble() = 0.0
        }
        val defence = GotrDefence(locRepo, npcRepo, clock, random, RouteFactory(collision), RayCastValidator(collision), players, collision)
        val game = GotrGame(locRepo, players, clock, random, defence, GotrHud(events), GotrPortalPresentation(locRepo, world))
        val script = GotrPortalScript(game, random, BankInvScript(events, WornBonuses(), WeaponSpeeds(AttackStyles())))
        var teleportDenied = false
        val bank = Inventory(InventoryServerType(size = 800), arrayOfNulls(800))
        val player = Player().apply {
            uuid = 1L; slotId = 1; displayName = "Tester"; currentMapClock = 100
            coords = CoordGrid(3615, 9491)
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
            invMap["inv.bank"] = bank
            VarPlayerIntMapSetter.set(this, "varbit.bank_capacity", 800)
            statMap.setBaseLevel("stat.runecrafting", 99)
            statMap.setCurrentLevel("stat.runecrafting", 99)
            players[1] = this
        }
        init {
            for (x in 3520..3712 step 8) for (z in 9408..9600 step 8) collision.allocateIfAbsent(x, z, 0)
            collision.allocateIfAbsent(AIR_ALTAR.x, AIR_ALTAR.z, 0)
            npcRepo.add(Npc("npc.gotr_great_guardian", CoordGrid(3613, 9501)), Int.MAX_VALUE)
        }
        fun join() { game.start(); assertEquals(GotrEnterResult.Allowed, game.tryEnter(player)) }
        fun beginAttack() { join(); tick(301); assertTrue(game.portalsAvailable()) }
        fun tick(cycle: Int) { clock.cycle = cycle; player.currentMapClock = cycle; game.tick() }
        fun item(slot: Int, type: String, count: Int = 1, vars: Int = 0) { player.inv[slot] = InvObj(type, count, vars) }
        fun access() = ProtectedAccess(player, GameCoroutine(), ProtectedAccessContext(
            getRandom = { random }, getEventBus = { events }, getNpcList = { npcs }, getPlayerList = { players },
            getCollision = { collision }, getAreaChecker = { AreaChecker(regions, AreaIndex()) },
            getAlignment = { error("unused") }, getLocInteractions = { LocInteractions(BoundValidator(collision), events) },
            getNpcInteractions = { NpcInteractions(events) }, getPlayerInteractions = { error("unused") },
            getHeldInteractions = { error("unused") }, getWornInteractions = { error("unused") },
            getMusicPlayer = { error("unused") }, getMarketPrices = { error("unused") },
            getInstantHitProcessor = { error("unused") }, getTeleportValidator = {
                PlayerTeleportValidator(setOf(PlayerTeleportValidateHook { _, _, _ -> if (teleportDenied) "Denied" else null }))
            },
            getWorldRepo = { world }, getHitModifier = { error("unused") },
        ))
        fun enterAltar(rune: String, dest: CoordGrid?) {
            val continuation = object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { result.getOrThrow() }
            }
            assertNotSame(COROUTINE_SUSPENDED, invoke("enter", access(), rune, dest, continuation))
        }
        fun action(name: String, vararg args: Any) { invoke(name, access(), *args) }
        fun toggleGateEffects() {
            val gate = GotrScript(game)
            gate.javaClass.declaredMethods.single { it.name == "visualEffects" }
                .apply { isAccessible = true }.invoke(gate, access())
        }
        private fun invoke(name: String, vararg args: Any?): Any? = try {
            script.javaClass.declaredMethods.single { it.name.substringBefore('-') == name }.apply { isAccessible = true }.invoke(script, *args)
        } catch (ex: InvocationTargetException) { throw ex.cause ?: ex }
    }

    private companion object { val AIR_ALTAR = CoordGrid(2841, 4829) }
}
