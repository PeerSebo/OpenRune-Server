package org.rsmod.content.bosses.hespori

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import dev.openrune.types.ItemServerType
import dev.openrune.types.varp.VarpLifetime
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bosses.dsl.boss
import org.rsmod.api.bosses.runtime.*
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.death.*
import org.rsmod.api.death.grave.GraveManager
import org.rsmod.api.farming.FarmingClock
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.music.MusicRepository
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.access.StandardNpcAccessContext
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.processor.StandardNpcHitProcessor
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.events.PlayerMoveClick
import org.rsmod.api.player.events.PlayerNpcClick
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.player.hook.GroundItemDropResolver
import org.rsmod.api.player.music.MusicPlayer
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.*
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.*
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.*
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitType
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import sun.misc.Unsafe

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HesporiTest {
    @BeforeAll fun cache() {
        ServerCacheManager.init(241).close()
        with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
            ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
        }
    }

    @Test fun `growth uses three global 640 minute cycles at the existing Farming speed`() {
        val cycle = HesporiCrop.cycleMinutes
        val anchor = 100L * cycle
        assertEquals(0, HesporiCrop.growth(anchor + cycle - 1, anchor + cycle - 1))
        assertEquals(1, HesporiCrop.growth(anchor + cycle - 1, anchor + cycle))
        assertEquals(2, HesporiCrop.growth(anchor, anchor + cycle * 2))
        assertEquals(3, HesporiCrop.growth(anchor, anchor + cycle * 3))
        assertEquals(3, HesporiCrop.growth(anchor, anchor + cycle * 30))
        assertEquals(0, HesporiCrop.growth(anchor, anchor - 10))
    }

    @Test fun `plant growth and pending harvest survive persistence and can only be claimed once`() {
        val player = Player()
        assertFalse(HesporiCrop.plant(player))
        player.hesporiRakeProgress = 3
        val planted = FarmingClock.currentMinute() - 4L * HesporiCrop.cycleMinutes
        assertTrue(HesporiCrop.plant(player, planted))
        assertFalse(HesporiCrop.plant(player, planted))
        val restored = savedCopy(player)
        assertEquals(7, HesporiCrop.stage(restored))
        assertTrue(HesporiCrop.defeat(restored))
        assertFalse(HesporiCrop.defeat(restored))
        val afterRestart = savedCopy(restored)
        assertEquals(8, HesporiCrop.stage(afterRestart))
        assertTrue(HesporiCrop.claimHarvest(afterRestart))
        assertFalse(HesporiCrop.claimHarvest(afterRestart))
        assertEquals(3, HesporiCrop.stage(afterRestart))
        assertTrue(HesporiCrop.plant(afterRestart))
    }

    @Test fun `all three flower waves block boss damage and prevent a huge hit skipping a wave`() {
        val life = HesporiLife()
        var hp = 300
        repeat(3) { wave ->
            life.openWave()
            assertEquals(0, life.damage(hp, 9999))
            for (flower in 0..2) { assertTrue(life.closeFlower(flower)); assertTrue(life.immune) }
            assertTrue(life.closeFlower(3))
            assertFalse(life.closeFlower(3))
            assertFalse(life.immune)
            hp -= life.damage(hp, 9999)
            assertEquals(if (wave == 0) 200 else if (wave == 1) 100 else 0, hp)
            assertEquals(wave < 2, life.needsWave(hp))
        }
    }

    @Test fun `exactly five deliberate clicks release a bound player with no passive progress`() {
        val f = Fixture()
        val room = f.enterReady()
        assertTrue(f.garden.start(f.player))
        room.life.root(100)
        f.player.frozen = true
        val stationary = PlayerMoveClick(f.player, f.player.coords)
        f.garden.move(stationary)
        assertTrue(stationary.consumed)
        assertEquals(0, room.life.rootClicks)
        repeat(4) {
            val click = PlayerMoveClick(f.player, f.player.coords.translate(1, 0))
            f.garden.move(click)
            assertTrue(click.consumed)
            assertTrue(f.player.frozen)
        }
        val other = PlayerMoveClick(Player(), CoordGrid(1235, 3728))
        f.garden.move(other)
        assertFalse(other.consumed)
        f.garden.move(PlayerMoveClick(f.player, f.player.coords.translate(1, 0)))
        assertFalse(f.player.frozen)
        assertNull(room.life.rootDeadline)
    }

    @Test fun `isolated garden creates four independently hittable flowers and resets after escape`() {
        val f = Fixture()
        val room = f.enterReady()
        assertTrue(f.garden.start(f.player))
        val boss = checkNotNull(room.boss)
        assertEquals(4, room.flowers.size)
        assertEquals(0, f.garden.incoming(boss, 90, f.player))
        for (flower in room.flowers.keys.toList()) {
            assertEquals(0, f.garden.incoming(flower, 1, Player()))
            assertEquals(10, f.garden.incoming(flower, 0, f.player))
        }
        assertEquals(100, f.garden.incoming(boss, 9999, f.player))
        f.garden.cleanup(f.player, returnOutside = true)
        assertEquals(HesporiGarden.EXIT, f.player.coords)
        assertNull(f.garden.garden(f.player))
        assertFalse(boss.isSlotAssigned)
        assertEquals(7, HesporiCrop.stage(f.player))
        val retry = f.enterReady()
        assertTrue(f.garden.start(f.player))
        assertEquals(1, retry.life.wave)
        assertEquals(300, retry.boss!!.hitpoints)
    }

    @Test fun `attacking the owned Hespori counts towards roots but other NPC clicks do not`() {
        val f = Fixture()
        val room = f.enterReady()
        f.garden.start(f.player)
        room.life.root(100)
        f.player.frozen = true
        val wrong = PlayerNpcClick(f.player, room.flowers.keys.first(), InteractionOp.Op2)
        f.garden.npcClick(wrong)
        assertFalse(wrong.consumed)
        repeat(5) {
            val click = PlayerNpcClick(f.player, room.boss!!, InteractionOp.Op2)
            f.garden.npcClick(click)
            assertTrue(click.consumed)
        }
        assertNull(room.life.rootDeadline)
        assertFalse(f.player.frozen)
    }

    @Test fun `already launched hits cannot bypass flowers that reopen before impact`() {
        val f = Fixture()
        val room = f.enterReady()
        f.garden.start(f.player)
        val boss = room.boss!!
        for (flower in room.flowers.keys.toList()) f.land(flower, f.launch(flower, 1))
        assertFalse(room.life.immune)
        val first = f.launch(boss, 150)
        val second = f.launch(boss, 999)
        f.land(boss, first)
        assertEquals(200, boss.hitpoints)
        assertEquals(2, room.life.wave)
        f.land(boss, second)
        assertEquals(200, boss.hitpoints)
        assertTrue(room.life.immune)
        assertFalse("queue.death" in boss.queueList)
    }

    @Test fun `launching an arrow does not close a flower until the actual NPC hit processor runs`() {
        val f = Fixture()
        val room = f.enterReady()
        f.garden.start(f.player)
        val flower = room.flowers.keys.first()
        val arrow = f.launch(flower, 1)
        assertEquals(10, flower.hitpoints)
        f.land(flower, arrow)
        assertEquals(0, flower.hitpoints)
        assertTrue("queue.death" in flower.queueList)
        assertTrue(room.life.immune)
    }

    @Test fun `victory survives leaving and restarting without spawning or duplicating a new fight`() {
        val f = Fixture()
        val room = f.enterReady()
        assertTrue(f.garden.start(f.player))
        f.garden.defeated(room.boss!!)
        f.garden.defeated(room.boss!!)
        assertEquals(1, f.player.vars["varp.total_hespori_kills"])
        f.garden.cleanup(f.player, returnOutside = true)
        assertEquals(8, HesporiCrop.stage(f.player))
        val copy = savedCopy(f.player)
        assertTrue(HesporiCrop.claimHarvest(copy))
        assertFalse(HesporiCrop.claimHarvest(copy))
    }

    @Test fun `death losses go to persistent Arno storage before region cleanup and retain item variables`() {
        val f = Fixture()
        f.enterReady()
        val reclaim = HesporiReclaim(f.garden)
        f.player.hesporiReclaimPaid = true
        val losses = listOf(InvObj("obj.coins", 98765, 12), InvObj("obj.hespori_seed", 2))
        f.player.inv[0] = losses[0]
        f.player.worn[0] = losses[1]
        val deathDrops = f.deathDrops(reclaim)
        val result = PlayerDeathDrops.DeathDropResult(emptyList(), emptyList(), losses, emptyList(), 0)
        val handling = PlayerDeathHandling(3, f.player, 6000, 100, true, UntradeableHandling.DROP, true)
        assertFalse(deathDrops.applyDrops(f.player, result, handling, HesporiGarden.EXIT))
        assertTrue(f.player.inv.all { it == null })
        assertTrue(f.player.worn.all { it == null })
        assertEquals(losses, HesporiReclaim.decode(f.player.attr[HesporiReclaim.ITEMS]))
        assertFalse(f.player.hesporiReclaimPaid)
        reclaim.cleanup(f.player)
        assertFalse(f.player.hesporiInArena)
        val restored = savedCopy(f.player)
        assertEquals(losses, HesporiReclaim.decode(restored.attr[HesporiReclaim.ITEMS]))
        assertTrue(reclaim.hasItems(restored))
        assertNull(reclaim.destination(Player()))
    }

    @Test fun `failed storage leaves original carried items intact`() {
        val f = Fixture()
        val coins = InvObj("obj.coins", 100)
        f.player.inv[0] = coins
        val drops = f.deathDrops(PlayerDeathItemStorageHook { _, _ -> error("Storage unavailable") })
        val result = PlayerDeathDrops.DeathDropResult(emptyList(), emptyList(), listOf(coins), emptyList(), 0)
        val handling = PlayerDeathHandling(3, f.player, 6000, 100, true, UntradeableHandling.DROP, true)
        assertThrows(IllegalStateException::class.java) { drops.applyDrops(f.player, result, handling, HesporiGarden.EXIT) }
        assertEquals(coins, f.player.inv[0])
    }

    @Test fun `partial paid reclaim retains unclaimed items and cannot duplicate them on repeated requests`() {
        val f = Fixture()
        val reclaim = HesporiReclaim(f.garden)
        val items = listOf(InvObj("obj.coins", 500), InvObj("obj.hespori_seed", 2))
        f.player.attr[HesporiReclaim.ITEMS] = HesporiReclaim.encode(items)
        for (slot in f.player.inv.indices) f.player.inv[slot] = InvObj("obj.weeds", 1)
        f.player.inv[0] = null
        reclaim.withdraw(f.access())
        assertNull(f.player.inv[0], "Unpaid retrieval cannot transfer items")
        f.player.hesporiReclaimPaid = true
        reclaim.withdraw(f.access())
        assertEquals(items[0], f.player.inv[0])
        assertEquals(listOf(items[1]), HesporiReclaim.decode(f.player.attr[HesporiReclaim.ITEMS]))
        assertTrue(f.player.hesporiReclaimPaid)
        assertTrue(savedCopy(f.player).hesporiReclaimPaid)
        f.player.inv[1] = null
        reclaim.withdraw(f.access())
        assertEquals(items[1], f.player.inv[1])
        assertFalse(reclaim.hasItems(f.player))
        assertFalse(f.player.hesporiReclaimPaid)
        reclaim.withdraw(f.access())
        assertEquals(500, f.player.inv.count("obj.coins"))
        assertEquals(2, f.player.inv.count("obj.hespori_seed"))
    }

    @Test fun `a normal NPC without an impact listener takes its original damage`() {
        val f = Fixture()
        val other = f.spawnOther()
        val hp = other.hitpoints
        f.land(other, f.launch(other, 5))
        assertEquals(hp - 5, other.hitpoints)
    }

    @Test fun `stale instance login returns outside while preserving crop and stored belongings`() {
        val f = Fixture()
        f.player.hesporiInArena = true
        f.player.hesporiPlantedMinute = (FarmingClock.currentMinute() - 999).toInt()
        f.player.coords = CoordGrid(6400, 6400)
        f.garden.login(f.player)
        assertEquals(HesporiGarden.EXIT, f.player.coords)
        assertEquals(7, HesporiCrop.stage(f.player))
        assertFalse(f.player.hesporiInArena)
    }

    @Test fun `legacy arena login migrates before relocation and preserves paid reclaim`() {
        val f = Fixture()
        f.player.attr.putAllFromPersistence(mapOf(
            "hespori_in_arena" to true,
            "hespori_planted_minute" to (FarmingClock.currentMinute() - 999).toInt(),
            "hespori_reclaim_paid" to true,
            "hespori_reclaim_items" to HesporiReclaim.encode(listOf(InvObj("obj.coins", 100))),
        ))
        f.player.coords = CoordGrid(6400, 6400)
        f.garden.login(f.player)
        assertEquals(HesporiGarden.EXIT, f.player.coords)
        assertEquals(7, HesporiCrop.stage(f.player))
        assertFalse(f.player.hesporiInArena)
        assertTrue(f.player.hesporiReclaimPaid)
        assertEquals(100, HesporiReclaim.decode(f.player.attr[HesporiReclaim.ITEMS]).single().count)
    }

    @Test fun `logout releases arena state and restores outside without losing the crop`() {
        val f = Fixture()
        f.enterReady()
        assertTrue(f.player.hesporiInArena)
        f.garden.cleanup(f.player, returnOutside = true)
        assertFalse(f.player.hesporiInArena)
        assertEquals(0, f.player.vars["varbit.player_in_instance"])
        assertEquals(HesporiGarden.EXIT, f.player.coords)
        assertEquals(7, HesporiCrop.stage(f.player))
        assertNull(f.garden.garden(f.player))
    }

    @Test fun `orphaned arena cleanup is safe while unrelated instance state is preserved`() {
        val f = Fixture()
        f.player.hesporiInArena = true
        f.player.coords = CoordGrid(6400, 6400)
        f.garden.cleanup(f.player, returnOutside = true)
        assertFalse(f.player.hesporiInArena)
        assertEquals(HesporiGarden.EXIT, f.player.coords)
        org.rsmod.api.player.vars.VarPlayerIntMapSetter.set(f.player, "varbit.player_in_instance", 1)
        f.garden.cleanup(f.player)
        assertEquals(1, f.player.vars["varbit.player_in_instance"])
    }

    @OptIn(InternalApi::class)
    private class Fixture {
        val clock = MapClock(100)
        private val events = EventBus()
        private val flags = CollisionFlagMap()
        private val players = PlayerList()
        private val npcs = NpcList()
        private val npcRegistry = NpcRegistry(npcs, flags, events)
        private val storage = LocZoneStorage()
        private val updates = ZoneUpdateMap()
        private val normal = LocRegistryNormal(updates, flags, storage)
        private val regions = RegionRegistry(RegionListSmall(), RegionListLarge(), normal, flags, storage, npcRegistry,
            ControllerRegistry(clock, ControllerList()), ZonePlayerActivityBitSet())
        private val locRegistry = LocRegistry(storage, normal, LocRegistryRegion(updates, flags, storage, regions))
        private val encounters = EncounterRegistry(clock).apply { register(HesporiGarden.BOSS.asRSCM(), listOf(HesporiScript.SPEC), HesporiScript.SPEC) }
        private val npcRepo = NpcRepository(clock, npcRegistry, npcs)
        private val worldRepo = WorldRepository(updates)
        private val deps = BossDeps(DefaultGameRandom(1), unused<AccuracyFormulae>(), worldRepo, npcRepo, players,
            clock, WorldQueueList(), flags, encounters, BossExtensionRegistry(), NoopPlayerHitModifier)
        private val music = MusicPlayer::class.java
            .getDeclaredConstructor(GameRandom::class.java, MusicRepository::class.java)
            .apply { isAccessible = true }
            .newInstance(DefaultGameRandom(1), MusicRepository(DefaultGameRandom(1)).apply { load() })
        val garden = HesporiGarden(deps, RegionRepository(regions), LocRepository(clock, locRegistry, regions), AiPlayerInteractions(events, players), music)
        val player = Player().apply {
            slotId = 1
            uuid = 123L
            assignUid()
            coords = HesporiGarden.EXIT
            currentMapClock = clock.cycle
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
        }
        init {
            players[1] = player
            for (level in 0..3) for (x in 1216 until 1280 step 8) for (z in 10048 until 10112 step 8) flags.allocateIfAbsent(x, z, level)
            flags.allocateIfAbsent(HesporiGarden.EXIT.x, HesporiGarden.EXIT.z, 0)
            for (id in listOf(HesporiGarden.BOSS.asRSCM(), HesporiGarden.ACTIVE.asRSCM())) {
                events.subscribeKeyed(NpcHitEvents.BeforeImpact::class.java, id.toLong()) {
                    damage = garden.incoming(npc, damage, hit.resolvePlayerSource(players))
                }
            }
            events.subscribeKeyed(NpcHitEvents.Impact::class.java, HesporiGarden.BOSS.asRSCM().toLong()) { garden.afterHit(npc) }
        }
        fun launch(npc: Npc, damage: Int): Hit = npc.queueHit(player, 5, HitType.Ranged, damage, NpcHitModifier {})
        fun land(npc: Npc, hit: Hit) {
            val processor = StandardNpcHitProcessor(players, events, emptySet(), worldRepo)
            val access = StandardNpcAccess(npc, GameCoroutine(), StandardNpcAccessContext({ DefaultGameRandom(1) }, { NpcHitModifier {} }, { processor }))
            with(processor) { access.process(hit) }
        }
        fun deathDrops(storageHook: PlayerDeathItemStorageHook) = PlayerDeathDrops(clock, ObjRepository(clock, ObjRegistry(updates)),
            object : MarketPrices { override fun get(type: ItemServerType): Int? = null }, GroundItemDropResolver(emptySet()),
            GraveManager(clock, npcRepo, emptySet(), emptySet()), setOf(storageHook))
        fun spawnOther(): Npc = Npc("npc.scorpia", HesporiGarden.EXIT).also { npcRepo.add(it, Int.MAX_VALUE) }
        fun access() = ProtectedAccess(player, GameCoroutine(), ProtectedAccessContext(
            getRandom = { DefaultGameRandom(1) }, getEventBus = { events }, getNpcList = { npcs }, getPlayerList = { players },
            getCollision = { flags }, getAreaChecker = { error("unused") }, getAlignment = { error("unused") },
            getLocInteractions = { error("unused") }, getNpcInteractions = { error("unused") }, getPlayerInteractions = { error("unused") },
            getHeldInteractions = { error("unused") }, getWornInteractions = { error("unused") }, getMusicPlayer = { error("unused") },
            getMarketPrices = { error("unused") }, getInstantHitProcessor = { error("unused") }, getTeleportValidator = { error("unused") },
            getWorldRepo = { worldRepo }, getHitModifier = { NoopPlayerHitModifier }))
        fun enterReady(): HesporiGarden.Garden {
            if (HesporiCrop.stage(player) < 7) {
                player.hesporiRakeProgress = 3
                HesporiCrop.plant(player, FarmingClock.currentMinute() - 999)
            }
            val room = checkNotNull(garden.create(player))
            player.coords = room.at(HesporiGarden.ARRIVAL)
            garden.entered(player)
            return room
        }
    }

    companion object {
        private fun savedCopy(player: Player): Player = Player().apply {
            attr.putAllFromPersistence(player.attr.toPersistentMap())
            vars.backing.putAll(player.vars.backing.filterKeys {
                ServerCacheManager.getVarp(it)?.scope == VarpLifetime.Perm
            })
        }

        private inline fun <reified T> unused(): T {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
            return (field.get(null) as Unsafe).allocateInstance(T::class.java) as T
        }
    }
}
