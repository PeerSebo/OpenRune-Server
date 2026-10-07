package org.rsmod.content.skills.fletching.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.types.InventoryServerType
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.content.skills.fletching.table.FletchingRecipe
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorage
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@ResourceLock("cachedInventoryTransactions")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SeekingFletchingTest {
    private val transactions = Class.forName("org.rsmod.api.invtx.InvTransactionsScriptKt")
        .getDeclaredField("cachedInventoryTransactions").apply { isAccessible = true }
    private val storage = Class.forName("org.rsmod.api.invtx.VirtualInvTransactionsKt")
        .getDeclaredField("cachedPlayerItemStorage").apply { isAccessible = true }
    private var previousTransactions: Any? = null
    private var previousStorage: Any? = null
    private var previousVirtual: InvVirtualStorage? = null

    @BeforeAll fun setup() {
        ServerCacheManager.init(241).close()
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

    @Test fun `bulk conversion consumes exactly one batch of 150`() {
        val access = access(InvObj("obj.adamant_arrow", 159),
            InvObj("obj.seeking_headless_arrow", 153))
        assertTrue(access.tryCraft(bulkRecipe()))
        assertEquals(9, access.inv.count("obj.adamant_arrow"))
        assertEquals(3, access.inv.count("obj.seeking_headless_arrow"))
        assertEquals(150, access.inv.count("obj.seeking_adamant_arrow"))
    }

    @Test fun `incomplete bulk conversion preserves every input`() {
        val access = access(InvObj("obj.adamant_arrow", 149),
            InvObj("obj.seeking_headless_arrow", 150))
        val before = access.inv.objs.toList()
        assertFalse(access.tryCraft(bulkRecipe()))
        assertEquals(before, access.inv.objs.toList())
    }

    @Test fun `full inventory rolls back a conversion whose ingredients retain their slots`() {
        val access = access(InvObj("obj.adamant_arrow", 159),
            InvObj("obj.seeking_headless_arrow", 153))
        for (slot in 2 until 28) access.inv[slot] = InvObj("obj.knife")
        val before = access.inv.objs.toList()
        assertFalse(access.tryCraft(bulkRecipe()))
        assertEquals(before, access.inv.objs.toList())
    }

    @Test fun `wooden blowpipes require both logs and the beak`() {
        val recipe = FletchingRecipe.table("fletching_carving")
            .single { it.output.internalName == "obj.camphor_blowpipe_empty" }
        val access = access(InvObj("obj.camphor_logs"), InvObj("obj.camphor_logs"))
        val before = access.inv.objs.toList()
        assertFalse(access.tryCraft(recipe))
        assertEquals(before, access.inv.objs.toList())
        access.inv[2] = InvObj("obj.squid_beak")
        assertTrue(access.tryCraft(recipe))
        assertEquals(0, access.inv.count("obj.camphor_logs"))
        assertEquals(0, access.inv.count("obj.squid_beak"))
        assertEquals(1, access.inv.count("obj.camphor_blowpipe_empty"))
    }

    @Test fun `fractional experience remains per individual arrow and original bow`() {
        val arrows = FletchingRecipe.table("fletching_arrows")
        assertEquals(2.5, arrows.single { it.output.internalName == "obj.seeking_iron_arrow" }.xp)
        assertEquals(2.5, arrows.single { it.output.internalName == "obj.iron_arrow" }.xp)
        assertEquals(83.3, FletchingRecipe.table("fletching_carving")
            .single { it.output.internalName == "obj.unstrung_magic_shortbow" }.xp)
        assertEquals(0.0, bulkRecipe().xp)
    }

    private fun bulkRecipe() = FletchingRecipe.table("fletching_special_assembly").single {
        it.output.internalName == "obj.seeking_adamant_arrow" && it.outputAmount == 150
    }

    private fun access(vararg items: InvObj): ProtectedAccess {
        val player = Player().apply {
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
            items.forEachIndexed { slot, item -> inv[slot] = item }
        }
        return ProtectedAccess(player, GameCoroutine(), ProtectedAccessContextFactory.empty())
    }
}
