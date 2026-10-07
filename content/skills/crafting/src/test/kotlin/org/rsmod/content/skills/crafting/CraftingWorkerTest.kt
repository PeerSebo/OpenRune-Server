package org.rsmod.content.skills.crafting

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.types.InventoryServerType
import dev.openrune.types.ItemServerType
import dev.openrune.types.StatType
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactions
import org.rsmod.api.player.events.PlayerActionCompleteEvent
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.content.skills.Material
import org.rsmod.content.skills.crafting.util.CraftingConstants
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class CraftingWorkerTest {
    private val completions = mutableListOf<PlayerActionCompleteEvent>()
    private val restore = mutableListOf<() -> Unit>()

    @BeforeAll
    fun installDefinitions() {
        replaceMap(RSCM::class.java, "cache", definitions.mapValues { it.value.id } +
            (CraftingConstants.STAT_CRAFTING to 12))
        replaceMap(ServerCacheManager::class.java, "items", definitions.values.associateBy { it.id })
        replaceMap(ServerCacheManager::class.java, "statTypes", mapOf(12 to StatType(id = 12)))
        replaceField(
            Class.forName("org.rsmod.api.invtx.InvTransactionsScriptKt"),
            "cachedInventoryTransactions",
            InvTransactions(emptyMap(), emptyMap(), emptyMap(), emptySet(), emptySet()),
        )
        replaceField(
            Class.forName("org.rsmod.api.invtx.VirtualInvTransactionsKt"),
            "cachedPlayerItemStorage",
            PlayerItemStorage(emptySet()),
        )
    }

    @AfterAll
    fun restoreDefinitions() {
        restore.asReversed().forEach { it() }
    }

    @Test
    fun `craft stops if the last thread is gone after the batch was accepted`() {
        val access = access(CraftingConstants.NEEDLE, CraftingConstants.THREAD, LEATHER)
        assertTrue(runImmediate { access.canCraft(product(), verbose = false) })
        access.inv[1] = null

        assertFalse(runImmediate { access.craftOnce(product()) })
        assertEquals(1, access.inv.count(LEATHER))
        assertEquals(0, access.inv.count(GLOVES))
        assertEquals(0, access.player.statMap.getFineXP(CraftingConstants.STAT_CRAFTING))
    }

    @Test
    fun `craft stops if a required tool disappears before completion`() {
        val access = access(CraftingConstants.NEEDLE, CraftingConstants.THREAD, LEATHER)
        assertTrue(runImmediate { access.canCraft(product(), verbose = false) })
        access.inv[0] = null

        assertFalse(runImmediate { access.craftOnce(product()) })
        assertEquals(1, access.inv.count(LEATHER))
        assertEquals(1, access.inv.count(CraftingConstants.THREAD))
        assertEquals(0, access.player.statMap.getFineXP(CraftingConstants.STAT_CRAFTING))
    }

    @Test
    fun `craft stops when the temporary level required by the recipe expires`() {
        val access = access(CraftingConstants.COSTUME_NEEDLE, LEATHER)
        val product = product().copy(level = 10)
        access.player.statMap.setCurrentLevel(CraftingConstants.STAT_CRAFTING, 10)
        assertTrue(runImmediate { access.canCraft(product, verbose = false) })
        access.player.statMap.setCurrentLevel(CraftingConstants.STAT_CRAFTING, 9)

        assertFalse(runImmediate { access.craftOnce(product) })
        assertEquals(1, access.inv.count(LEATHER))
        assertEquals(0, access.player.statMap.getFineXP(CraftingConstants.STAT_CRAFTING))
    }

    @Test
    fun `valid full inventory craft replaces leather and awards XP once`() {
        val access = access(CraftingConstants.COSTUME_NEEDLE, LEATHER)
        fillRemainingSlots(access)

        assertTrue(runImmediate { access.craftOnce(product()) })
        assertEquals(0, access.inv.count(LEATHER))
        assertEquals(1, access.inv.count(GLOVES))
        assertEquals(1, access.inv.count(CraftingConstants.COSTUME_NEEDLE))
        assertEquals(150, access.player.statMap.getFineXP(CraftingConstants.STAT_CRAFTING))
        assertEquals(GLOVES, completions.single().target)
        assertFalse(runImmediate { access.craftOnce(product()) })
        assertEquals(150, access.player.statMap.getFineXP(CraftingConstants.STAT_CRAFTING))
    }

    @Test
    fun `an output that does not fit restores materials without awarding XP`() {
        val access = access(CraftingConstants.COSTUME_NEEDLE, LEATHER)
        fillRemainingSlots(access)

        assertFalse(runImmediate { access.craftOnce(product().copy(outputCount = 4)) })
        assertEquals(1, access.inv.count(LEATHER))
        assertEquals(0, access.inv.count(GLOVES))
        assertEquals(0, access.player.statMap.getFineXP(CraftingConstants.STAT_CRAFTING))
    }

    private fun product() = CraftingProduct(
        section = CraftingSection.NEEDLEWORK,
        output = GLOVES,
        inputs = listOf(Material(LEATHER, 1)),
        level = 1,
        xp = 150.0,
        ticks = listOf(3),
        tools = listOf(CraftingConstants.NEEDLE),
        consumesThread = true,
        actionName = "make leather gloves",
    )

    private fun access(vararg held: String): ProtectedAccess {
        completions.clear()
        val bus = EventBus().apply {
            subscribeUnbound(PlayerActionCompleteEvent::class.java) { completions += this }
        }
        val player = Player()
        player.inv = Inventory(InventoryServerType(id = 93, size = 28), arrayOfNulls(28))
        player.worn = Inventory(InventoryServerType(id = 94, size = 14), arrayOfNulls(14))
        player.statMap.setBaseLevel(CraftingConstants.STAT_CRAFTING, 99)
        player.statMap.setCurrentLevel(CraftingConstants.STAT_CRAFTING, 99)
        held.forEachIndexed { slot, item -> player.inv[slot] = InvObj(definitions.getValue(item)) }
        val context = ProtectedAccessContext(
            getRandom = { error("Unexpected random dependency") },
            getEventBus = { bus },
            getNpcList = { error("Unexpected NPC dependency") },
            getPlayerList = { error("Unexpected player list dependency") },
            getCollision = { error("Unexpected collision dependency") },
            getAreaChecker = { error("Unexpected area dependency") },
            getAlignment = { error("Unexpected dialogue dependency") },
            getLocInteractions = { error("Unexpected loc dependency") },
            getNpcInteractions = { error("Unexpected NPC interactions") },
            getPlayerInteractions = { error("Unexpected player interactions") },
            getHeldInteractions = { error("Unexpected held interactions") },
            getWornInteractions = { error("Unexpected worn interactions") },
            getMusicPlayer = { error("Unexpected music dependency") },
            getMarketPrices = { error("Unexpected market dependency") },
            getInstantHitProcessor = { error("Unexpected damage dependency") },
            getTeleportValidator = { error("Unexpected teleport dependency") },
            getWorldRepo = { error("Unexpected world dependency") },
            getHitModifier = { error("Unexpected hit modifier dependency") },
        )
        return ProtectedAccess(player, GameCoroutine("crafting-test"), context)
    }

    private fun fillRemainingSlots(access: ProtectedAccess) {
        access.inv.indices.filter { access.inv[it] == null }.forEach {
            access.inv[it] = InvObj(definitions.getValue(FILLER))
        }
    }

    private fun <T> runImmediate(action: suspend () -> T): T {
        var completed: Result<T>? = null
        action.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) { completed = result }
        })
        return checkNotNull(completed) { "Crafting commit unexpectedly suspended" }.getOrThrow()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <K, V> replaceMap(owner: Class<*>, fieldName: String, values: Map<K, V>) {
        val field = owner.getDeclaredField(fieldName).apply { isAccessible = true }
        val map = field.get(null) as MutableMap<K, V>
        val previous = map.toMap()
        restore += { map.clear(); map.putAll(previous) }
        map.clear()
        map.putAll(values)
    }

    private fun replaceField(owner: Class<*>, fieldName: String, value: Any) {
        val field = owner.getDeclaredField(fieldName).apply { isAccessible = true }
        val previous = field.get(null)
        restore += { field.set(null, previous) }
        field.set(null, value)
    }

    private companion object {
        const val LEATHER = "obj.leather"
        const val GLOVES = "obj.leather_gloves"
        const val FILLER = "obj.crafting_test_filler"
        val definitions = listOf(
            CraftingConstants.NEEDLE,
            CraftingConstants.COSTUME_NEEDLE,
            CraftingConstants.THREAD,
            LEATHER,
            GLOVES,
            FILLER,
        ).mapIndexed { index, internal -> internal to ItemServerType(id = index + 100, name = internal) }.toMap()
    }
}
