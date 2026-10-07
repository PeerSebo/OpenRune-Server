package org.rsmod.content.raids.theatreofblood

import dev.openrune.ServerCacheManager
import dev.openrune.types.ItemServerType
import jakarta.inject.Provider
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossExtensionRegistry
import org.rsmod.api.bosses.runtime.EncounterRegistry
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.death.PlayerDeathDrops
import org.rsmod.api.death.PlayerDeathItemStorageHook
import org.rsmod.api.death.grave.GraveManager
import org.rsmod.api.instances.*
import org.rsmod.api.instances.region.BaseAreaKey
import org.rsmod.api.instances.region.InstanceAreaResolver
import org.rsmod.api.instances.region.InstancePlacement
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.music.MusicRepository
import org.rsmod.api.player.disablePrayers
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.player.hook.GroundItemDropResolver
import org.rsmod.api.player.ironman.PlayerGamemode
import org.rsmod.api.player.ironman.setGamemode
import org.rsmod.api.player.music.MusicPlayer
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.region.RegionStaticTemplate
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.BoundValidator
import org.rsmod.api.route.RayCastValidator
import org.rsmod.api.route.StepFactory
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.routefinder.collision.CollisionFlagMap
import sun.misc.Unsafe

@ResourceLock("ServerCacheManager")
class TheatreLifecycleTest {
    @Test fun `voluntary combat exit keeps equipment hardcore status and the existing death count`() {
        val fixture = Fixture()
        val player = fixture.player()
        fixture.equip(player)
        player.setGamemode(PlayerGamemode.HARDCORE_IRONMAN)
        fixture.attach(player)
        TheatreManager.set(player, TheatreManager.DISCONNECT, 1)
        TheatreManager.set(player, TheatreManager.DEATHS, 2)
        val carried = player.inv.toList()
        val equipped = player.worn.toList()
        fixture.manager.leave(player)
        assertEquals(carried, player.inv.toList())
        assertEquals(equipped, player.worn.toList())
        assertEquals(PlayerGamemode.HARDCORE_IRONMAN, player.gamemode)
        assertEquals(2, player.vars[TheatreManager.DEATHS])
        assertTrue(fixture.inventory.recovery(player).isEmpty())
        assertEquals(0, player.vars[TheatreInventory.RECOVERY_FEE])
        assertEquals(0, player.vars[TheatreManager.DISCONNECT])
    }

    @Test fun `protect item is retained after prayers are disabled for death`() {
        val fixture = Fixture()
        val player = fixture.player()
        fixture.equip(player)
        VarPlayerIntMapSetter.set(player, "varbit.prayer_protectitem", 1)
        fixture.manager.captureDeathProtection(player)
        player.disablePrayers()
        assertEquals(0, player.vars["varbit.prayer_protectitem"])
        assertTrue(fixture.manager.recoverLostGear(player))
        assertEquals(4, player.inv.occupiedSpace())
        assertEquals(1, fixture.inventory.recovery(player).occupiedSpace())
        assertEquals(0, player.vars["varp.theatre_recovery_pending"])
    }

    @Test fun `forced disconnect recovery executes once and does not remove survivor gear on retry`() {
        val fixture = Fixture()
        val player = fixture.player()
        fixture.equip(player)
        TheatreManager.set(player, TheatreManager.DISCONNECT, 1)
        fixture.manager.login(player)
        assertEquals(3, player.inv.occupiedSpace())
        assertEquals(2, fixture.inventory.recovery(player).occupiedSpace())
        assertEquals(TheatreData.rules.exit, player.coords)
        val carried = player.inv.toList()
        val chest = fixture.inventory.recovery(player).toList()
        fixture.manager.login(player)
        assertEquals(carried, player.inv.toList())
        assertEquals(chest, fixture.inventory.recovery(player).toList())
        TheatreManager.set(player, TheatreManager.DISCONNECT, 1)
        fixture.manager.login(player)
        assertEquals(carried, player.inv.toList())
        assertEquals(chest, fixture.inventory.recovery(player).toList())
    }

    @Test fun `shutdown departure preserves all gear and does not create a fee`() {
        val fixture = Fixture()
        val player = fixture.player()
        fixture.equip(player)
        val run = fixture.attach(player)
        TheatreManager.set(player, TheatreManager.DISCONNECT, 1)
        player.pendingShutdown = true
        val carried = player.inv.toList()
        fixture.manager.departed(player, run.session.id)
        assertEquals(carried, player.inv.toList())
        assertTrue(fixture.inventory.recovery(player).isEmpty())
        assertEquals(0, player.vars[TheatreInventory.RECOVERY_FEE])
        assertEquals(0, player.vars[TheatreManager.DISCONNECT])
        fixture.manager.login(player)
        assertEquals(carried, player.inv.toList())
    }

    @Test fun `combat departure recovers gear while peaceful departure preserves it`() {
        val fixture = Fixture()
        val fighter = fixture.player()
        val peaceful = fixture.player()
        fixture.equip(fighter)
        fixture.equip(peaceful)
        val run = fixture.attach(fighter, peaceful)
        TheatreManager.set(fighter, TheatreManager.DISCONNECT, 1)
        fixture.manager.departed(fighter, run.session.id)
        fixture.manager.departed(peaceful, run.session.id)
        assertEquals(3, fighter.inv.occupiedSpace())
        assertEquals(2, fixture.inventory.recovery(fighter).occupiedSpace())
        assertEquals(5, peaceful.inv.occupiedSpace())
        assertTrue(fixture.inventory.recovery(peaceful).isEmpty())
    }

    @Test fun `queued wipe during shutdown preserves both party members gear`() {
        val fixture = Fixture()
        val a = fixture.player()
        val b = fixture.player()
        fixture.equip(a)
        fixture.equip(b)
        val run = fixture.attach(a, b)
        a.pendingShutdown = true
        b.pendingShutdown = true
        fixture.manager.fail(run)
        for (player in listOf(a, b)) {
            assertEquals(5, player.inv.occupiedSpace())
            assertTrue(fixture.inventory.recovery(player).isEmpty())
            assertEquals(0, player.vars[TheatreInventory.RECOVERY_FEE])
        }
    }

    @Test fun `party wipe stores both inventories once and clears instance membership`() {
        val fixture = Fixture()
        val a = fixture.player()
        val b = fixture.player()
        fixture.equip(a)
        fixture.equip(b)
        val run = fixture.attach(a, b)
        fixture.manager.fail(run)
        assertEquals(TheatreManager.FAILED, run.stage)
        for (player in listOf(a, b)) {
            assertEquals(3, player.inv.occupiedSpace())
            assertEquals(2, fixture.inventory.recovery(player).occupiedSpace())
            assertEquals(100_000, player.vars[TheatreInventory.RECOVERY_FEE])
            assertNull(fixture.manager.run(player))
            assertEquals(TheatreData.rules.exit, player.coords)
        }
        val before = a.inv.toList() to fixture.inventory.recovery(a).toList()
        fixture.manager.fail(run)
        assertEquals(before, a.inv.toList() to fixture.inventory.recovery(a).toList())
    }

    @Test fun `existing recovery cannot be overwritten by another death`() {
        val fixture = Fixture()
        val player = fixture.player()
        fixture.equip(player)
        fixture.inventory.storeRecovery(player, listOf(InvObj("obj.bones", 1, 77)))
        val before = player.inv.toList()
        assertFalse(fixture.manager.recoverLostGear(player))
        assertEquals(before, player.inv.toList())
        assertEquals(77, fixture.inventory.recovery(player)[0]?.vars)
    }

    @Test fun `worn item vars survive a wipe and equipment is cleared without deleting them`() {
        val fixture = Fixture()
        val player = fixture.player()
        fixture.equip(player)
        val worn = InvObj("obj.bronze_platebody", 1, 888)
        player.worn[0] = worn
        player.appearance.javaClass.getDeclaredField("rebuild").apply { isAccessible = true }.setBoolean(player.appearance, false)
        val original = player.inv.filterNotNull { true } + player.worn.filterNotNull { true }
        assertTrue(fixture.manager.recoverLostGear(player))
        assertTrue(player.worn.isEmpty())
        assertTrue(player.appearance.rebuild)
        val retained = player.inv.filterNotNull { true } + fixture.inventory.recovery(player).filterNotNull { true }
        assertEquals(original.toSet(), retained.toSet())
        assertEquals(original.size, retained.size)
        assertTrue(worn in retained)
    }

    @Test fun `dangerous recovery revokes hardcore but never changes ultimate ironman mode`() {
        val fixture = Fixture()
        val hardcore = fixture.player()
        val ultimate = fixture.player()
        fixture.equip(hardcore)
        fixture.equip(ultimate)
        hardcore.setGamemode(PlayerGamemode.HARDCORE_IRONMAN)
        ultimate.setGamemode(PlayerGamemode.ULTIMATE_IRONMAN)
        assertTrue(fixture.manager.recoverLostGear(hardcore))
        assertTrue(fixture.manager.recoverLostGear(ultimate))
        assertEquals(PlayerGamemode.IRONMAN, hardcore.gamemode)
        assertEquals(PlayerGamemode.IRONMAN, hardcore.vars["varbit.ironman"])
        assertEquals(PlayerGamemode.ULTIMATE_IRONMAN, ultimate.gamemode)
        assertEquals(5, fixture.inventory.recovery(ultimate).occupiedSpace())
    }

    internal class Fixture {
        private val clock = MapClock(100)
        private val events = EventBus()
        private val collision = CollisionFlagMap()
            .apply {
                val exit = TheatreData.rules.exit
                allocateIfAbsent(exit.x, exit.z, exit.level)
            }
        private val players = PlayerList()
        private val random = DefaultGameRandom(1)
        val inventory = TheatreInventory(random)
        private val instances = InstanceManager(unused<RegionRepository>(), unused<NpcRepository>(),
            players, events, InstanceAreaResolver(), clock, collision)
        private val prices = object : MarketPrices {
            override fun get(type: ItemServerType): Int? = type.cost
        }
        private val drops = PlayerDeathDrops(clock, unused<ObjRepository>(), prices,
            unused<GroundItemDropResolver>(), unused<GraveManager>(),
            setOf(PlayerDeathItemStorageHook { player, items ->
                check(manager.isWiping(player))
                check(inventory.storeRecovery(player, items))
                true
            }))
        private val deps = BossDeps(random, unused<AccuracyFormulae>(), WorldRepository(ZoneUpdateMap()),
            unused<NpcRepository>(), players, clock, WorldQueueList(), collision,
            EncounterRegistry(clock), BossExtensionRegistry(), NoopPlayerHitModifier)
        private val music = MusicPlayer::class.java.getDeclaredConstructor(
            GameRandom::class.java, MusicRepository::class.java,
        ).apply { isAccessible = true }.newInstance(random, MusicRepository(random).apply { load() })
        private val encounters by lazy { TheatreEncounters(manager, StepFactory(collision), TheatreBloat(manager, RayCastValidator(collision))) }
        private val nylocas by lazy { TheatreNylocas(Provider { manager }, RayCastValidator(collision), BoundValidator(collision)) }
        private val roomObjects = TheatreRoomObjects(instances, unused<LocRepository>(),
            unused<ObjRepository>(), collision, inventory, unused<NpcRepository>())
        val manager: TheatreManager = TheatreManager(deps, instances, inventory, drops, events,
            Provider { encounters }, Provider { nylocas }, TheatrePresentation(music), roomObjects, TheatreParties())
        private var nextPlayer = 1L

        fun player() = Player().apply {
            uuid = nextPlayer++
            currentMapClock = clock.cycle
            coords = TheatreData.rules.exit
            inv = Inventory.create("inv.inv")
            worn = Inventory.create("inv.worn")
            invMap["inv.inv"] = inv
            invMap["inv.worn"] = worn
            statMap.setBaseLevel("stat.hitpoints", 99)
            statMap.setCurrentLevel("stat.hitpoints", 99)
            statMap.setBaseLevel("stat.prayer", 99)
            statMap.setCurrentLevel("stat.prayer", 99)
            client = object : Client<Any, Any> {
                override fun write(message: Any) {}
                override fun close() {}
                override fun read(player: Player) {}
                override fun flush() {}
                override fun flushHighPriority() {}
                override fun unregister(service: Any, player: Player) {}
            }
        }

        fun equip(player: Player) {
            val items = listOf("obj.rune_platebody", "obj.rune_platelegs", "obj.rune_full_helm",
                "obj.rune_kiteshield", "obj.bronze_dagger")
            items.forEachIndexed { slot, obj -> player.inv[slot] = InvObj(obj, 1, slot + 10) }
        }

        @Suppress("UNCHECKED_CAST")
        fun attach(vararg members: Player): TheatreRun {
            val area = InstanceArea.copyRegions(TheatreData.rules.regions,
                enterCoord = RegionLocal(0, 49, 66, 0, 0), exitCoord = TheatreData.rules.exit)
            val spec = InstanceSettings(destroyWhenEmpty = false).withArea(area, TheatreData.rules.rowId)
            val placement = InstancePlacement(RegionLocal(0, 49, 66, 0, 0), TheatreData.rules.exit,
                unused<RegionStaticTemplate>(), emptyList(), 0, BaseAreaKey(0, 0, 0))
            val session = InstanceSession(InstanceId(1), mutableSetOf(), checkNotNull(members.first().uuid),
                TheatreManager.KEY, spec, placement, InstanceAccess.Private)
            members.forEach { session.addOccupant(checkNotNull(it.uuid)) }
            val run = TheatreRun(session, Npc("npc.tob_sotetseg_creeper", TheatreData.rules.exit))
            run.members += members
            run.stage = TheatreManager.ACTIVE
            val runsField = TheatreManager::class.java.getDeclaredField("runs").apply { isAccessible = true }
            (runsField.get(manager) as MutableMap<InstanceId, TheatreRun>)[session.id] = run
            val sessionsField = InstanceManager::class.java.getDeclaredField("sessions").apply { isAccessible = true }
            (sessionsField.get(instances) as MutableMap<InstanceId, InstanceSession>)[session.id] = session
            val playersField = InstanceManager::class.java.getDeclaredField("playerIndex").apply { isAccessible = true }
            val index = playersField.get(instances) as MutableMap<Long, InstanceId>
            members.forEach { index[checkNotNull(it.uuid)] = session.id }
            return run
        }
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
        private inline fun <reified T> unused(): T {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
            return (field.get(null) as Unsafe).allocateInstance(T::class.java) as T
        }
    }
}
