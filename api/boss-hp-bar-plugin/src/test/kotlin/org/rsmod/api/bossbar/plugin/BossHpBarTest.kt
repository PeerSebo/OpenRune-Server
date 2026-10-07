package org.rsmod.api.bossbar.plugin

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import net.rsprot.protocol.game.outgoing.interfaces.IfSetHide
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import net.rsprot.protocol.game.outgoing.varp.VarpLarge
import net.rsprot.protocol.game.outgoing.varp.VarpSmall
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.config.refs.params
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.instances.*
import org.rsmod.api.instances.region.InstanceAreaResolver
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.access.StandardNpcAccessContext
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.processor.StandardNpcHitProcessor
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.registry.controller.ControllerRegistry
import org.rsmod.api.registry.loc.LocRegistryNormal
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onEvent
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.*
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.Inventory
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.region.RegionListLarge
import org.rsmod.game.region.RegionListSmall
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@OptIn(InternalApi::class)
class BossHpBarTest {
    @Test fun `Brutus misses open the native widget before impact for every combat style`() {
        for (style in listOf(HitType.Melee, HitType.Ranged, HitType.Magic)) {
            val f = Fixture()
            val boss = f.npc("cowboss")
            val hit = f.launch(boss, 0, style)
            val packet = f.client(f.player).messages.filterIsInstance<RunClientScript>().single()
            assertEquals(2376, packet.id)
            assertEquals(nativeOpenArguments, packet.values.size)
            assertEquals("i".repeat(19), packet.types.concatToString())
            for (component in packet.values.map { it as Int }) {
                assertEquals("interface.hpbar_hud".asRSCM(), component ushr 16)
                assertEquals(component, ServerCacheManager.fromComponent(component).packed)
            }
            assertEquals(nativeOpenComponents, packet.values)
            assertEquals(boss.id, f.player.vars["varp.hpbar_hud_npc"])
            assertEquals(boss.baseHitpointsLvl, f.player.vars["varbit.hpbar_hud_basehp"])
            assertEquals(boss.hitpoints, f.player.vars["varbit.hpbar_hud_hp"])
            assertEquals(1, f.player.vars["varbit.hpbar_hud_boss"])
            assertTrue(f.client(f.player).messages.any { it is VarpLarge || it is VarpSmall })
            f.land(boss, hit)
            f.tick()
            assertEquals(1, f.client(f.player).messages.filterIsInstance<RunClientScript>().size)
        }
    }

    @Test fun `all implemented bosses and combat forms have explicit metadata but helpers do not`() {
        for (symbol in bosses) {
            val type = checkNotNull(ServerCacheManager.getNpc("npc.$symbol".asRSCM()))
            assertTrue((type.paramOrNull(params.boss_hp_bar_mode) ?: 0) in 1..2, symbol)
            val f = Fixture()
            val boss = f.npc(symbol)
            f.launch(boss, 0)
            assertEquals(boss.visType.id, f.player.vars["varp.hpbar_hud_npc"], symbol)
            assertEquals(1, f.client(f.player).messages.filterIsInstance<RunClientScript>().size, symbol)
        }
        for (symbol in nonBosses) {
            val f = Fixture()
            val npc = f.npc(symbol)
            f.launch(npc, 0)
            assertTrue(f.client(f.player).messages.filterIsInstance<RunClientScript>().isEmpty(), symbol)
        }
    }

    @Test fun `hits and healing update every existing viewer without reopening the widget`() {
        val f = Fixture()
        val second = f.player(2)
        val boss = f.npc("cowboss")
        f.launch(boss, 0)
        f.launch(boss, 0, player = second)
        f.land(boss, f.launch(boss, 13))
        f.tick()
        for (player in listOf(f.player, second)) {
            assertEquals(boss.hitpoints, player.vars["varbit.hpbar_hud_hp"])
            assertEquals(1, f.client(player).messages.filterIsInstance<RunClientScript>().size)
        }
        boss.hitpoints += 7
        f.tick()
        assertEquals(boss.hitpoints, second.vars["varbit.hpbar_hud_hp"])
    }

    @Test fun `Kraken uses the surfaced native name and closes on submerging`() {
        val f = Fixture()
        val boss = f.npc("slayer_kraken_boss_whirlpool")
        f.launch(boss, 0)
        assertTrue(f.client(f.player).messages.isEmpty())
        boss.transmog(checkNotNull(ServerCacheManager.getNpc("npc.slayer_kraken_boss".asRSCM())), Int.MAX_VALUE)
        f.launch(boss, 0)
        assertEquals("npc.slayer_kraken_boss".asRSCM(), f.player.vars["varp.hpbar_hud_npc"])
        boss.resetTransmog()
        f.tick()
        f.assertClosed()
    }

    @Test fun `phase changes synchronize the visual type and restored health`() {
        val f = Fixture()
        val boss = f.npc("kalphite_queen")
        f.launch(boss, 0)
        boss.transmog(checkNotNull(ServerCacheManager.getNpc("npc.kalphite_flyingqueen".asRSCM())), Int.MAX_VALUE)
        boss.hitpoints = boss.baseHitpointsLvl
        f.tick()
        assertEquals("npc.kalphite_flyingqueen".asRSCM(), f.player.vars["varp.hpbar_hud_npc"])
        assertEquals(boss.hitpoints, f.player.vars["varbit.hpbar_hud_hp"])
    }

    @Test fun `deleting an old target never hides the replacement boss`() {
        val f = Fixture()
        val first = f.npc("cowboss")
        val second = f.npc("king_dragon")
        f.launch(first, 0)
        f.launch(second, 0)
        f.events.publish(NpcStateEvents.Delete(first))
        f.tick()
        assertEquals(second.id, f.player.vars["varp.hpbar_hud_npc"])
        assertTrue(f.client(f.player).messages.filterIsInstance<IfSetHide>().isEmpty())
    }

    @Test fun `a delayed projectile from the previous target cannot replace the selected boss`() {
        val f = Fixture()
        val first = f.npc("cowboss")
        val second = f.npc("king_dragon")
        val delayed = f.launch(first, 10, HitType.Magic)
        f.launch(second, 0)
        f.land(first, delayed)
        f.tick()
        assertEquals(second.id, f.player.vars["varp.hpbar_hud_npc"])
        assertEquals(2, f.client(f.player).messages.filterIsInstance<RunClientScript>().size)
    }

    @Test fun `unchanged health never resends vars or scripts on idle ticks`() {
        val f = Fixture()
        val boss = f.npc("cowboss")
        f.launch(boss, 0)
        val messages = f.client(f.player).messages.toList()
        f.tick(5)
        assertEquals(messages, f.client(f.player).messages)
    }

    @Test fun `boss specific hit modifiers coexist with the HUD in either startup order`() {
        for (modifierFirst in listOf(true, false)) {
            val f = Fixture(modifierFirst)
            val boss = f.npc("cowboss")
            val hit = f.launch(boss, 10)
            assertEquals(7, hit.damage)
            assertEquals(boss.id, f.player.vars["varp.hpbar_hud_npc"])
            f.land(boss, hit)
            f.tick()
            assertEquals(boss.baseHitpointsLvl - 7, f.player.vars["varbit.hpbar_hud_hp"])
        }
    }

    @Test fun `same type different actors cannot clear each others health bars`() {
        val f = Fixture()
        val first = f.npc("cowboss")
        val second = f.npc("cowboss")
        f.launch(first, 0)
        f.launch(second, 0)
        f.events.publish(NpcStateEvents.Delete(first))
        f.tick()
        assertEquals(second.id, f.player.vars["varp.hpbar_hud_npc"])
        assertTrue(f.client(f.player).messages.filterIsInstance<IfSetHide>().isEmpty())
    }

    @Test fun `death fades then closes the widget for all participants`() {
        val f = Fixture()
        val second = f.player(2)
        val boss = f.npc("cowboss")
        f.launch(boss, 0)
        f.launch(boss, 0, player = second)
        f.land(boss, f.launch(boss, boss.hitpoints))
        f.tick()
        assertEquals(boss.id, f.player.vars["varp.hpbar_hud_npc"])
        f.tick(10)
        assertTrue(f.client(f.player).messages.filterIsInstance<RunClientScript>().any { it.id == 2889 })
        assertTrue(f.client(second).messages.filterIsInstance<RunClientScript>().any { it.id == 2889 })
        f.tick(4)
        f.assertClosed()
        f.assertClosed(second)
    }

    @Test fun `repeated misses keep combat visible and expire exactly ten idle ticks later`() {
        val f = Fixture()
        val boss = f.npc("cowboss")
        f.launch(boss, 0)
        f.tick(9)
        f.launch(boss, 0)
        f.tick(9)
        assertEquals(boss.id, f.player.vars["varp.hpbar_hud_npc"])
        f.tick()
        f.assertClosed()
    }

    @Test fun `departing changing plane and hiding or deleting a boss close immediately`() {
        val changes = listOf<(Fixture, Npc) -> Unit>(
            { f, _ -> f.player.coords = CoordGrid(3200, 3200) },
            { f, _ -> f.player.coords = f.player.coords.copy(level = 1) },
            { _, npc -> npc.hidden = true },
            { f, npc -> f.events.publish(NpcStateEvents.Delete(npc)) },
            { f, _ -> f.player.statMap.setCurrentLevel("stat.hitpoints", 0) },
        )
        for (change in changes) {
            val f = Fixture()
            val boss = f.npc("cowboss")
            f.launch(boss, 0)
            change(f, boss)
            f.tick()
            f.assertClosed()
        }
    }

    @Test fun `logout and login cannot retain a saved boss target`() {
        val f = Fixture()
        val boss = f.npc("cowboss")
        f.launch(boss, 0)
        f.events.publish(SessionStateEvent.Logout(f.player))
        f.assertClosed()
        VarPlayerIntMapSetter.set(f.player, "varp.hpbar_hud_npc", boss.id)
        f.events.publish(SessionStateEvent.Login(f.player))
        f.assertClosed()
    }

    @Test fun `instance entry respawn and leave own the persistent Scurrius widget`() {
        val f = Fixture()
        val session = f.enterBoss("rat_boss_instance")
        val boss = f.manager.npcsForInstance(session.id).single()
        assertEquals(boss.id, f.player.vars["varp.hpbar_hud_npc"])
        f.tick(20)
        assertEquals(boss.id, f.player.vars["varp.hpbar_hud_npc"])
        boss.hitpoints = 0
        f.tick(15)
        f.assertClosed()
        boss.hitpoints = boss.baseHitpointsLvl
        f.events.publish(NpcStateEvents.Respawn(boss))
        assertEquals(boss.id, f.player.vars["varp.hpbar_hud_npc"])
        f.manager.leave(f.player, session, f.clock.cycle)
        f.assertClosed()
    }

    @Test fun `an attack only boss stays visible through helpers in its instance but not before combat`() {
        val f = Fixture()
        val session = f.enterBoss("hespori")
        val boss = f.manager.npcsForInstance(session.id).single()
        assertTrue(f.client(f.player).messages.filterIsInstance<RunClientScript>().isEmpty())
        f.launch(boss, 0)
        val helper = f.npc("hespori_healer_active")
        repeat(4) {
            f.launch(helper, 0)
            f.tick(5)
        }
        assertEquals(boss.id, f.player.vars["varp.hpbar_hud_npc"])
        assertEquals(1, f.client(f.player).messages.filterIsInstance<RunClientScript>().size)
        f.manager.leave(f.player, session, f.clock.cycle)
        f.assertClosed()
    }

    @Test fun `native disable preference suppresses opening and closes an existing widget`() {
        val f = Fixture()
        val boss = f.npc("cowboss")
        VarPlayerIntMapSetter.set(f.player, "varbit.hpbar_hud_boss_disabled", 1)
        f.launch(boss, 0)
        assertTrue(f.client(f.player).messages.filterIsInstance<RunClientScript>().isEmpty())
        VarPlayerIntMapSetter.set(f.player, "varbit.hpbar_hud_boss_disabled", 0)
        f.launch(boss, 0)
        VarPlayerIntMapSetter.set(f.player, "varbit.hpbar_hud_boss_disabled", 1)
        f.tick()
        f.assertClosed()
    }

    private class Fixture(keyedModifierFirst: Boolean? = null) {
        val clock = MapClock(100)
        val events = EventBus()
        private val flags = CollisionFlagMap()
        private val players = PlayerList()
        private val npcs = NpcList()
        private val npcRegistry = NpcRegistry(npcs, flags, events)
        private val storage = LocZoneStorage()
        private val updates = ZoneUpdateMap()
        private val normal = LocRegistryNormal(updates, flags, storage)
        private val regions = RegionRegistry(RegionListSmall(), RegionListLarge(), normal, flags, storage, npcRegistry,
            ControllerRegistry(clock, ControllerList()), ZonePlayerActivityBitSet())
        private val npcRepo = NpcRepository(clock, npcRegistry, npcs)
        private val worldRepo = WorldRepository(updates)
        val manager = InstanceManager(RegionRepository(regions), npcRepo, players, events, InstanceAreaResolver(), clock, flags)
        private val modifier = StandardNpcHitModifier(events, players)
        private val processor = StandardNpcHitProcessor(players, events, emptySet(), worldRepo)
        val player = player(1)

        init {
            val context = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            fun registerModifier() {
                context.onEvent<NpcHitEvents.Modify>("npc.cowboss".asRSCM()) { hit.damage = 7 }
            }
            if (keyedModifierFirst == true) registerModifier()
            with(BossHpBarScript(manager, players, clock)) { context.startup() }
            if (keyedModifierFirst == false) registerModifier()
        }

        fun player(slot: Int): Player = Player().apply {
            slotId = slot
            uuid = slot.toLong()
            assignUid()
            coords = CoordGrid(3260, 3288)
            currentMapClock = clock.cycle
            processedMapClock = clock.cycle
            client = RecordingClient()
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
            statMap.setBaseLevel("stat.hitpoints", 99)
            statMap.setCurrentLevel("stat.hitpoints", 99)
            players[slot] = this
        }

        fun npc(symbol: String): Npc = Npc("npc.$symbol", player.coords.translate(1, 0)).also {
            npcRepo.add(it, Int.MAX_VALUE)
        }

        fun enterBoss(symbol: String): InstanceSession {
            val entry = CoordGrid(3298, 9867)
            val spec = InstanceSpec(
                fee = 0, maxPlayers = 1, reclaimTicks = 0, graceTicks = 0, destroyWhenEmpty = true,
                settingsRowId = "dbrow.instance_scurrius".asRSCM(),
                area = InstanceArea.copyRegions(regionIds = listOf(13210),
                    enterCoord = RegionLocal(entry.level, entry.mx, entry.mz, entry.lx, entry.lz),
                    exitCoord = player.coords,
                    npcSpawns = listOf(InstanceNpc("npc.$symbol", CoordGrid(3299, 9867)))),
            )
            val result = manager.create(player, "boss-hud-test", spec, InstanceAccess.Private, clock.cycle) as InstanceManager.Result.Created
            val boss = manager.npcsForInstance(result.session.id).single()
            player.coords = boss.coords.translate(-1, 0)
            manager.finalizeEntry(player, result.session, clock.cycle)
            return result.session
        }

        fun launch(npc: Npc, damage: Int, style: HitType = HitType.Melee, player: Player = this.player): Hit =
            npc.queueHit(player, 5, style, damage, modifier)

        fun land(npc: Npc, hit: Hit) {
            val access = StandardNpcAccess(npc, GameCoroutine(), StandardNpcAccessContext({ DefaultGameRandom(1) }, { modifier }, { processor }))
            with(processor) { access.process(hit) }
        }

        fun tick(count: Int = 1) {
            repeat(count) {
                clock.cycle++
                events.publish(GameLifecycle.LateCycle)
            }
        }

        fun client(player: Player): RecordingClient = player.client as RecordingClient

        fun assertClosed(player: Player = this.player) {
            assertEquals(-1, player.vars["varp.hpbar_hud_npc"])
            assertEquals(0, player.vars["varbit.hpbar_hud_boss"])
            assertEquals(0, player.vars["varbit.hpbar_hud_hp"])
            assertEquals(0, player.vars["varbit.hpbar_hud_basehp"])
            assertTrue(client(player).messages.filterIsInstance<IfSetHide>().any {
                it.combinedId == "component.hpbar_hud:hp".asRSCM() && it.hidden
            })
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
        private var nativeOpenArguments = 0
        private lateinit var nativeOpenComponents: List<Any>

        @JvmStatic @BeforeAll fun cache() {
            val cache = ServerCacheManager.init(241)
            try {
                val raw = checkNotNull(cache.data(12, 2376, 0))
                val buffer = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN)
                val header = raw.size - 2 - (buffer.getShort(raw.size - 2).toInt() and 65535) - 16
                nativeOpenArguments = buffer.getShort(header + 10).toInt() and 65535
                val native = checkNotNull(ServerCacheManager.getInterface("interface.hpbar_hud".asRSCM()))
                val onLoad = checkNotNull(native.components[0]?.onLoad).toList()
                assertEquals(2099, onLoad.first())
                nativeOpenComponents = onLoad.drop(1)
            } finally {
                cache.close()
            }
        }

        private val bosses = listOf(
            "cowboss", "king_dragon", "rat_boss_normal", "rat_boss_instance",
            "godwars_bandos_avatar", "godwars_armadyl_avatar", "godwars_saradomin_avatar", "godwars_zamorak_avatar",
            "mole_giant", "kalphite_queen", "kalphite_flyingqueen", "hillgiant_boss", "gb_mossgiant",
            "slayer_kraken_boss", "cata_boss", "hespori", "smoke_devil_boss", "sarachnis",
            "callisto", "callisto_singles", "venenatis", "venenatis_singles", "scorpia",
            "chaoselemental", "crazy_archaeologist", "chaos_fanatic", "fossil_crazy_archaeologist",
            "elvarg", "melzar_the_mad", "vetion", "vetion_2", "vetion_single", "vetion_2_single",
        )
        private val nonBosses = listOf(
            "cowboss_mapped", "cow", "rat_boss_giant_rat", "godwars_sergeant_goblin1", "scorpia_minion",
            "slayer_kraken_boss_whirlpool", "slayer_kraken_boss_tentacle", "slayer_kraken_boss_tentacle_whirlpool",
            "hespori_healer_active", "gb_growthling", "cata_boss_minion", "vetion_hellhound_jnr",
        )
    }
}
