package org.rsmod.content.skills.magic.utility

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import net.rsprot.protocol.game.outgoing.sound.SynthSound
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.game.process.player.PlayerQueueProcessor
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.cheat.adminInfiniteRunes
import org.rsmod.api.player.events.PlayerActionCompleteEvent
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.IfOverlayButtonT
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.api.spells.runes.combo.ComboRuneRepository
import org.rsmod.api.spells.runes.compact.CompactRuneRepository
import org.rsmod.api.spells.runes.fake.FakeRuneRepository
import org.rsmod.api.spells.runes.staves.StaffSubstituteRepository
import org.rsmod.api.spells.runes.subs.RuneSubstituteRepository
import org.rsmod.api.spells.runes.unlimited.UnlimitedRuneRepository
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.seq.EntitySeq
import org.rsmod.game.spot.EntitySpotanim
import org.rsmod.game.ui.Component
import org.rsmod.plugin.scripts.ScriptContext
import sun.misc.Unsafe

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class JewelleryEnchantDispatchTest {
    @Test
    fun `every original product casts through its spell event with unchanged shape presentation`() {
        val tiers = fixture("tiers").associateBy { it[1] }
        for (product in fixture("products")) {
            val tier = tiers.getValue(product[0])
            val f = Fixture()
            f.player.adminInfiniteRunes = true
            for (slot in f.player.inv.indices) f.player.inv[slot] = InvObj("obj.pot_empty")
            f.player.inv[5] = InvObj(product[2])
            val expectedInventory = f.player.inv.objs.toMutableList().apply {
                this[5] = InvObj(product[3])
            }
            f.click(tier[2], product[2], 5)
            assertEquals(expectedInventory, f.player.inv.objs.toList(), product[2])
            assertEquals(103, f.player.actionDelay, product[2])
            val ring = product[4] == "Ring"
            val sequence = if (ring) "seq.human_cast_enchantring" else tier[3]
            val spot = if (ring) "spotanim.enchant_ring" else tier[4]
            val sound = if (ring) tier[6] else tier[5]
            assertEquals(sequence.asRSCM(), f.player.pendingSequence.id, product[2])
            assertEquals(1, f.player.pendingSpotanims.size)
            assertEquals(EntitySpotanim(spot.asRSCM(), 0, 92, 0), EntitySpotanim(f.player.pendingSpotanims.getLong(0)))
            assertEquals(listOf(SynthSound(sound.asRSCM(), 1, 0)), f.client.messages.filterIsInstance<SynthSound>())
            assertEquals(listOf(RunClientScript(915, listOf(6))), f.client.messages.filterIsInstance<RunClientScript>())
            val spell = checkNotNull(spells.resolveStandardSpell(tier[2]))
            assertEquals((spell.castXp * 10).toInt(), f.player.statMap.getFineXP("stat.magic"), product[2])
            assertEquals(0, f.player.queueList.count(ENCHANT_QUEUE))
            assertEquals(product[3], f.completions.single().target)
        }
    }

    @Test
    fun `successful level one enchant consumes each native rune once and awards fractional xp`() {
        val f = Fixture()
        f.player.inv[0] = InvObj("obj.sapphire_ring")
        f.player.inv[1] = InvObj("obj.cosmicrune", 2)
        f.player.inv[2] = InvObj("obj.waterrune", 2)
        f.click(LEVEL_ONE, "obj.sapphire_ring", 0)
        assertEquals(InvObj("obj.ring_of_recoil"), f.player.inv[0])
        assertEquals(1, f.player.inv.count("obj.cosmicrune"))
        assertEquals(1, f.player.inv.count("obj.waterrune"))
        assertEquals(175, f.player.statMap.getFineXP("stat.magic"))
        assertEquals(103, f.player.actionDelay)
    }

    @Test
    fun `invalid targets missing runes and spell restrictions reject before payment or effects`() {
        for (failure in listOf("wrong tier", "stale slot", "negative slot", "overflow slot", "null item", "missing runes", "level", "spellbook")) {
            val f = Fixture()
            f.player.inv[0] = InvObj("obj.sapphire_ring")
            f.player.inv[1] = InvObj("obj.cosmicrune", 2)
            f.player.inv[2] = InvObj("obj.waterrune", 2)
            var input: String? = "obj.sapphire_ring"
            var slot = 0
            when (failure) {
                "wrong tier" -> {
                    input = "obj.emerald_ring"
                    f.player.inv[0] = InvObj(input)
                }
                "stale slot" -> f.player.inv[0] = InvObj("obj.sapphire_necklace")
                "negative slot" -> slot = -1
                "overflow slot" -> slot = f.player.inv.size
                "null item" -> input = null
                "missing runes" -> f.player.inv[2] = null
                "level" -> f.player.statMap.setCurrentLevel("stat.magic", 1)
                "spellbook" -> VarPlayerIntMapSetter.set(f.player, "varbit.spellbook", 1)
            }
            val before = f.player.inv.objs.toList()
            f.click(LEVEL_ONE, input, slot)
            assertEquals(before, f.player.inv.objs.toList(), failure)
            f.assertNoCast(failure)
        }
    }

    @Test
    fun `rapid clicks replace the queued target and perform only its final cast at the delay boundary`() {
        val f = Fixture()
        f.player.inv[0] = InvObj("obj.sapphire_ring")
        f.player.inv[1] = InvObj("obj.sapphire_necklace")
        f.player.inv[2] = InvObj("obj.opal_bracelet")
        f.player.inv[3] = InvObj("obj.cosmicrune", 3)
        f.player.inv[4] = InvObj("obj.waterrune", 3)
        f.click(LEVEL_ONE, "obj.sapphire_ring", 0)
        f.clearPresentation()
        f.click(LEVEL_ONE, "obj.sapphire_necklace", 1)
        f.click(LEVEL_ONE, "obj.opal_bracelet", 2)
        assertEquals(1, f.player.queueList.count(ENCHANT_QUEUE))
        assertEquals(2, f.player.inv.count("obj.cosmicrune"))
        assertEquals(175, f.player.statMap.getFineXP("stat.magic"))
        repeat(2) {
            f.nextTick()
            assertEquals(InvObj("obj.opal_bracelet"), f.player.inv[2])
            assertEquals(175, f.player.statMap.getFineXP("stat.magic"))
        }
        f.nextTick()
        assertEquals(InvObj("obj.sapphire_necklace"), f.player.inv[1])
        assertEquals(InvObj("obj.expeditious_bracelet"), f.player.inv[2])
        assertEquals(1, f.player.inv.count("obj.cosmicrune"))
        assertEquals(1, f.player.inv.count("obj.waterrune"))
        assertEquals(350, f.player.statMap.getFineXP("stat.magic"))
        assertEquals(106, f.player.actionDelay)
        assertEquals(0, f.player.queueList.count(ENCHANT_QUEUE))
        repeat(6) { f.nextTick() }
        assertEquals(350, f.player.statMap.getFineXP("stat.magic"))
        assertEquals(1, f.client.messages.filterIsInstance<SynthSound>().size)
    }

    @Test
    fun `queued cast revalidates its exact inventory slot and current rune supply`() {
        for (failure in listOf("swapped slot", "removed item", "removed rune")) {
            val f = Fixture()
            f.player.actionDelay = 103
            f.player.inv[0] = InvObj("obj.sapphire_ring")
            f.player.inv[1] = InvObj("obj.cosmicrune")
            f.player.inv[2] = InvObj("obj.waterrune")
            f.click(LEVEL_ONE, "obj.sapphire_ring", 0)
            assertEquals(1, f.player.queueList.count(ENCHANT_QUEUE))
            when (failure) {
                "swapped slot" -> {
                    f.player.inv[0] = InvObj("obj.sapphire_necklace")
                    f.player.inv[3] = InvObj("obj.sapphire_ring")
                }
                "removed item" -> f.player.inv[0] = null
                "removed rune" -> f.player.inv[2] = null
            }
            val before = f.player.inv.objs.toList()
            repeat(6) { f.nextTick() }
            assertEquals(before, f.player.inv.objs.toList(), failure)
            assertEquals(0, f.player.statMap.getFineXP("stat.magic"), failure)
            assertEquals(103, f.player.actionDelay, failure)
            assertTrue(f.client.messages.none { it is SynthSound }, failure)
            assertEquals(0, f.player.queueList.count(ENCHANT_QUEUE), failure)
        }
    }

    private class Fixture {
        val clock = MapClock(100)
        val client = RecordingClient()
        val player = Player().apply {
            this.client = this@Fixture.client
            currentMapClock = clock.cycle
            processedMapClock = clock.cycle
            pendingSequence = EntitySeq.NULL
            statMap.setBaseLevel("stat.magic", 99)
            statMap.setCurrentLevel("stat.magic", 99)
            inv = Inventory.create("inv.inv")
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
        }
        private val events = EventBus()
        val completions = mutableListOf<PlayerActionCompleteEvent>()
        private val contextFactory = unused<ProtectedAccessContextFactory>().also {
            it.javaClass.getDeclaredField("eventBus").apply { isAccessible = true }.set(it, events)
        }
        private val launcher = ProtectedAccessLauncher(contextFactory)
        private val queues = PlayerQueueProcessor(events, launcher)

        init {
            events.subscribeUnbound(PlayerActionCompleteEvent::class.java) { completions += this }
            with(JewelleryEnchantScript(events, launcher, spells, runes)) {
                ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        fun click(spellObj: String, targetObj: String?, slot: Int) {
            val spell = checkNotNull(spells.resolveStandardSpell(spellObj))
            val target = targetObj?.let { checkNotNull(ServerCacheManager.getItem(it.asRSCM())) }
            val event = IfOverlayButtonT(
                player, -1, null, slot, target,
                Component(spell.component.packed), Component("component.inventory:items".asRSCM()),
            )
            assertTrue(events.publish(event), "Missing enchant spell event: $spellObj")
        }

        fun nextTick() {
            clock.tick()
            player.currentMapClock = clock.cycle
            player.processedMapClock = clock.cycle
            queues.process(player)
        }

        fun clearPresentation() {
            player.pendingSequence = EntitySeq.NULL
            player.pendingSpotanims.clear()
            client.messages.clear()
        }

        fun assertNoCast(message: String) {
            assertTrue(completions.isEmpty(), message)
            assertEquals(-1, player.actionDelay, message)
            assertEquals(0, player.statMap.getFineXP("stat.magic"), message)
            assertEquals(EntitySeq.NULL, player.pendingSequence, message)
            assertTrue(player.pendingSpotanims.isEmpty(), message)
            assertTrue(client.messages.none { it is SynthSound || it is RunClientScript }, message)
            assertEquals(0, player.queueList.count(ENCHANT_QUEUE), message)
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

    private fun fixture(name: String): List<List<String>> =
        checkNotNull(javaClass.getResourceAsStream("/jewellery-enchant-$name.tsv"))
            .bufferedReader().use { reader -> reader.readLines().map { it.split('\t') } }

    companion object {
        private const val LEVEL_ONE = "obj.07_enchant_amulet_lvl1"
        private const val ENCHANT_QUEUE = "queue.jewellery_enchant"
        private val restoreGlobals = mutableListOf<() -> Unit>()
        private lateinit var spells: MagicSpellRegistry
        private lateinit var runes: MagicRuneManager

        @JvmStatic @BeforeAll
        fun cache() {
            ServerCacheManager.init(241).close()
            for ((owner, name) in listOf(
                "org.rsmod.api.invtx.InvTransactionsScriptKt" to "cachedInventoryTransactions",
                "org.rsmod.api.invtx.VirtualInvTransactionsKt" to "cachedPlayerItemStorage",
            )) {
                val field = Class.forName(owner).getDeclaredField(name).apply { isAccessible = true }
                val previous = field.get(null)
                restoreGlobals += { field.set(null, previous) }
            }
            val previous = InvVirtualStorageHolder.instance
            restoreGlobals += { InvVirtualStorageHolder.instance = previous }
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
                ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
            }
            spells = initialized(MagicSpellRegistry())
            runes = MagicRuneManager(
                initialized(FakeRuneRepository()), initialized(ComboRuneRepository()),
                initialized(CompactRuneRepository()), initialized(UnlimitedRuneRepository()),
                initialized(StaffSubstituteRepository()), initialized(RuneSubstituteRepository()),
                emptySet(),
            )
        }

        @JvmStatic @AfterAll
        fun restore() {
            restoreGlobals.asReversed().forEach { it() }
            restoreGlobals.clear()
        }

        private fun <T : Any> initialized(value: T): T = value.also {
            it.javaClass.declaredMethods.single { method -> method.name.startsWith("init") && method.parameterCount == 0 }
                .apply { isAccessible = true }.invoke(it)
        }

        private inline fun <reified T> unused(): T {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
            return (field.get(null) as Unsafe).allocateInstance(T::class.java) as T
        }
    }
}
