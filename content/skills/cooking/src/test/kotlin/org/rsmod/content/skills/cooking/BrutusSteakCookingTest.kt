package org.rsmod.content.skills.cooking

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.interact.LocUInteractions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.random.GameRandom
import org.rsmod.api.route.BoundValidator
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.utils.skills.SkillingSuccessRate
import org.rsmod.content.skills.cooking.table.CookingBurnContext
import org.rsmod.content.skills.cooking.table.CookingFood
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorage
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BrutusSteakCookingTest {
    private val transactionsField by lazy {
        Class.forName("org.rsmod.api.invtx.InvTransactionsScriptKt")
            .getDeclaredField("cachedInventoryTransactions").apply { isAccessible = true }
    }
    private val storageField by lazy {
        Class.forName("org.rsmod.api.invtx.VirtualInvTransactionsKt")
            .getDeclaredField("cachedPlayerItemStorage").apply { isAccessible = true }
    }
    private var oldTransactions: Any? = null
    private var oldStorage: Any? = null
    private var oldVirtualStorage: InvVirtualStorage? = null

    @BeforeAll
    fun loadCacheAndTransactions() {
        ServerCacheManager.init(241).close()
        oldTransactions = transactionsField.get(null)
        oldStorage = storageField.get(null)
        oldVirtualStorage = InvVirtualStorageHolder.instance
        with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
            ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
        }
    }

    @AfterAll
    fun restoreTransactions() {
        transactionsField.set(null, oldTransactions)
        storageField.set(null, oldStorage)
        InvVirtualStorageHolder.instance = oldVirtualStorage
    }

    @Test
    fun `Brutus drop has its own packed cooking recipe and observed success curve`() {
        val food = CookingFood.all().single { it.raw.id == 33106 }
        assertEquals(33109, food.cooked.id)
        assertEquals(33112, food.burnt.id)
        assertEquals(13, food.level)
        assertEquals(90.0, food.xp)
        assertEquals(1, food.outputAmount)
        assertFalse(food.gauntletAffected)
        val successOutOf256 = listOf(
            134, 137, 140, 144, 147, 150, 154, 157, 161, 164, 167, 171, 174,
            177, 181, 184, 188, 191, 194, 198, 201, 204, 208, 211, 215, 218,
            221, 225, 228, 231, 235, 238, 242, 245, 248, 252, 255, 256,
        )
        for (surface in listOf(CookingBurnContext.FIRE, CookingBurnContext.RANGE)) {
            val curve = food.burnCurve(surface)
            for ((index, expected) in successOutOf256.withIndex()) {
                val rate = SkillingSuccessRate.successRate(curve.low, curve.high, index + 13, 99)
                assertEquals(expected / 256.0, rate.coerceAtMost(1.0), "Level ${index + 13} on $surface")
            }
        }
    }

    @Test
    fun `using a Brutus steak on a fire or range cooks it even with a full inventory`() = runBlocking {
        for (surface in listOf("loc.fire", "loc.range")) {
            val fixture = Fixture(surface, level = 50, randomRoll = 0.999)
            for (slot in 1 until 28) fixture.player.inv[slot] = InvObj(item("obj.bones"))
            fixture.useSteak()
            assertEquals(0, fixture.player.inv.count("obj.raw_tbone_steak"))
            assertEquals(1, fixture.player.inv.count("obj.tbone_steak"))
            assertEquals(27, fixture.player.inv.count("obj.bones"))
            assertEquals(900, fixture.player.statMap.getFineXP("stat.cooking"))
            assertTrue(fixture.client.messages.any { "You successfully cook the t-bone steak." in it.toString() })
        }
    }

    @Test
    fun `a failed cook produces burnt steak and awards no XP`() = runBlocking {
        for (surface in listOf("loc.fire", "loc.range")) {
            val fixture = Fixture(surface, level = 13, randomRoll = 0.999)
            fixture.useSteak()
            assertEquals(0, fixture.player.inv.count("obj.raw_tbone_steak"))
            assertEquals(1, fixture.player.inv.count("obj.tbone_steak_burned"))
            assertEquals(0, fixture.player.statMap.getFineXP("stat.cooking"))
            assertTrue(fixture.client.messages.any { "You accidentally burn the t-bone steak." in it.toString() })
        }
    }

    @Test
    fun `players below level 13 cannot consume or cook Brutus steaks`() = runBlocking {
        for (surface in listOf("loc.fire", "loc.range")) {
            val fixture = Fixture(surface, level = 12)
            fixture.useSteak()
            assertEquals(1, fixture.player.inv.count("obj.raw_tbone_steak"))
            assertEquals(0, fixture.player.inv.count("obj.tbone_steak"))
            assertEquals(0, fixture.player.statMap.getFineXP("stat.cooking"))
        }
    }

    @Test
    fun `clicking Cook on a range also finds the steak recipe`() = runBlocking {
        val fixture = Fixture("loc.range", level = 13)
        val interactions = LocInteractions(BoundValidator(CollisionFlagMap()), fixture.events)
        val event = checkNotNull(interactions.opTrigger(fixture.player, fixture.loc, InteractionOp.Op1))
        assertTrue(fixture.events.publish(fixture.access, event))
        assertEquals(1, fixture.player.inv.count("obj.tbone_steak"))
        assertEquals(900, fixture.player.statMap.getFineXP("stat.cooking"))
    }

    @Test
    fun `noted steak drops must be unnoted before cooking`() {
        val fixture = Fixture("loc.fire", level = 50)
        val noted = item("obj.cert_raw_tbone_steak")
        fixture.player.inv[0] = InvObj(noted, 3)
        val event = with(fixture.useInteractions) {
            fixture.access.opTrigger(fixture.loc, fixture.loc, fixture.locType, noted, 0)
        }
        assertNull(event)
        assertEquals(3, fixture.player.inv.count("obj.cert_raw_tbone_steak"))
    }

    private class Fixture(surface: String, level: Int, randomRoll: Double = 0.0) {
        val events = EventBus()
        val client = RecordingClient()
        val player = Player().apply {
            client = this@Fixture.client
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
            inv[0] = InvObj(item("obj.raw_tbone_steak"))
            statMap.setBaseLevel("stat.cooking", level.toByte())
            statMap.setCurrentLevel("stat.cooking", level.toByte())
        }
        private val random = object : GameRandom by DefaultGameRandom(1) {
            override fun randomDouble(): Double = randomRoll
        }
        val locType = checkNotNull(ServerCacheManager.getObject(surface.asRSCM()))
        val loc = locType.let {
            BoundLocInfo(LocInfo(2, CoordGrid(3200, 3200), LocEntity(it.id, 10, 0)), it)
        }
        val useInteractions = LocUInteractions::class.java.getDeclaredConstructor(EventBus::class.java)
            .apply { isAccessible = true }.newInstance(events)
        val access = ProtectedAccess(
            player, GameCoroutine(),
            ProtectedAccessContext(
                getRandom = { random },
                getEventBus = { events },
                getNpcList = { error("unused") },
                getPlayerList = { error("unused") },
                getCollision = { error("unused") },
                getAreaChecker = { error("unused") },
                getAlignment = { error("unused") },
                getLocInteractions = { error("unused") },
                getNpcInteractions = { error("unused") },
                getPlayerInteractions = { error("unused") },
                getHeldInteractions = { error("unused") },
                getWornInteractions = { error("unused") },
                getMusicPlayer = { error("unused") },
                getMarketPrices = { error("unused") },
                getInstantHitProcessor = { error("unused") },
                getTeleportValidator = { error("unused") },
                getWorldRepo = { error("unused") },
                getHitModifier = { error("unused") },
            ),
        )

        init {
            with(CookingEvents(XpModifiers(emptySet()))) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        suspend fun useSteak() {
            val raw = item("obj.raw_tbone_steak")
            val event = with(useInteractions) { access.opTrigger(loc, loc, locType, raw, 0) }
            checkNotNull(event) { "Missing steak cooking handler for ${locType.internalName}" }
            assertTrue(events.publish(access, event))
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

    private companion object {
        fun item(name: String) = checkNotNull(ServerCacheManager.getItem(name.asRSCM()))
    }
}
