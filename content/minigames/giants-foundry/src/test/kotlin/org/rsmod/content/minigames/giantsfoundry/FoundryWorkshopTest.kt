package org.rsmod.content.minigames.giantsfoundry

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.ItemServerType
import dev.openrune.util.Wearpos
import net.rsprot.protocol.game.incoming.misc.user.MoveGameClick
import net.rsprot.protocol.game.incoming.misc.user.MoveMinimapClick
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import net.rsprot.protocol.game.outgoing.sound.SynthSound
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.config.refs.params
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.net.rsprot.handlers.MoveGameClickHandler
import org.rsmod.api.net.rsprot.handlers.MoveMinimapClickHandler
import org.rsmod.api.player.events.PlayerMovementEvent
import org.rsmod.api.player.hook.GroundItemDropResolver
import org.rsmod.api.player.hook.PlayerRestrictions
import org.rsmod.api.player.interact.HeldInteractions
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.worn.HeldEquipOp
import org.rsmod.api.random.GameRandom
import org.rsmod.api.realm.Realm
import org.rsmod.api.realm.RealmConfig
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.route.BoundValidator
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.minigames.giantsfoundry.configs.FoundryStation
import org.rsmod.content.minigames.giantsfoundry.quest.SleepingGiantsState
import org.rsmod.content.minigames.giantsfoundry.quest.sleepingGiantsStage
import org.rsmod.content.minigames.giantsfoundry.quest.sleepingGiantsTutorial
import org.rsmod.content.minigames.giantsfoundry.quest.sleepingGiantsTutorialCommission
import org.rsmod.content.minigames.giantsfoundry.state.*
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.client.NoopClient
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.movement.RouteRequestCoord
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import sun.misc.Unsafe

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class FoundryWorkshopTest {
    @Test
    fun `native preform stance uses the recorded ready walking and running animations`() {
        val type = checkNotNull(ServerCacheManager.getItem(FoundryGame.PREFORM.asRSCM()))
        assertEquals("seq.human_elder_maul_ready".asRSCM(), type.param(params.bas_readyanim).id)
        for (param in listOf(params.bas_walk_f, params.bas_walk_b, params.bas_walk_l, params.bas_walk_r)) {
            assertEquals("seq.human_elder_maul_walk".asRSCM(), type.param(param).id)
        }
        assertEquals("seq.human_elder_maul_run".asRSCM(), type.param(params.bas_running).id)
    }

    @Test
    fun `collecting the tutorial preform immediately advances the native helper progress`() {
        val f = Fixture()
        f.mould(hot = false)
        f.player.sleepingGiantsStage = SleepingGiantsState.TUTORIAL
        f.player.sleepingGiantsTutorialCommission = true
        f.player.sleepingGiantsTutorial = 45
        f.operate("loc.giants_foundry_mould_jig")
        assertTrue(FoundryGame.isEquipped(f.player))
        assertEquals(50, f.player.sleepingGiantsTutorial)
    }

    @Test
    fun `game minimap and moderator teleport clicks interrupt every workshop action`() {
        val stations = listOf(
            "loc.giants_foundry_trip_hammer" to InteractionOp.Op1,
            "loc.giants_foundry_grindstone" to InteractionOp.Op1,
            "loc.giants_foundry_polishing_wheel" to InteractionOp.Op1,
            "loc.giants_foundry_lava_pool" to InteractionOp.Op1,
            "loc.giants_foundry_lava_pool" to InteractionOp.Op2,
            "loc.giants_foundry_waterfall" to InteractionOp.Op1,
            "loc.giants_foundry_waterfall" to InteractionOp.Op2,
        )
        for ((symbol, op) in stations) for (click in 0..2) {
            val f = Fixture()
            f.sword(temperature = 500)
            f.operate(symbol, op)
            f.tick(2)
            val progress = f.player.foundryCompletion
            val temperature = f.player.foundryTemperature
            val quality = f.player.foundryQuality
            val xp = f.player.statMap.getFineXP("stat.smithing")
            assertFalse(f.player.isDelayed, "$symbol op=$op click=$click")
            val destination = f.player.coords.translate(2, 0)
            f.moveClick(destination, click)
            assertTrue(f.coroutine!!.isIdle, "$symbol op=$op click=$click")
            assertEquals(EntitySeq.ZERO, f.player.pendingSequence)
            assertEquals(0, f.player.foundryWorkStation)
            if (click == 2) assertEquals(destination, f.player.coords)
            else assertEquals(destination, (f.player.routeRequest as RouteRequestCoord).destination)
            f.tick(12)
            assertEquals(progress, f.player.foundryCompletion)
            assertEquals(temperature, f.player.foundryTemperature)
            assertEquals(quality, f.player.foundryQuality)
            assertEquals(xp, f.player.statMap.getFineXP("stat.smithing"))
        }
    }

    @Test
    fun `joining just after a hammer strike waits five ticks for the next result`() {
        val f = Fixture()
        f.sword()
        f.operate("loc.giants_foundry_trip_hammer")
        assertEquals("seq.human_preform_lift_start02".asRSCM(), f.player.pendingSequence.id)
        assertEquals(0, f.player.foundryCompletion)
        f.tick(4)
        assertEquals(0, f.player.foundryCompletion)
        assertEquals("seq.human_preform_lift_loop02".asRSCM(), f.player.pendingSequence.id)
        f.tick()
        assertEquals(20, f.player.foundryCompletion)
        assertEquals(775, f.player.foundryTemperature)
        f.tick(5)
        assertEquals(40, f.player.foundryCompletion)
        f.stop()
    }

    @Test
    fun `hammer progress follows the shared strike cycle for every arrival phase`() {
        for (phase in 0 until FoundryMachineryScript.HAMMER_CYCLES) {
            val f = Fixture()
            f.player.currentMapClock += phase
            f.player.processedMapClock = f.player.currentMapClock
            f.sword()
            f.operate("loc.giants_foundry_trip_hammer")
            val wait = FoundryMachineryScript.HAMMER_CYCLES - phase
            repeat(wait - 1) {
                f.tick()
                assertNotEquals(CoordGrid.NULL, f.player.pendingFaceSquare)
                assertEquals("seq.human_preform_lift_loop02".asRSCM(), f.player.pendingSequence.id)
            }
            assertEquals(0, f.player.foundryCompletion, "phase=$phase")
            assertEquals(0, f.player.statMap.getFineXP("stat.smithing"))
            assertTrue(f.packets.filterIsInstance<SynthSound>().isEmpty())
            f.tick()
            assertEquals(105, f.player.currentMapClock)
            assertEquals(20, f.player.foundryCompletion, "phase=$phase")
            assertTrue(f.player.statMap.getFineXP("stat.smithing") > 0)
            assertEquals("synth.SG_HAMMER_TOOL_HIT".asRSCM(), f.packets.filterIsInstance<SynthSound>().single().id)
            assertNotEquals(CoordGrid.NULL, f.player.pendingFaceSquare)
            assertEquals("seq.human_preform_lift_loop02".asRSCM(), f.player.pendingSequence.id)
            f.tick(5)
            assertEquals(40, f.player.foundryCompletion)
            f.stop()
        }
    }

    @Test
    fun `grindstone starts after three ticks then repeats every two`() {
        val f = Fixture()
        f.sword(completion = 260, temperature = 450)
        f.operate("loc.giants_foundry_grindstone")
        assertEquals("seq.human_preform_lift_start".asRSCM(), f.player.pendingSequence.id)
        f.tick(2)
        assertEquals(260, f.player.foundryCompletion)
        f.tick()
        assertEquals(270, f.player.foundryCompletion)
        f.tick()
        assertEquals(270, f.player.foundryCompletion)
        f.tick()
        assertEquals(280, f.player.foundryCompletion)
        assertEquals(480, f.player.foundryTemperature)
        f.stop()
    }

    @Test
    fun `heating waits for the first animation and movement cancels further temperature changes`() {
        val f = Fixture()
        f.sword(temperature = 500)
        f.operate("loc.giants_foundry_lava_pool")
        assertEquals(500, f.player.foundryTemperature)
        assertEquals("seq.human_pickupfloor".asRSCM(), f.player.pendingSequence.id)
        f.tick()
        assertEquals(507, f.player.foundryTemperature)
        f.tick()
        assertEquals(515, f.player.foundryTemperature)
        f.stop()
        assertEquals(EntitySeq.ZERO, f.player.pendingSequence)
        f.tick(10)
        assertEquals(515, f.player.foundryTemperature)
        assertTrue(FoundryGame.isEquipped(f.player))
    }

    @Test
    fun `hot mould refuses collection without ice gloves or consuming the sword`() {
        val f = Fixture()
        f.mould(hot = true)
        f.operate("loc.giants_foundry_mould_jig")
        assertEquals(2, f.player.foundryMouldState)
        assertFalse(FoundryGame.isEquipped(f.player))
        assertEquals(0, f.player.inv.count(FoundryGame.PREFORM))
        assertEquals(94, f.player.foundryStartQuality)
    }

    @Test
    fun `both kinds of ice gloves cool and equip the native two handed preform`() {
        for (gloves in listOf("obj.ice_gloves", "obj.smithing_uniform_gloves_ice")) {
            val f = Fixture()
            f.mould(hot = true)
            f.player.worn[Wearpos.Hands.slot] = InvObj(gloves)
            f.player.worn[Wearpos.RightHand.slot] = InvObj("obj.bronze_sword")
            f.operate("loc.giants_foundry_mould_jig")
            assertTrue(FoundryGame.isEquipped(f.player), gloves)
            assertEquals(0, f.player.foundryMouldState)
            assertEquals(982, f.player.foundryTemperature)
            assertNull(f.player.worn[Wearpos.LeftHand.slot])
            assertEquals(1, f.player.inv.count("obj.bronze_sword"))
            assertTrue(f.player.ui.containsOverlay(FoundryInterfaces.HUD))
        }
    }

    @Test
    fun `cooled mould can be collected without ice gloves and storage preserves its temperature`() {
        val f = Fixture()
        f.mould(hot = false)
        f.operate("loc.giants_foundry_mould_jig")
        assertTrue(FoundryGame.isEquipped(f.player))
        f.player.foundryTemperature = 400
        f.player.foundryCompletion = 420
        f.operate("loc.giants_foundry_preform_storage")
        assertEquals(1, f.player.foundryStored)
        assertFalse(FoundryGame.isEquipped(f.player))
        assertFalse(f.player.ui.containsOverlay(FoundryInterfaces.HUD))
        f.operate("loc.giants_foundry_preform_storage")
        assertTrue(FoundryGame.isEquipped(f.player))
        assertEquals(0, f.player.foundryStored)
        assertEquals(400, f.player.foundryTemperature)
        assertEquals(420, f.player.foundryCompletion)
    }

    @Test
    fun `off-hand equipment blocks hot cooled and stored preforms without changing state`() {
        for (state in 2..4) {
            val f = Fixture()
            f.mould(hot = state == 2)
            f.player.foundryStored = if (state == 4) 1 else 0
            if (state == 4) f.player.foundryMouldState = 0
            f.player.worn[Wearpos.Hands.slot] = InvObj("obj.ice_gloves")
            f.player.worn[Wearpos.RightHand.slot] = InvObj("obj.bronze_sword")
            f.player.worn[Wearpos.LeftHand.slot] = InvObj("obj.wooden_shield")
            val mouldState = f.player.foundryMouldState
            val stored = f.player.foundryStored
            val source = if (state == 4) "loc.giants_foundry_preform_storage"
            else "loc.giants_foundry_mould_jig"

            f.operate(source)

            assertFalse(FoundryGame.isEquipped(f.player))
            assertEquals(mouldState, f.player.foundryMouldState)
            assertEquals(stored, f.player.foundryStored)
            assertEquals(94, f.player.foundryQuality)
            assertEquals(800, f.player.foundryTemperature)
            assertEquals("obj.bronze_sword".asRSCM(), f.player.worn[Wearpos.RightHand.slot]!!.id)
            assertEquals("obj.wooden_shield".asRSCM(), f.player.worn[Wearpos.LeftHand.slot]!!.id)
            assertEquals(28, f.player.inv.freeSpace())
            assertTrue(f.packets.filterIsInstance<MessageGame>().any {
                "remove your shield or off-hand item" in it.message
            })
            assertFalse(f.player.ui.containsOverlay(FoundryInterfaces.HUD))

            f.player.worn[Wearpos.LeftHand.slot] = null
            f.operate(source)
            assertTrue(FoundryGame.isEquipped(f.player))
            assertEquals(1, f.player.inv.count("obj.bronze_sword"))
        }
    }

    @Test
    fun `insufficient room for held equipment leaves the cooled sword in its mould`() {
        val f = Fixture()
        f.mould(hot = false)
        f.player.worn[Wearpos.RightHand.slot] = InvObj("obj.bronze_sword")
        repeat(28) { f.player.inv[it] = InvObj("obj.knife") }
        f.operate("loc.giants_foundry_mould_jig")
        assertFalse(FoundryGame.isEquipped(f.player))
        assertEquals(3, f.player.foundryMouldState)
        assertEquals(0, f.player.inv.freeSpace())
        assertEquals(0, f.player.inv.count(FoundryGame.PREFORM))
        assertEquals("obj.bronze_sword".asRSCM(), f.player.worn[Wearpos.RightHand.slot]!!.id)
    }

    @Test
    fun `interrupting the protected work loop cannot produce a delayed result`() {
        val f = Fixture()
        f.sword()
        f.operate("loc.giants_foundry_trip_hammer")
        f.tick(4)
        f.stop()
        assertEquals(EntitySeq.ZERO, f.player.pendingSequence)
        f.tick(10)
        assertEquals(0, f.player.foundryCompletion)
        assertEquals(0, f.player.foundryWorkStation)
        assertTrue(FoundryGame.isEquipped(f.player))
    }

    @Test
    fun `logout and leaving the workshop store equipped swords without erasing progress`() {
        for (logout in listOf(true, false)) {
            val f = Fixture()
            f.sword(completion = 620, temperature = 400)
            if (logout) f.events.publish(SessionStateEvent.Logout(f.player))
            else {
                val previous = f.player.coords
                f.player.coords = CoordGrid(3360, 3149)
                f.events.publish(PlayerMovementEvent.CoordsMovedEvent(f.player, previous))
            }
            assertFalse(FoundryGame.isEquipped(f.player))
            assertEquals(1, f.player.foundryStored)
            assertEquals(620, f.player.foundryCompletion)
            assertEquals(400, f.player.foundryTemperature)
            assertEquals(94, f.player.foundryQuality)
        }
    }

    @Test
    fun `a claimed sweet spot cannot be claimed again by repeatedly clicking the tool`() {
        val f = Fixture()
        f.sword()
        f.player.foundrySweetUntil = f.player.currentMapClock + 9
        f.operate("loc.giants_foundry_trip_hammer")
        assertTrue(f.player.foundrySweetClaimed)
        assertEquals(0, f.player.foundrySweetUntil)
        f.tick(5)
        val boostedProgress = f.player.foundryCompletion
        assertTrue(boostedProgress > FoundryStation.HAMMER.progress)
        f.stop()
        f.operate("loc.giants_foundry_trip_hammer")
        assertFalse(f.player.foundrySweetClaimed)
        f.tick(5)
        assertEquals(boostedProgress, f.player.foundryCompletion)
        f.tick(5)
        assertEquals(boostedProgress + FoundryStation.HAMMER.progress, f.player.foundryCompletion)
        assertEquals(94, f.player.foundryQuality)
        f.stop()
    }

    @Test
    fun `clicking the same tool during work skips one action without damaging quality`() {
        val f = Fixture()
        f.sword()
        f.operate("loc.giants_foundry_trip_hammer")
        f.tick(2)
        f.stop()
        f.operate("loc.giants_foundry_trip_hammer")
        f.tick(7)
        assertEquals(0, f.player.foundryCompletion)
        assertEquals(94, f.player.foundryQuality)
        f.tick()
        assertEquals(20, f.player.foundryCompletion)
        assertEquals(94, f.player.foundryQuality)
        f.stop()
    }

    @Test
    fun `ruined swords cannot continue temperature or refinement actions`() {
        for (loc in listOf("loc.giants_foundry_trip_hammer", "loc.giants_foundry_lava_pool", "loc.giants_foundry_waterfall")) {
            val f = Fixture()
            f.sword()
            f.player.foundryQuality = 0
            f.operate(loc)
            assertTrue(f.coroutine!!.isIdle)
            f.tick(5)
            assertEquals(0, f.player.foundryCompletion)
            assertEquals(800, f.player.foundryTemperature)
        }
    }

    private class Fixture {
        val events = EventBus()
        val packets = mutableListOf<Any>()
        private val interfaces = FoundryInterfaces(events)
        private val game = FoundryGame(interfaces, events, XpModifiers(emptySet()))
        private val world = FoundryQuestTestWorld(game, events)
        private val script = FoundryWorkshopScript(game, interfaces, events, world.service, world.workshop)
        private val collision = CollisionFlagMap()
        private val interactions = LocInteractions(BoundValidator(collision), events)
        val player = Player().apply {
            client = object : Client<Any, Any> by NoopClient {
                override fun write(message: Any) { packets += message }
            }
            inv = Inventory.create("inv.inv")
            worn = Inventory.create("inv.worn")
            coords = CoordGrid(3364, 11495)
            previousCoords = coords
            currentMapClock = 100
            processedMapClock = 100
            statMap.setBaseLevel("stat.smithing", 99)
            statMap.setCurrentLevel("stat.smithing", 99)
            VarPlayerIntMapSetter.set(this, "varbit.sleeping_giants", 30)
        }
        private val random = object : GameRandom {
            override fun of(maxExclusive: Int) = maxExclusive - 1
            override fun of(minInclusive: Int, maxInclusive: Int) = maxInclusive
            override fun randomDouble() = 0.99
        }
        private val context by lazy {
            ProtectedAccessContextFactory.empty().copy(
                getEventBus = { events }, getRandom = { random },
                getLocInteractions = { interactions }, getHeldInteractions = { heldInteractions() },
            )
        }
        var coroutine: GameCoroutine? = null
            private set

        init {
            with(script) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
        }

        fun sword(completion: Int = 0, temperature: Int = 800) {
            player.foundryWord1 = 4
            player.foundryWord2 = 5
            player.foundryQuality = 94
            player.foundryStartQuality = 94
            player.foundryDifficulty = 50
            player.foundryCompletion = completion
            player.foundryTemperature = temperature
            FoundryState.setStations(player, listOf(FoundryStation.HAMMER, FoundryStation.GRINDSTONE, FoundryStation.POLISHING_WHEEL, FoundryStation.HAMMER))
            player.worn[Wearpos.RightHand.slot] = InvObj(FoundryGame.PREFORM)
        }

        fun mould(hot: Boolean) {
            sword()
            player.worn[Wearpos.RightHand.slot] = null
            player.foundryMouldState = if (hot) 2 else 3
        }

        fun operate(symbol: String, op: InteractionOp = InteractionOp.Op1) {
            val id = symbol.asRSCM()
            val type = checkNotNull(ServerCacheManager.getObject(id))
            val loc = BoundLocInfo(LocInfo(2, player.coords.translateX(-1), LocEntity(id, 10, 0)), type)
            val event = checkNotNull(interactions.opTrigger(player, loc, op)) { "Missing handler for $symbol" }
            coroutine = player.launch { events.publish(ProtectedAccess(player, this, context), event) }
        }

        fun moveClick(destination: CoordGrid, kind: Int) {
            val realm = Realm("foundry-test").apply {
                updateConfig(RealmConfig(1, null, null, 1.0, 1.0, player.coords, player.coords, true, false, true))
            }
            val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
            val factory = (field.get(null) as Unsafe).allocateInstance(ProtectedAccessContextFactory::class.java) as ProtectedAccessContextFactory
            ProtectedAccessContextFactory::class.java.getDeclaredField("eventBus").apply {
                isAccessible = true
                set(factory, events)
            }
            collision.allocateIfAbsent(player.x, player.z, player.level)
            collision.allocateIfAbsent(destination.x, destination.z, destination.level)
            when (kind) {
                1 -> MoveMinimapClickHandler(realm, events, collision, factory).handle(
                    player, MoveMinimapClick(destination.x, destination.z, 0, 0, 0, 0, 0, 0),
                )
                else -> MoveGameClickHandler(realm, events, collision, factory).handle(
                    player, MoveGameClick(destination.x, destination.z, if (kind == 2) 2 else 0),
                )
            }
        }

        fun tick(count: Int = 1) = repeat(count) {
            player.pendingSequence = EntitySeq.NULL
            player.pendingFaceSquare = CoordGrid.NULL
            player.currentMapClock++
            player.processedMapClock = player.currentMapClock
            coroutine?.takeIf { it.isSuspended }?.advance()
        }

        fun stop() { player.cancelActiveCoroutine() }

        private fun heldInteractions(): HeldInteractions {
            val market = object : MarketPrices {
                override fun get(type: ItemServerType): Int? = null
            }
            val dropClass = Class.forName("org.rsmod.api.player.interact.HeldDropOp")
            val drop = dropClass.declaredConstructors.single().apply { isAccessible = true }.newInstance(
                events, ObjRepository(MapClock(), ObjRegistry(ZoneUpdateMap())), market,
                GroundItemDropResolver(emptySet()),
            )
            return HeldInteractions::class.java.declaredConstructors.single().apply { isAccessible = true }.newInstance(
                events, market, drop, HeldEquipOp(events, PlayerRestrictions(emptySet())),
            ) as HeldInteractions
        }
    }

    companion object {
        @JvmStatic @BeforeAll
        fun cache() {
            ServerCacheManager.init(241).close()
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
                ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
            }
        }
    }
}
