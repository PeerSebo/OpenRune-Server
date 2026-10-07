package org.rsmod.content.areas.city.lumbridge.npcs

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
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
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.dialogue.align.TextAlignment
import org.rsmod.api.player.events.interact.NpcEvents
import org.rsmod.api.player.input.ResumePauseButtonInput
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CombatTutorSupplyTest {
    private val restored = mutableListOf<() -> Unit>()

    @BeforeAll
    fun setup() {
        ServerCacheManager.init(241).close()
        for ((owner, name) in listOf(
            "org.rsmod.api.invtx.InvTransactionsScriptKt" to "cachedInventoryTransactions",
            "org.rsmod.api.invtx.VirtualInvTransactionsKt" to "cachedPlayerItemStorage",
        )) {
            val field = Class.forName(owner).getDeclaredField(name).apply { isAccessible = true }
            val old = field.get(null)
            restored += { field.set(null, old) }
        }
        val oldStorage = InvVirtualStorageHolder.instance
        restored += { InvVirtualStorageHolder.instance = oldStorage }
        val context = ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache())
        with(InvTransactionsScript(PlayerItemStorage(emptySet()))) { context.startup() }
    }

    @AfterAll
    fun restore() { restored.asReversed().forEach { it() } }

    @Test
    fun `Magic claim rejects base level twenty even when drained and accepts boosted level nineteen`() {
        val f = Fixture()
        f.level("stat.magic", base = 20, current = 1)
        f.player.inv[0] = item("obj.coins", 100)
        f.interact(MAGIC, claim = true)
        assertEquals(100, f.player.inv.count("obj.coins"))
        assertEquals(0, f.player.inv.count("obj.mindrune"))
        assertEquals(0, f.player.inv.count("obj.airrune"))
        assertFalse(f.onCooldown())
        assertTrue(f.output().contains("below base Magic level 20"))

        f.level("stat.magic", base = 19, current = 99)
        f.interact(MAGIC, claim = true)
        assertEquals(30, f.player.inv.count("obj.mindrune"))
        assertEquals(30, f.player.inv.count("obj.airrune"))
        assertTrue(f.onCooldown())
    }

    @Test
    fun `Ranged claim uses base Ranged without restricting a player with high Magic`() {
        val f = Fixture()
        f.level("stat.magic", base = 99)
        f.level("stat.ranged", base = 20, current = 1)
        f.interact(RANGED, claim = true)
        assertTrue(f.player.inv.isEmpty())
        assertFalse(f.onCooldown())
        assertTrue(f.output().contains("below base Ranged level 20"))

        f.level("stat.ranged", base = 19, current = 99)
        f.interact(RANGED, claim = true)
        assertEquals(1, f.player.inv.count("obj.aide_shortbow"))
        assertEquals(25, f.player.inv.count("obj.aide_arrow"))
        assertTrue(f.onCooldown())
    }

    @Test
    fun `Magic remains available to low Magic players with high Ranged and melee levels`() {
        val f = Fixture()
        for (stat in listOf("stat.ranged", "stat.attack", "stat.strength", "stat.defence")) {
            f.level(stat, base = 99)
        }
        f.level("stat.magic", base = 19)
        f.interact(MAGIC, claim = true)
        assertEquals(30, f.player.inv.count("obj.mindrune"))
        assertEquals(30, f.player.inv.count("obj.airrune"))
    }

    @Test
    fun `each melee base skill independently prevents free gear at level twenty`() {
        for (stat in listOf("stat.attack", "stat.strength", "stat.defence")) {
            val f = Fixture()
            f.level(stat, base = 20, current = 1)
            f.interact(MELEE)
            assertTrue(f.player.inv.isEmpty(), "$stat must block both training items")
            assertTrue(f.output().contains("all below 20"), f.output())
            assertFalse(f.onCooldown())
        }
    }

    @Test
    fun `level nineteen melee players can still claim while boosted or skilled in other styles`() {
        val f = Fixture()
        for (stat in listOf("stat.attack", "stat.strength", "stat.defence")) {
            f.level(stat, base = 19, current = 99)
        }
        f.level("stat.magic", base = 99)
        f.level("stat.ranged", base = 99)
        f.interact(MELEE)
        assertEquals(1, f.player.inv.count("obj.aide_shortsword"), f.output())
        assertEquals(1, f.player.inv.count("obj.aide_shield"))
        assertFalse(f.onCooldown(), "Melee gear does not start the rune and arrow cooldown")
    }

    @Test
    fun `melee equipment checks the latest base level after the opening dialogue`() {
        val f = Fixture()
        f.level("stat.attack", base = 19)
        f.interact(MELEE, beforeFirstContinue = { f.level("stat.attack", base = 20) })
        assertTrue(f.player.inv.isEmpty())
        assertTrue(f.output().contains("all below 20"), f.output())
    }

    @Test
    fun `Magic and Ranged conversations explain the same restriction as Claim`() {
        for ((npc, stat, text) in listOf(
            Triple(MAGIC, "stat.magic", "below base Magic level 20"),
            Triple(RANGED, "stat.ranged", "below base Ranged level 20"),
        )) {
            val f = Fixture()
            f.level(stat, base = 99)
            f.interact(npc)
            assertTrue(f.output().contains(text), f.output())
            assertTrue(f.player.inv.isEmpty())
            assertFalse(f.onCooldown())
        }
    }

    @Test
    fun `eligible claims still enforce shared cooldown and bank ownership`() {
        val f = Fixture()
        f.interact(MAGIC, claim = true)
        f.player.inv.objs.fill(null)
        f.interact(RANGED, claim = true)
        assertTrue(f.player.inv.isEmpty())
        assertTrue(f.output().contains("Come back later"))

        val banked = Fixture()
        banked.bank[0] = item("obj.airrune")
        banked.interact(MAGIC, claim = true)
        assertTrue(banked.player.inv.isEmpty())
        assertEquals(1, banked.bank.count("obj.airrune"))
        assertFalse(banked.onCooldown())
    }

    @Test
    fun `eligible melee claims preserve already owned equipment and full inventories`() {
        val f = Fixture()
        f.bank[0] = item("obj.aide_shortsword")
        f.player.worn[0] = item("obj.aide_shield")
        f.interact(MELEE)
        assertTrue(f.player.inv.isEmpty())
        assertEquals(1, f.bank.count("obj.aide_shortsword"))
        assertEquals(1, f.player.worn.count("obj.aide_shield"))

        val full = Fixture()
        full.player.inv.objs.fill(item("obj.logs"))
        val before = full.player.inv.objs.toList()
        full.interact(MAGIC, claim = true)
        full.interact(RANGED, claim = true)
        full.interact(MELEE)
        assertEquals(before, full.player.inv.objs.toList())
        assertFalse(full.onCooldown())
    }

    @Test
    fun `a single empty slot cannot claim only half a rune or ranged supply bundle`() {
        for (npc in listOf(MAGIC, RANGED)) {
            val f = Fixture()
            f.player.inv.objs.fill(item("obj.logs"))
            f.player.inv[27] = null
            val before = f.player.inv.objs.toList()

            repeat(3) { f.interact(npc, claim = true) }

            assertEquals(before, f.player.inv.objs.toList())
            assertFalse(f.onCooldown())
            assertTrue(f.output().contains("enough inventory space"), f.output())
        }
    }

    @Test
    fun `two free slots grant each complete bundle and begin the shared cooldown`() {
        for ((npc, first, second) in listOf(
            Triple(MAGIC, "obj.mindrune" to 30, "obj.airrune" to 30),
            Triple(RANGED, "obj.aide_shortbow" to 1, "obj.aide_arrow" to 25),
        )) {
            val f = Fixture()
            f.player.inv.objs.fill(item("obj.logs"))
            f.player.inv[26] = null
            f.player.inv[27] = null

            f.interact(npc, claim = true)

            assertEquals(first.second, f.player.inv.count(first.first))
            assertEquals(second.second, f.player.inv.count(second.first))
            assertEquals(26, f.player.inv.count("obj.logs"))
            assertTrue(f.onCooldown())
        }
    }

    private class Fixture {
        private val client = RecordingClient()
        val player = Player().apply {
            this.client = this@Fixture.client
            coords = CoordGrid(3230, 3230)
            currentMapClock = 100
            processedMapClock = 100
            inv = inventory(28)
            worn = inventory(14)
        }
        val bank = inventory(800).also { player.invMap["inv.bank"] = it }
        private val events = EventBus()
        private val context = ProtectedAccessContextFactory.empty().copy(
            getRandom = { DefaultGameRandom(1) },
            getEventBus = { events },
            getAlignment = { TextAlignment() },
        )

        init {
            val scriptContext = ScriptContext(events, CheatCommandMap(), EngineQueueCache())
            with(MagicCombatTutor()) { scriptContext.startup() }
            with(RangedCombatTutor()) { scriptContext.startup() }
            with(MeleeCombatTutor()) { scriptContext.startup() }
        }

        fun level(stat: String, base: Int, current: Int = base) {
            player.statMap.setBaseLevel(stat, base.toByte())
            player.statMap.setCurrentLevel(stat, current.toByte())
        }

        fun onCooldown(): Boolean = ProtectedAccess(player, GameCoroutine(), context).aideTutorClaimOnCooldown()

        fun output(): String = client.messages.joinToString("\n").replace("<br>", " ")

        fun interact(type: String, claim: Boolean = false, beforeFirstContinue: () -> Unit = {}) {
            val coroutine = GameCoroutine("combat-tutor-test")
            player.activeCoroutine = coroutine
            val access = ProtectedAccess(player, coroutine, context)
            val npc = Npc(type, player.coords.translateX(1))
            var completion: Result<Unit>? = null
            val block: suspend () -> Unit = {
                val dispatched = if (claim) events.publish(access, NpcEvents.Op3(npc))
                    else events.publish(access, NpcEvents.Op1(npc))
                assertTrue(dispatched, "Missing production tutor operation: $type")
            }
            block.startCoroutine(object : Continuation<Unit> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<Unit>) { completion = result }
            })
            var pages = 0
            while (completion == null && pages++ < 10) {
                assertTrue(coroutine.isAwaiting(ResumePauseButtonInput::class), "Unexpected tutor suspension")
                if (pages == 1) beforeFirstContinue()
                val button = if (claim) "component.objectbox_double:pausebutton" else "component.chat_left:continue"
                coroutine.resumeWith(ResumePauseButtonInput(button, -1))
            }
            checkNotNull(completion) { "Tutor conversation did not finish" }.getOrThrow()
            player.activeCoroutine = null
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
        private const val MAGIC = "npc.aide_tutor_magic"
        private const val RANGED = "npc.aide_tutor_ranging"
        private const val MELEE = "npc.aide_tutor_melee"
        private fun inventory(size: Int): Inventory = Inventory(InventoryServerType(size = size), arrayOfNulls(size))
        private fun item(type: String, count: Int = 1): InvObj =
            InvObj(checkNotNull(ServerCacheManager.getItem(type.asRSCM())), count)
    }
}
