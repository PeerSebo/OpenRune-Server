package org.rsmod.content.skills.construction.sawmill

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcMode
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.game.process.npc.NpcMovementProcessor
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.route.StepFactory
import org.rsmod.api.shops.Shops
import org.rsmod.content.minigames.mahoganyhomes.planksack.PlankSack
import org.rsmod.content.minigames.mahoganyhomes.planksack.PlankType
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.obj.Obj
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag
import sun.misc.Unsafe

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class SawmillOperatorTest {
    @Test fun `all native Buy-plank definitions are covered and preserve Trade`() {
        val native = ServerCacheManager.getNpcs().values.filter {
            (0..4).any { slot -> it.actions.getOpOrNull(slot) == "Buy-plank" }
        }.map { it.id }.toSet()
        assertEquals(native, SawmillOperatorScript.Operators.map { it.asRSCM() }.toSet())
        for (symbol in SawmillOperatorScript.Operators) {
            val f = Fixture(symbol)
            f.give("obj.logs", 3)
            f.give("obj.coins", 300)
            f.start(InteractionOp.Op3, approach = true)
            assertTrue(f.player.ui.containsModal("interface.skillmulti"), symbol)
            f.choose("component.skillmulti:a", 3)
            assertEquals(3, f.player.inv.count("obj.woodplank"), symbol)
            assertEquals(0, f.player.inv.count("obj.coins"), symbol)
            assertEquals("obj.newsletter_sawdust".asRSCM(), f.sawdust()?.type, symbol)
            assertEquals(f.player.coords, f.sawdust()?.coords, symbol)
            assertEquals(1, f.sawdust()?.count, symbol)
            f.completed()
            val trade = Fixture(symbol)
            trade.start(InteractionOp.Op4, approach = true)
            trade.completed()
            assertEquals("inv.poh_sawmill_shop", trade.player.openedShop?.inv?.internalName)
        }
    }

    @Test fun `talking offers plank making and supplies across the counter`() {
        for (symbol in SawmillOperatorScript.Operators) for (choice in 1..3) {
            val f = Fixture(symbol)
            f.give("obj.oak_logs")
            f.give("obj.coins", 250)
            f.start(InteractionOp.Op1, approach = true)
            f.choose("component.chat_left:continue", -1)
            f.choose("component.chatmenu:options", choice)
            when (choice) {
                1 -> {
                    assertTrue(f.player.ui.containsModal("interface.skillmulti"))
                    f.choose("component.skillmulti:a", 1)
                    assertEquals(1, f.player.inv.count("obj.plank_oak"))
                }
                2 -> assertEquals("inv.poh_sawmill_shop", f.player.openedShop?.inv?.internalName)
                else -> assertFalse(f.player.ui.containsModal("interface.skillmulti"))
            }
            f.completed()
        }
    }

    @Test fun `approach retains its route until inside one tile`() {
        for (op in listOf(InteractionOp.Op1, InteractionOp.Op3, InteractionOp.Op4)) {
            val f = Fixture()
            f.player.coords = f.npc.coords.translate(0, -2)
            f.start(op, approach = true)
            f.completed()
            assertEquals(1, f.player.interaction?.apRange)
            assertNotNull(f.player.routeRequest)
            assertFalse(f.player.ui.containsModal("interface.skillmulti"))
            assertNull(f.player.openedShop)
        }
    }

    @Test fun `successful conversion leaves a temporary ground item under the player`() {
        val f = Fixture()
        f.give("obj.logs")
        f.give("obj.coins", 100)
        f.start(InteractionOp.Op3, approach = true)
        f.choose("component.skillmulti:a", 1)
        assertNotNull(f.sawdust())
        assertEquals(0, f.collision[f.player.coords.x, f.player.coords.z, 0] and
            (CollisionFlag.BLOCK_WALK or CollisionFlag.GROUND_DECOR or CollisionFlag.LOC))
        repeat(SawmillOperatorScript.SawdustTicks - 1) { f.tick() }
        assertNotNull(f.sawdust())
        f.tick()
        assertNull(f.sawdust())
    }

    @Test fun `failed purchase and movement away from the operator create no sawdust`() {
        for (move in listOf(false, true)) {
            val f = Fixture()
            f.give("obj.logs")
            f.give("obj.coins", 100)
            f.start(InteractionOp.Op3, approach = true)
            if (move) f.player.coords = f.npc.coords.translate(0, -2)
            else f.player.inv[f.player.inv.indices.first { f.player.inv[it]?.id == "obj.coins".asRSCM() }] = null
            f.choose("component.skillmulti:a", 1)
            assertEquals(1, f.player.inv.count("obj.logs"))
            assertEquals(0, f.player.inv.count("obj.woodplank"))
            assertNull(f.sawdust())
        }
    }

    @Test fun `separate exchanges leave independently expiring sawdust items`() {
        val f = Fixture()
        f.give("obj.logs", 2)
        f.give("obj.coins", 200)
        f.start(InteractionOp.Op3, approach = true)
        f.choose("component.skillmulti:a", 1)
        repeat(50) { f.tick() }
        f.start(InteractionOp.Op3, approach = true)
        f.choose("component.skillmulti:a", 1)
        assertEquals(2, f.sawdustPiles().size)
        repeat(50) { f.tick() }
        assertEquals(1, f.sawdustPiles().size)
        repeat(50) { f.tick() }
        assertNull(f.sawdust())
    }

    @Test fun `operators remain at the requested tiles and orientations after movement requests`() {
        for (operator in SawmillOperatorScript.Operators) {
            assertEquals(1, checkNotNull(ServerCacheManager.getNpc(operator.asRSCM())).renderPriority)
        }
        for ((coords, direction) in SawmillOperatorScript.Facing) {
            val symbol = if (coords.x == 1396) "npc.auburn_sawmill_operator" else "npc.poh_sawmill_opp"
            val f = Fixture(symbol)
            val npc = Npc(symbol, coords)
            f.script.faceCounter(npc)
            assertEquals(direction, npc.respawnDir)
            assertEquals(NpcMode.None, npc.defaultMode)
            assertEquals(MoveRestrict.NoMove, npc.moveRestrict)
            assertEquals(0, npc.wanderRange)
            val collision = CollisionFlagMap()
            collision.allocateIfAbsent(coords.x, coords.z, coords.level)
            npc.walk(coords.translate(1, 0))
            NpcMovementProcessor(collision, StepFactory(collision), EventBus()).process(npc)
            assertEquals(coords, npc.coords)
            npc.defaultMode()
            assertEquals(NpcMode.None, npc.mode)
            assertEquals(direction, npc.respawnDir)
        }
    }

    @Test fun `all seven recipes convert a full inventory and charge the recorded price`() {
        assertEquals(listOf(100, 250, 500, 1500, 2500, 5000, 7500), SawmillPlanks.recipes.map { it.coins })
        for (recipe in SawmillPlanks.recipes) {
            val f = Fixture()
            f.give(recipe.logs.internalName, 27)
            f.give("obj.coins", recipe.coins * 27)
            assertEquals(27, SawmillPlanks.convert(f.player, recipe, Int.MAX_VALUE))
            assertEquals(27, f.player.inv.count(recipe.plank.internalName))
            assertEquals(0, f.player.inv.count(recipe.logs.internalName))
            assertEquals(0, f.player.inv.count("obj.coins"))
            assertEquals(0, SawmillPlanks.convert(f.player, recipe, 1))
        }
    }

    @Test fun `unaffordable missing noted and cancelled inputs never charge or create planks`() {
        val recipe = SawmillPlanks.recipes.first()
        for ((logs, coins, requested) in listOf(Triple(1, 99, 1), Triple(0, 100, 1), Triple(1, 100, 0), Triple(1, 100, -1))) {
            val f = Fixture()
            f.give(recipe.logs.internalName, logs)
            f.give("obj.coins", coins)
            assertEquals(0, SawmillPlanks.convert(f.player, recipe, requested))
            assertEquals(logs, f.player.inv.count(recipe.logs.internalName))
            assertEquals(coins, f.player.inv.count("obj.coins"))
            assertEquals(0, f.player.inv.count(recipe.plank.internalName))
        }
        val noted = Fixture()
        noted.give("obj.cert_logs", 20)
        noted.give("obj.coins", 2000)
        assertEquals(0, SawmillPlanks.convert(noted.player, recipe, 20))
        assertEquals(20, noted.player.inv.count("obj.cert_logs"))
    }

    @Test fun `conversion is limited by requested quantity and affordable logs`() {
        val recipe = SawmillPlanks.recipes.first()
        val f = Fixture()
        f.give("obj.logs", 10)
        f.give("obj.coins", 450)
        assertEquals(3, SawmillPlanks.convert(f.player, recipe, 3))
        assertEquals(1, SawmillPlanks.convert(f.player, recipe, Int.MAX_VALUE))
        assertEquals(50, f.player.inv.count("obj.coins"))
        assertEquals(6, f.player.inv.count("obj.logs"))
        assertEquals(4, f.player.inv.count("obj.woodplank"))
    }

    @Test fun `sawmill vouchers yield an additional plank without a second log or fee`() {
        val f = Fixture()
        f.give("obj.logs", 3)
        f.give("obj.coins", 300)
        f.give(SawmillPlanks.VOUCHER, 2)
        assertEquals(3, SawmillPlanks.convert(f.player, SawmillPlanks.recipes.first(), 3))
        assertEquals(5, f.player.inv.count("obj.woodplank"))
        assertEquals(0, f.player.inv.count(SawmillPlanks.VOUCHER))
        assertEquals(0, f.player.inv.count("obj.coins"))
    }

    @Test fun `sack autofill uses the existing shared capacity and overflows to inventory`() {
        val f = Fixture()
        f.give("obj.plank_sack")
        f.give("obj.logs", 4)
        f.give("obj.coins", 400)
        f.give(SawmillPlanks.VOUCHER, 4)
        PlankSack.depositUpTo(f.player, PlankType.OAK, 25)
        assertEquals(4, SawmillPlanks.convert(f.player, SawmillPlanks.recipes.first(), 4))
        assertEquals(3, PlankSack.stored(f.player, PlankType.PLAIN))
        assertEquals(25, PlankSack.stored(f.player, PlankType.OAK))
        assertEquals(5, f.player.inv.count("obj.woodplank"))
    }

    @Test fun `failure to fit voucher output leaves logs coins vouchers and sack unchanged`() {
        val f = Fixture()
        f.give("obj.logs")
        f.give("obj.coins", 1000)
        f.give(SawmillPlanks.VOUCHER, 10)
        f.give("obj.plank_sack")
        PlankSack.depositUpTo(f.player, PlankType.OAK, 28)
        f.give("obj.spade", f.player.inv.freeSpace())
        assertEquals(0, SawmillPlanks.convert(f.player, SawmillPlanks.recipes.first(), 1))
        assertEquals(1, f.player.inv.count("obj.logs"))
        assertEquals(1000, f.player.inv.count("obj.coins"))
        assertEquals(10, f.player.inv.count(SawmillPlanks.VOUCHER))
        assertEquals(28, PlankSack.totalStored(f.player))
    }

    private class Fixture(symbol: String = "npc.poh_sawmill_opp") {
        val player = Player().apply {
            coords = CoordGrid(3302, 3491)
            currentMapClock = 100
            processedMapClock = 100
            inv = Inventory.create("inv.inv")
        }
        val npc = Npc(symbol, CoordGrid(3302, 3492))
        private val events = EventBus()
        private val interactions = NpcInteractions(events)
        private val coroutine = GameCoroutine("sawmill-test")
        private var result: Result<Unit>? = null
        private val list = NpcList()
        private val clock = MapClock(100)
        val collision = CollisionFlagMap().apply { allocateIfAbsent(3302, 3491, 0) }
        private val objs = ObjRepository(clock, ObjRegistry(ZoneUpdateMap()))
        private val repository = NpcRepository(
            MapClock(), NpcRegistry(list, CollisionFlagMap(), events), list,
        )
        val script = SawmillOperatorScript(Shops(events), repository, objs)

        init {
            with(script) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
            repository.add(npc, Int.MAX_VALUE)
        }

        fun give(symbol: String, count: Int = 1) {
            if (count <= 0) return
            val type = requireNotNull(ServerCacheManager.getItem(symbol.asRSCM()))
            val stackable = symbol == "obj.coins" || symbol == SawmillPlanks.VOUCHER || symbol == "obj.cert_logs"
            repeat(if (stackable) 1 else count) {
                player.inv[player.inv.indices.first { player.inv[it] == null }] = InvObj(type, if (stackable) count else 1)
            }
        }

        fun start(op: InteractionOp, approach: Boolean = false) {
            interactions.interact(player, npc, op)
            val context = ProtectedAccessContextFactory.empty().copy(
                getRandom = { DefaultGameRandom(1) }, getEventBus = { events },
                getAlignment = { TextAlignment() }, getNpcInteractions = { interactions },
            )
            val access = ProtectedAccess(player, coroutine, context)
            player.activeCoroutine = coroutine
            val block: suspend () -> Unit = {
                val event = checkNotNull(if (approach) interactions.apTrigger(player, npc, op) else interactions.opTrigger(player, npc, op))
                assertTrue(events.publish(access, event))
            }
            block.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(value: Result<Unit>) { result = value }
            })
            result?.getOrThrow()
        }

        fun choose(component: String, slot: Int) {
            assertTrue(coroutine.isAwaiting(ResumePauseButtonInput::class))
            coroutine.resumeWith(ResumePauseButtonInput(component, slot))
            result?.getOrThrow()
        }

        fun completed() = checkNotNull(result).getOrThrow()
        fun sawdustPiles(): List<Obj> = objs.findAll(player.coords)
            .filter { it.type == "obj.newsletter_sawdust".asRSCM() }.toList()
        fun sawdust(): Obj? = sawdustPiles().firstOrNull()
        fun tick() {
            clock.tick()
            objs.javaClass.declaredMethods.single { it.name.startsWith("processDurations") }
                .apply { isAccessible = true }.invoke(objs)
        }
    }

    companion object {
        private inline fun <reified T> unused(): T {
            val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").run {
                isAccessible = true
                get(null) as Unsafe
            }
            return unsafe.allocateInstance(T::class.java) as T
        }
        private val restore = mutableListOf<() -> Unit>()

        @JvmStatic @BeforeAll fun cache() {
            ServerCacheManager.init(241).close()
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
