package org.rsmod.content.interfaces.bank

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.input.ResumePCountDialogInput
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorage
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BankCurrencyExchangeTest {
    private val transactions = Class.forName("org.rsmod.api.invtx.InvTransactionsScriptKt")
        .getDeclaredField("cachedInventoryTransactions").apply { isAccessible = true }
    private val storage = Class.forName("org.rsmod.api.invtx.VirtualInvTransactionsKt")
        .getDeclaredField("cachedPlayerItemStorage").apply { isAccessible = true }
    private var previousTransactions: Any? = null
    private var previousStorage: Any? = null
    private var previousVirtual: InvVirtualStorage? = null

    @BeforeAll fun setup() {
        ServerCacheManager.init(241).close()
        assertTrue(item("obj.coins").isStackable)
        assertTrue(item("obj.platinum").isStackable)
        previousTransactions = transactions.get(null)
        previousStorage = storage.get(null)
        previousVirtual = InvVirtualStorageHolder.instance
        with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
            ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
        }
    }

    @AfterAll fun restore() {
        transactions.set(null, previousTransactions)
        storage.set(null, previousStorage)
        InvVirtualStorageHolder.instance = previousVirtual
    }

    @Test fun `coins prompt for the requested token quantity and preserve the remainder`() {
        val f = Fixture("obj.coins", 12_345)
        f.begin()
        assertTrue(f.coroutine.isAwaiting(ResumePCountDialogInput::class))
        assertTrue(f.client.messages.any { "How many platinum tokens would you like to receive?" in it.toString() })
        f.answer(8)
        f.expect(4_345, 8)
    }

    @Test fun `requesting one token spends exactly one thousand coins`() {
        val f = Fixture("obj.coins", 2_345)
        f.begin(); f.answer(1); f.expect(1_345, 1)
        assertEquals(2_345L, f.wealth())
    }

    @Test fun `tokens exchange the requested quantity at exactly one thousand coins each`() {
        val f = Fixture("obj.platinum", 20)
        f.begin()
        assertTrue(f.client.messages.any { "How many platinum tokens would you like to exchange for coins?" in it.toString() })
        f.answer(7)
        f.expect(7_000, 13)
    }

    @Test fun `requests above the held amount exchange only the available currency`() {
        val coins = Fixture("obj.coins", 2_345)
        coins.begin(); coins.answer(Int.MAX_VALUE); coins.expect(345, 2)
        val tokens = Fixture("obj.platinum", 12)
        tokens.begin(); tokens.answer(Int.MAX_VALUE); tokens.expect(12_000, 0)
    }

    @Test fun `less than a thousand coins cannot buy a token`() {
        for ((held, request) in listOf(999 to 1, 999 to Int.MAX_VALUE)) {
            val f = Fixture("obj.coins", held)
            f.begin(); f.answer(request); f.expect(held, 0)
        }
    }

    @Test fun `zero negative and signed integer minimum inputs do not spend money`() {
        for (source in listOf("obj.coins", "obj.platinum")) {
            for (input in listOf(0, -1, Int.MIN_VALUE)) {
                val f = Fixture(source, 10_000)
                f.begin(); f.answer(input)
                assertEquals(10_000, f.player.inv.count(source))
            }
        }
    }

    @Test fun `closing the quantity prompt cancels without changing the inventory`() {
        val f = Fixture("obj.coins", 5_000)
        f.begin(); f.coroutine.cancel()
        assertTrue(f.completion?.exceptionOrNull() is CancellationException)
        assertFalse(f.coroutine.isSuspended)
        f.expect(5_000, 0)
    }

    @Test fun `replacement or removal of the selected item while prompting is safe`() {
        for (replacement in listOf(null, InvObj(item("obj.bones")))) {
            val f = Fixture("obj.coins", 5_000)
            f.begin(); f.player.inv[0] = replacement; f.answer(5_000)
            assertEquals(replacement, f.player.inv[0]); assertEquals(0, f.player.inv.count("obj.platinum"))
        }
    }

    @Test fun `quantity and destination changes during the prompt are revalidated`() {
        val f = Fixture("obj.coins", 10_000)
        f.begin()
        f.player.inv[0] = InvObj(item("obj.coins"), 2_999)
        f.player.inv[1] = InvObj(item("obj.platinum"), Int.MAX_VALUE - 1)
        f.answer(10_000)
        f.expect(1_999, Int.MAX_VALUE)
    }

    @Test fun `full inventories exchange a whole stack in its own slot`() {
        for ((source, count, expected) in listOf(
            Triple("obj.coins", 2_000, 0 to 2), Triple("obj.platinum", 2, 2_000 to 0),
        )) {
            val f = Fixture(source, count)
            f.fill(); f.begin(); f.answer(2)
            f.expect(expected.first, expected.second)
            assertEquals(0, f.player.inv.freeSpace())
            assertEquals(27, f.player.inv.count("obj.bones"))
        }
    }

    @Test fun `partial exchanges in a full inventory fail atomically without an existing destination stack`() {
        for ((source, count, input) in listOf(Triple("obj.coins", 2_345, 2), Triple("obj.platinum", 5, 2))) {
            val f = Fixture(source, count)
            f.fill(); val before = f.player.inv.objs.toList()
            f.begin(); f.answer(input)
            assertEquals(before, f.player.inv.objs.toList())
        }
    }

    @Test fun `partial exchange can merge into an existing stack in a full inventory`() {
        val f = Fixture("obj.coins", 2_345)
        f.fill(); f.player.inv[1] = InvObj(item("obj.platinum"), 5)
        f.begin(); f.answer(2); f.expect(345, 7)
        assertEquals(0, f.player.inv.freeSpace())
    }

    @Test fun `maximum token stack cannot overflow coins and excess tokens remain`() {
        val f = Fixture("obj.platinum", Int.MAX_VALUE)
        f.begin(); f.answer(Int.MAX_VALUE)
        f.expect(2_147_483_000, Int.MAX_VALUE - 2_147_483)
        assertEquals(Int.MAX_VALUE.toLong() * 1_000, f.wealth())
    }

    @Test fun `maximum token request converts the affordable part of a maximum coin stack`() {
        val f = Fixture("obj.coins", Int.MAX_VALUE)
        f.begin(); f.answer(Int.MAX_VALUE)
        f.expect(647, 2_147_483)
        assertEquals(Int.MAX_VALUE.toLong(), f.wealth())
    }

    @Test fun `exchange stops at the destination cap in both directions`() {
        val coins = Fixture("obj.coins", Int.MAX_VALUE)
        coins.player.inv[1] = InvObj(item("obj.platinum"), Int.MAX_VALUE - 1)
        coins.begin(); coins.answer(Int.MAX_VALUE); coins.expect(Int.MAX_VALUE - 1_000, Int.MAX_VALUE)
        val tokens = Fixture("obj.platinum", 100)
        tokens.player.inv[1] = InvObj(item("obj.coins"), Int.MAX_VALUE - 1_500)
        tokens.begin(); tokens.answer(100); tokens.expect(Int.MAX_VALUE - 500, 99)
    }

    @Test fun `full destination stacks never spend source currency`() {
        for (source in listOf("obj.coins", "obj.platinum")) {
            val f = Fixture(source, 5_000)
            f.player.inv[1] = InvObj(item(if (source == "obj.coins") "obj.platinum" else "obj.coins"), Int.MAX_VALUE)
            val before = f.player.inv.objs.toList()
            f.begin(); f.answer(5_000)
            assertEquals(before, f.player.inv.objs.toList())
        }
    }

    @Test fun `forced movement during a prompt cannot exchange remotely`() {
        val f = Fixture("obj.coins", 5_000)
        f.begin(); f.player.coords = CoordGrid(3222, 3218); f.answer(5_000); f.expect(5_000, 0)
    }

    @Test fun `unrelated items are left to the existing banknote handlers`() {
        val f = Fixture("obj.bones", 1)
        f.begin()
        assertFalse(f.coroutine.isSuspended)
        assertEquals(false, f.completion?.getOrThrow())
        assertTrue(f.client.messages.isEmpty())
    }

    private class Fixture(val source: String, count: Int) {
        val client = RecordingClient()
        val player = Player().apply {
            this.client = this@Fixture.client
            coords = CoordGrid(3208, 3220)
            currentMapClock = 100; processedMapClock = 100
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
            inv[0] = InvObj(item(source), count)
        }
        val coroutine = GameCoroutine("bank-currency-test")
        var completion: Result<Boolean>? = null
        fun fill() { for (slot in 1..27) player.inv[slot] = InvObj(item("obj.bones")) }
        fun begin() {
            player.activeCoroutine = coroutine
            val access = ProtectedAccess(player, coroutine, ProtectedAccessContextFactory.empty())
            val block: suspend () -> Boolean = { access.exchangeBankCurrency(0, item(source)) }
            block.startCoroutine(object : Continuation<Boolean> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Boolean>) { completion = result }
            })
            completion?.getOrThrow()
        }
        fun answer(count: Int) { coroutine.resumeWith(ResumePCountDialogInput(count)); checkNotNull(completion).getOrThrow() }
        fun expect(coins: Int, tokens: Int) {
            assertEquals(coins, player.inv.count("obj.coins"))
            assertEquals(tokens, player.inv.count("obj.platinum"))
        }
        fun wealth() = player.inv.count("obj.coins").toLong() + player.inv.count("obj.platinum").toLong() * 1_000
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
