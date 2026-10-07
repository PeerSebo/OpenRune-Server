package org.rsmod.content.minigames.tempoross

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
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
import org.rsmod.game.client.Client
import org.rsmod.game.client.NoopClient
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
import org.rsmod.game.ui.UserInterface
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TemporossEncounterTest {
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

    @Test fun `ammunition fires in loading order and retains fish while submerged or disabled`() {
        val cannon = TemporossCannon()
        cannon.ammunition.addAll(listOf(TemporossAmmo.Cooked, TemporossAmmo.Raw, TemporossAmmo.Crystal))
        assertNull(cannon.takeShot(10, false))
        assertEquals(3, cannon.ammunition.size)
        cannon.disabledUntil = 12
        assertNull(cannon.takeShot(11, true))
        assertEquals(TemporossAmmo.Cooked, cannon.takeShot(12, true))
        assertNull(cannon.takeShot(12, true))
        assertNull(cannon.takeShot(14, true))
        assertEquals(TemporossAmmo.Raw, cannon.takeShot(15, true))
        assertEquals(TemporossAmmo.Crystal, cannon.takeShot(18, true))
        assertNull(cannon.takeShot(21, true))
    }

    @Test fun `loading during vulnerable phase awards once and defers damage until projectile impact`() {
        val f = Fixture()
        val game = f.start()
        f.quiet(game)
        game.phase = TemporossPhase.VULNERABLE
        game.energy = 100
        f.item(0, C.COOKED_HARPOONFISH)
        f.due()
        f.fill(game)
        assertNull(f.player.inv[0])
        assertEquals(100, game.energy)
        assertEquals(65, game.pointsFor(1L))
        assertEquals(listOf(TemporossAmmo.Cooked), game.cannons[0].ammunition.toList())
        f.tick(108)
        assertEquals(1, game.cannons[0].ammunition.size)
        game.phase = TemporossPhase.ENERGY
        val energy = game.energy
        f.tick(109)
        assertTrue(game.cannons[0].ammunition.isEmpty())
        assertEquals(energy, game.energy)
        f.tick(111)
        assertEquals(energy, game.energy)
        f.tick(112)
        assertEquals(energy - 15, game.energy)
        assertEquals(65, game.pointsFor(1L))
    }

    @Test fun `eighteen preloaded fish split between two cannons rescue the enraged fourth phase`() {
        val f = Fixture()
        val game = f.start()
        f.quiet(game)
        game.phase = TemporossPhase.ENRAGED
        game.cycle = 3
        repeat(9) { game.cannons[0].ammunition += TemporossAmmo.Cooked; game.cannons[1].ammunition += TemporossAmmo.Cooked }
        for (tick in 108..136) f.tick(tick)
        assertEquals(TemporossPhase.VULNERABLE, game.phase)
        assertEquals(4, game.cycle)
        assertEquals(0, game.intensity)
    }

    @Test fun `third resurfacing preserves score and essence until storm actually reaches maximum`() {
        val f = Fixture()
        val game = f.start()
        f.quiet(game)
        game.phase = TemporossPhase.VULNERABLE
        game.energy = game.energyMax
        game.essence = 12
        game.cycle = 3
        f.manager.awardPoints(f.player, game, 2000)
        f.tick(108)
        assertEquals(TemporossPhase.ENRAGED, game.phase)
        assertEquals(12, game.essence)
        assertEquals(2000, game.pointsFor(1L))
        game.intensity = 99
        f.tick(109)
        assertEquals(TemporossPhase.STORM_LOSS, game.phase)
        assertEquals(0, game.pointsFor(1L))
    }

    @Test fun `vulnerability after enrage restores energy faster without restoring essence`() {
        val f = Fixture()
        val game = f.start()
        f.quiet(game)
        game.phase = TemporossPhase.VULNERABLE
        game.energy = 0
        game.essence = 20
        game.cycle = 3
        f.tick(108)
        val ordinary = game.energy
        game.energy = 0
        game.energyRegenerationRemainder = 0
        game.cycle = 4
        f.tick(109)
        assertEquals(ordinary * 2, game.energy)
        assertEquals(20, game.essence)
    }

    @Test fun `tether protects supplies awards wave points and cleans movement lock on leaving`() {
        val f = Fixture(randomValue = 99)
        val game = f.start()
        f.placeAnchor(game, 0)
        f.item(0, C.ROPE)
        f.item(1, C.RAW_HARPOONFISH)
        assertTrue(f.manager.tether(game, f.player, 0))
        assertTrue(f.player.frozen)
        assertFalse(f.manager.canAct(f.player, game))
        assertEquals(2, f.player.vars[C.TETHER_VARBIT])
        f.manager.environment.wave(game, listOf(f.player)) { player, points -> f.manager.awardPoints(player, game, points) }
        assertEquals(10, game.pointsFor(1L))
        assertEquals(C.RAW_HARPOONFISH.asRSCM(), f.player.inv[1]?.id)
        f.manager.onPlayerLeave(f.player, game.instanceId)
        assertFalse(f.player.frozen)
        assertTrue(game.tethers.isEmpty())
        assertEquals(0, f.player.vars[C.TETHER_VARBIT])
    }

    @Test fun `tether does not take ownership of an existing combat freeze`() {
        val f = Fixture()
        val game = f.start()
        f.placeAnchor(game, 0)
        f.item(0, C.ROPE)
        f.player.frozen = true
        assertFalse(f.manager.tether(game, f.player, 0))
        assertTrue(game.tethers.isEmpty())
        assertTrue(f.player.frozen)
    }

    @Test fun `only full spirit angler outfit replaces rope`() {
        val f = Fixture()
        val game = f.start()
        f.placeAnchor(game, 2)
        for ((index, item) in C.SPIRIT_ANGLER.withIndex()) f.player.worn[index] = InvObj(item)
        assertTrue(f.manager.tether(game, f.player, 2))
        assertEquals(1, f.player.vars[C.TETHER_VARBIT])
        f.manager.environment.untether(game, f.player, false)
        f.player.worn[3] = null
        assertFalse(f.manager.tether(game, f.player, 2))
        assertFalse(f.player.frozen)
    }

    @Test fun `wave breaks anchor only after protecting tethered players and preserving its rotation`() {
        val f = Fixture(randomValue = 0)
        val game = f.start()
        val original = f.placeAnchor(game, 0)
        f.item(0, C.ROPE)
        f.manager.tether(game, f.player, 0)
        f.manager.environment.wave(game, listOf(f.player)) { player, points -> f.manager.awardPoints(player, game, points) }
        assertEquals(10, game.pointsFor(1L))
        assertTrue(0 in game.brokenAnchors)
        assertFalse(f.player.frozen)
        assertEquals(C.ANCHORS[0].broken.asRSCM(), game.anchors.getValue(0).id)
        assertEquals(original.angle, game.anchors.getValue(0).angle)
        assertFalse(f.manager.tether(game, f.player, 0))
    }

    @Test fun `repair grants points once and restores original native anchor without losing tools`() {
        val f = Fixture()
        val game = f.start()
        f.placeAnchor(game, 0)
        game.brokenAnchors += 0
        f.item(0, C.HAMMER)
        f.due()
        f.anchor(game, 0)
        assertTrue(game.brokenAnchors.isEmpty())
        assertEquals(40, game.pointsFor(1L))
        assertEquals(0, f.player.statMap.getXP("stat.construction"))
        assertEquals(C.HAMMER.asRSCM(), f.player.inv[0]?.id)
        assertFalse(f.manager.repair(game, 0))
        assertEquals(2, game.anchors.getValue(0).angle.id)
    }

    @Test fun `hazards cannot delete valuable items harpoons or imcando hammers`() {
        val f = Fixture(randomValue = 0)
        f.item(0, "obj.coins", 1000000)
        f.item(1, C.HARPOON)
        f.item(2, "obj.imcando_hammer")
        f.item(3, C.ROPE)
        f.item(4, C.CRYSTAL_HARPOONFISH)
        f.item(5, C.WATER_BUCKET)
        assertEquals(3, f.manager.environment.loseSupplies(f.player, 28))
        assertEquals(1000000, f.player.inv[0]?.count)
        assertEquals(C.HARPOON.asRSCM(), f.player.inv[1]?.id)
        assertEquals("obj.imcando_hammer".asRSCM(), f.player.inv[2]?.id)
    }

    @Test fun `fire dousing exchanges a full inventory bucket and only rewards the first douse`() {
        val f = Fixture()
        val game = f.start()
        val coord = game.translate(org.rsmod.map.CoordGrid(3045, 2854, 0))
        assertTrue(f.manager.environment.spawnFire(game, coord, 107))
        val fire = game.fires.getValue(coord)
        f.fillInventory()
        f.item(0, C.WATER_BUCKET)
        f.due()
        f.douse(fire.npc)
        assertEquals(C.BUCKET.asRSCM(), f.player.inv[0]?.id)
        assertEquals(40, game.pointsFor(1L))
        assertTrue(game.fires.isEmpty())
        f.item(0, C.WATER_BUCKET)
        f.due()
        f.douse(fire.npc)
        assertEquals(C.WATER_BUCKET.asRSCM(), f.player.inv[0]?.id)
        assertEquals(40, game.pointsFor(1L))
    }

    @Test fun `dousing without water cannot remove a fire or gain points`() {
        val f = Fixture()
        val game = f.start()
        val coord = game.translate(org.rsmod.map.CoordGrid(3045, 2854, 0))
        assertTrue(f.manager.environment.spawnFire(game, coord, 107))
        f.item(0, C.BUCKET)
        f.due()
        f.douse(game.fires.getValue(coord).npc)
        assertEquals(1, game.fires.size)
        assertEquals(0, game.pointsFor(1L))
    }

    @Test fun `wave extinguishes all fires and stops their spread`() {
        val f = Fixture(randomValue = 99)
        val game = f.start()
        f.quiet(game)
        val coord = game.translate(org.rsmod.map.CoordGrid(3045, 2854, 0))
        assertTrue(f.manager.environment.spawnFire(game, coord, 107))
        f.manager.environment.wave(game, emptyList()) { _, _ -> error("no player") }
        f.tick(125)
        assertTrue(game.fires.isEmpty())
        assertTrue(f.locRepo.findAll(coord).none { it.id == C.FIRE_LOC.asRSCM() })
    }

    @Test fun `water pump fills one bucket per tick in a full inventory without XP`() {
        val f = Fixture()
        f.start()
        f.fillInventory()
        f.item(0, C.BUCKET)
        f.item(1, C.BUCKET)
        f.due()
        repeat(2) { f.water() }
        assertEquals(1, f.player.inv.count(C.WATER_BUCKET))
        assertEquals(1, f.player.inv.count(C.BUCKET))
        f.player.currentMapClock++
        f.water()
        assertEquals(2, f.player.inv.count(C.WATER_BUCKET))
        assertEquals(0, f.player.statMap.getXP("stat.fishing"))
    }

    @Test fun `disabled cannon rejects loading without consuming fish or granting XP`() {
        val f = Fixture()
        val game = f.start()
        game.cannons[0].disabledUntil = 150
        f.item(0, C.COOKED_HARPOONFISH)
        f.due()
        f.fill(game)
        assertEquals(C.COOKED_HARPOONFISH.asRSCM(), f.player.inv[0]?.id)
        assertTrue(game.cannons[0].ammunition.isEmpty())
        assertEquals(0, game.pointsFor(1L))
    }

    @Test fun `crystal loading has raw damage and points with twenty percent additional XP`() {
        val f = Fixture()
        val game = f.start()
        f.item(0, C.CRYSTAL_HARPOONFISH)
        f.due()
        f.fill(game)
        assertEquals(TemporossAmmo.Crystal, game.cannons[0].ammunition.first())
        assertEquals(20, game.pointsFor(1L))
        assertEquals(120, f.player.statMap.getXP("stat.fishing"))
        assertEquals(10, game.cannons[0].ammunition.first().damage)
    }

    @Test fun `special fishing spot extra catch fits only available slots and cannot duplicate XP`() {
        val f = Fixture(randomValue = 0)
        val game = f.start()
        f.fillInventory()
        f.player.inv[27] = null
        game.specialSpot = 0
        f.due()
        f.fish(game)
        assertEquals(1, f.player.inv.count(C.RAW_HARPOONFISH))
        assertEquals(5, game.pointsFor(1L))
        assertEquals(10, f.player.statMap.getXP("stat.fishing"))
    }

    @Test fun `infernal harpoon preserves cooked catch and awards half cooking XP`() {
        val f = Fixture(randomValue = 0)
        val game = f.start()
        f.item(0, "obj.infernal_harpoon")
        f.due()
        f.fish(game)
        assertEquals(1, f.player.inv.count(C.COOKED_HARPOONFISH))
        assertEquals(0, f.player.inv.count(C.RAW_HARPOONFISH))
        assertEquals(5, f.player.statMap.getXP("stat.cooking"))
        assertEquals(15, game.pointsFor(1L))
    }

    @Test fun `last harpoon charge depletes before a bonus fish applies its effect`() {
        for ((active, inactive, specialFish) in listOf(
            Triple("obj.crystal_harpoon", "obj.crystal_harpoon_inactive", C.CRYSTAL_HARPOONFISH),
            Triple("obj.infernal_harpoon", "obj.infernal_harpoon_empty", C.COOKED_HARPOONFISH),
        )) {
            val f = Fixture(randomValue = 0)
            val game = f.start()
            f.player.inv[0] = InvObj(checkNotNull(ServerCacheManager.getItem(active.asRSCM())), 1, 1)
            game.specialSpot = 0
            f.due()
            f.fish(game)
            assertEquals(inactive.asRSCM(), f.player.inv[0]?.id)
            assertEquals(1, f.player.inv.count(specialFish))
            assertEquals(1, f.player.inv.count(C.RAW_HARPOONFISH))
            assertEquals(20, f.player.statMap.getXP("stat.fishing"))
            assertEquals(if (specialFish == C.COOKED_HARPOONFISH) 5 else 0, f.player.statMap.getXP("stat.cooking"))
        }
    }

    @Test fun `fishing warmup full inventory and wrong phase preserve harpoon charges`() {
        val f = Fixture(randomValue = 0)
        val game = f.start()
        val tool = InvObj(checkNotNull(ServerCacheManager.getItem("obj.crystal_harpoon".asRSCM())), 1, 2)
        f.player.inv[0] = tool
        f.fish(game)
        assertSame(tool, f.player.inv[0])
        for (slot in 1..27) f.item(slot, C.HARPOON)
        f.due()
        f.fish(game)
        assertSame(tool, f.player.inv[0])
        f.player.inv[27] = null
        game.phase = TemporossPhase.VULNERABLE
        f.due()
        f.fish(game)
        assertSame(tool, f.player.inv[0])
        assertEquals(0, game.pointsFor(1L))
    }

    @Test fun `spirit scattering consumes one crystal charge only on its action boundary`() {
        val f = Fixture(randomValue = 0)
        val game = f.start()
        f.player.inv[0] = InvObj(checkNotNull(ServerCacheManager.getItem("obj.crystal_harpoon".asRSCM())), 1, 2)
        game.phase = TemporossPhase.VULNERABLE
        game.energy = 0
        f.harpoonPool(game)
        assertEquals(2, f.player.inv[0]?.vars)
        f.due()
        f.harpoonPool(game)
        assertEquals(1, f.player.inv[0]?.vars)
        assertEquals(C.POINTS_SCATTER, game.pointsFor(1L))
        f.harpoonPool(game)
        assertEquals(1, f.player.inv[0]?.vars)
        game.phase = TemporossPhase.ENERGY
        f.due()
        f.harpoonPool(game)
        assertEquals(1, f.player.inv[0]?.vars)
        assertEquals(C.POINTS_SCATTER, game.pointsFor(1L))
    }

    @Test fun `barehand fishing requires training and levels and awards strength XP`() {
        val f = Fixture(randomValue = 0)
        val game = f.start()
        f.player.statMap.setCurrentLevel("stat.strength", 35)
        assertFalse(TemporossFishing.canBarehand(f.player))
        VarPlayerIntMapSetter.set(f.player, "varbit.brut_fishing_s", 1)
        assertTrue(TemporossFishing.canBarehand(f.player))
        f.due()
        f.fish(game)
        assertEquals(1, f.player.inv.count(C.RAW_HARPOONFISH))
        assertEquals(10, f.player.statMap.getXP("stat.strength"))
        f.player.statMap.setCurrentLevel("stat.fishing", 54)
        assertFalse(TemporossFishing.canBarehand(f.player))
    }

    @Test fun `current catch chart reaches guaranteed catches at documented tool thresholds`() {
        val standard = FishingEquipment.HarpoonTier.Standard
        val dragon = FishingEquipment.HarpoonTier.Dragon
        val crystal = FishingEquipment.HarpoonTier.Crystal
        assertEquals(200, TemporossFishing.catchChance(35, standard))
        assertEquals(256, TemporossFishing.catchChance(99, standard))
        assertEquals(233, TemporossFishing.catchChance(61, dragon))
        assertEquals(256, TemporossFishing.catchChance(86, dragon))
        assertEquals(254, TemporossFishing.catchChance(71, crystal))
        assertEquals(256, TemporossFishing.catchChance(73, crystal))
    }

    @Test fun `both dock spirit pools are spawned and belong to this encounter`() {
        val f = Fixture()
        val game = f.start()
        assertEquals(2, game.spiritPools.size)
        assertTrue(game.spiritPools.all { f.manager.ownsNpc(game, it) })
        assertTrue(game.spiritPools.map { it.coords }.toSet().size == 2)
    }

    @Test fun `torrent stuns only the player still interacting with the targeted crate`() {
        val f = Fixture()
        val game = f.start()
        f.quiet(game)
        f.item(0, C.COOKED_HARPOONFISH)
        f.item(1, C.COOKED_HARPOONFISH)
        f.due()
        f.fill(game)
        game.torrentCannon = 0
        game.torrentImpact = 108
        f.tick(108)
        assertTrue(org.rsmod.api.mechanics.stun.Stun.isActionBlocked(f.player))
        assertEquals(108 + C.CANNON_DISABLE_TICKS, game.cannons[0].disabledUntil)
        assertEquals(0, game.cannonFlags[0])
    }

    @Test fun `switching cannon avoids pending torrent stun and preserves other ammunition`() {
        val f = Fixture()
        val game = f.start()
        f.quiet(game)
        f.item(0, C.COOKED_HARPOONFISH)
        f.item(1, C.COOKED_HARPOONFISH)
        f.item(2, C.COOKED_HARPOONFISH)
        f.due()
        f.fill(game, 0)
        f.fill(game, 1)
        game.torrentCannon = 0
        game.torrentImpact = 108
        f.tick(108)
        assertFalse(org.rsmod.api.mechanics.stun.Stun.isActionBlocked(f.player))
        assertEquals(1, game.cannons[0].ammunition.size)
    }

    @Test fun `stopping loading without moving avoids pending torrent stun`() {
        val f = Fixture()
        val game = f.start()
        f.quiet(game)
        f.item(0, C.COOKED_HARPOONFISH)
        f.item(1, C.COOKED_HARPOONFISH)
        f.due()
        f.fill(game)
        f.player.clearInteraction()
        game.torrentCannon = 0
        game.torrentImpact = 108
        f.tick(108)
        assertFalse(org.rsmod.api.mechanics.stun.Stun.isActionBlocked(f.player))
    }

    @Test fun `new special attacks remain suppressed at ten percent energy`() {
        val f = Fixture()
        val game = f.start()
        game.energy = game.energyMax / 10
        game.nextWave = 108
        game.nextLightning = 108
        game.nextTorrent = 108
        f.tick(108)
        assertEquals(-1, game.waveImpact)
        assertEquals(-1, game.torrentImpact)
        assertTrue(game.lightning.isEmpty())
        game.energy++
        f.tick(109)
        assertTrue(game.waveImpact > 109)
        assertTrue(game.torrentImpact > 109)
    }

    @Test fun `a complete vulnerable window restores exactly the original scaled energy`() {
        val f = Fixture()
        val game = f.start()
        f.quiet(game)
        game.phase = TemporossPhase.VULNERABLE
        game.cycle = 1
        game.energy = 0
        for (tick in 108 until 107 + C.VULNERABLE_TICKS) f.tick(tick)
        assertEquals(TemporossPhase.VULNERABLE, game.phase)
        assertTrue(game.energy < game.energyMax)
        f.tick(107 + C.VULNERABLE_TICKS)
        assertEquals(game.energyMax, game.energy)
        assertEquals(TemporossPhase.ENERGY, game.phase)
    }

    @Test fun `bucket take quantities fill only available spaces and cannot remove existing inventory`() {
        val f = Fixture()
        f.start()
        f.fillInventory()
        f.player.inv[26] = null
        f.player.inv[27] = null
        f.supplies(10)
        assertEquals(2, f.player.inv.count(C.BUCKET))
        assertEquals(26, f.player.inv.count(C.HARPOON))
        f.supplies(-1)
        assertEquals(2, f.player.inv.count(C.BUCKET))
    }

    @Test fun `angler equipment bonus applies to encounter fishing XP`() {
        val f = Fixture()
        val game = f.start()
        for ((slot, item) in listOf(0 to "obj.spirit_angler_hat", 4 to "obj.spirit_angler_top", 7 to "obj.spirit_angler_legs", 10 to "obj.spirit_angler_boots")) f.player.worn[slot] = InvObj(item)
        f.item(0, C.COOKED_HARPOONFISH)
        f.due()
        f.fill(game)
        assertEquals(102, f.player.statMap.getXP("stat.fishing"))
        assertEquals(65, game.pointsFor(1L))
    }

    @Test fun `arena actors preserve the captured geometry around all four native crates`() {
        val f = Fixture()
        val game = f.start()
        assertEquals(game.translate(CoordGrid(3043, 2845, 0)), game.boss?.coords)
        assertEquals(setOf(CoordGrid(3046, 2853, 0), CoordGrid(3046, 2843, 0)).map(game::translate).toSet(), game.spiritPools.map { it.coords }.toSet())
        assertEquals(setOf(CoordGrid(3036, 2835, 0), CoordGrid(3037, 2868, 0)).map(game::translate).toSet(), game.fishingSpots.map { it.coords }.toSet())
        val hosts = listOf(CoordGrid(3036, 2852, 0), CoordGrid(3043, 2869, 0), CoordGrid(3058, 2846, 0), CoordGrid(3041, 2834, 0))
        assertEquals(hosts.map(game::translate), game.hosts.map { it.coords })
        for ((index, crate) in game.crateNpcs.withIndex()) {
            val crew = f.npcs.first { it.id == C.CANNONEERS[index].npc.asRSCM() }
            assertEquals(crate.coords.z, crew.coords.z)
            assertEquals(crate.coords.x + if (index < 2) 1 else -1, crew.coords.x)
            assertEquals(C.CANNONEERS[index].facing, crew.respawnDir)
        }
    }

    @Test fun `arena HUD replaces the fade in the native full viewport slot and relies on its native onload`() {
        val f = Fixture()
        f.start()
        assertTrue(f.player.ui.containsOverlay(C.HUD_INTERFACE))
        assertFalse(f.player.ui.containsOverlay("interface.fade_overlay"))
        assertEquals(C.HUD_OVERLAY_TARGET.asRSCM(), f.player.ui.overlays.getComponent(UserInterface(C.HUD_INTERFACE.asRSCM()))?.packed)
        assertEquals(listOf(4071, "component.tempoross_hud:dodger".asRSCM(), "component.tempoross_hud:status".asRSCM()),
            ServerCacheManager.getInterface(437)?.components?.get(0)?.onLoad?.toList())
        assertFalse(f.client.packets.filterIsInstance<RunClientScript>().any { it.id == 4071 })
        f.manager.onPlayerLeave(f.player, checkNotNull(f.manager.gameForPlayer(f.player)).instanceId)
        assertFalse(f.player.ui.containsOverlay(C.HUD_INTERFACE))
    }

    @Test fun `native HUD reports storm against 350 and broken anchors instead of cannon availability`() {
        val f = Fixture()
        val game = f.start()
        game.energy = 130; game.energyMax = 260
        game.essence = 200; game.essenceMax = 250
        game.intensity = 100
        game.brokenAnchors += listOf(2, 1)
        game.cannonFlags.fill(0)
        assertArrayEquals(arrayOf(0, 2035, 130, 200, 260, 250, 350, 0, 1, 0, 1, 0, 0, 0, 0), TemporossHud.arguments(game, 2035))
        game.brokenAnchors.clear()
        game.intensity = 50
        assertEquals(175, TemporossHud.arguments(game, 0)[6])
        assertEquals(listOf(1, 1, 1, 1), TemporossHud.arguments(game, 0).slice(7..10))
    }

    @Test fun `native HUD fire icons follow island and ship fires and clear after dousing`() {
        val f = Fixture()
        val game = f.start()
        for (index in listOf(2, 0, 1, 3)) {
            val coord = game.translate(C.ANCHORS[index].source)
            assertTrue(f.manager.environment.spawnFire(game, coord, 108))
            val expected = listOf(2, 0, 1, 3).map { if (it == index) 1 else 0 }
            assertEquals(expected, TemporossHud.arguments(game, 0).slice(11..14))
            assertTrue(f.manager.environment.douse(game, game.fires.getValue(coord).npc))
            assertEquals(listOf(0, 0, 0, 0), TemporossHud.arguments(game, 0).slice(11..14))
        }
    }

    @Test fun `wave warning advances and recedes through native client scripts once each`() {
        val f = Fixture(randomValue = 99)
        val game = f.start()
        f.quiet(game)
        game.nextWave = 108
        f.tick(108)
        assertEquals(110, game.waveVisualStart)
        assertFalse(game.waveVisualActive)
        f.tick(109)
        f.tick(110)
        assertTrue(game.waveVisualActive)
        assertEquals(1, f.client.scripts(C.WAVE_ADVANCE_SCRIPT).size)
        assertEquals(RunClientScript(C.WAVE_ADVANCE_SCRIPT, listOf(300)).toString(), f.client.scripts(C.WAVE_ADVANCE_SCRIPT).single().toString())
        f.tick(111)
        val impact = 108 + C.WAVE_WARNING_TICKS
        f.tick(impact)
        assertEquals(impact + 2, game.waveVisualRecede)
        f.tick(impact + 2)
        assertFalse(game.waveVisualActive)
        f.tick(impact + 3)
        assertEquals(1, f.client.scripts(C.WAVE_RECEDE_SCRIPT).size)
        assertEquals(RunClientScript(C.WAVE_RECEDE_SCRIPT, listOf(60)).toString(), f.client.scripts(C.WAVE_RECEDE_SCRIPT).single().toString())
    }

    @Test fun `victory cancels pending wave visuals and recedes an already visible wave`() {
        for (advance in listOf(false, true)) {
            val f = Fixture()
            val game = f.start()
            f.quiet(game)
            game.nextWave = 108
            f.tick(108)
            if (advance) f.tick(110)
            game.phase = TemporossPhase.VULNERABLE
            game.essence = 0
            f.tick(if (advance) 111 else 109)
            assertEquals(TemporossPhase.WON, game.phase)
            assertEquals(-1, game.waveVisualStart)
            assertEquals(-1, game.waveVisualRecede)
            assertFalse(game.waveVisualActive)
            assertEquals(if (advance) 1 else 0, f.client.scripts(C.WAVE_RECEDE_SCRIPT).size)
            f.tick(112)
            assertEquals(if (advance) 1 else 0, f.client.scripts(C.WAVE_ADVANCE_SCRIPT).size)
        }
    }

    private class RecordingClient : Client<Any, Any> by NoopClient {
        val packets = mutableListOf<Any>()
        override fun write(message: Any) { packets += message }
        fun scripts(id: Int) = packets.filterIsInstance<RunClientScript>().filter { it.id == id }
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
        val client = RecordingClient()
        val player = Player(client = client).apply {
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
        fun tick(tick: Int) { clock.cycle = tick; player.currentMapClock = tick; manager.tick(tick) }
        fun fill(game: TemporossEncounter, cannon: Int = 0) = invoke(arena, "fillCrate", access(), game.crateNpcs[cannon])
        fun fish(game: TemporossEncounter) = invoke(arena, "attemptFish", access(), game.fishingSpots.first())
        fun supplies(count: Int) = invoke(arena, "takeSupplies", access(), C.BUCKET, "bucket", count)
        fun water() = invoke(arena, "fillWater", access(), bound(C.PUMPS.first(), player.coords))
        fun anchor(game: TemporossEncounter, index: Int) = invoke(arena, "useAnchor", access(), bound(game.anchors.getValue(index)))
        fun douse(npc: org.rsmod.game.entity.Npc) = invoke(arena, "douseFire", access(), npc)
        fun bound(loc: LocInfo) = BoundLocInfo(loc, checkNotNull(ServerCacheManager.getObject(loc.id)))
        fun bound(symbol: String, coord: org.rsmod.map.CoordGrid) = bound(LocInfo(2, coord, LocEntity(symbol.asRSCM(), 10, 0)))
        fun placeAnchor(game: TemporossEncounter, index: Int): LocInfo {
            val config = C.ANCHORS[index]
            val loc = LocInfo(2, game.translate(config.source), LocEntity(config.normal.asRSCM(), 10, if (index == 0) 2 else 0))
            check(locRepo.add(loc, Int.MAX_VALUE))
            game.anchors[index] = loc
            return loc
        }
        fun quiet(game: TemporossEncounter) { game.nextWave = Int.MAX_VALUE; game.nextLightning = Int.MAX_VALUE; game.nextTorrent = Int.MAX_VALUE }
        private fun invoke(target: Any, name: String, vararg args: Any): Any? =
            target.javaClass.declaredMethods.single { it.name == name }.apply { isAccessible = true }.invoke(target, *args)
    }
}
