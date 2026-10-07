package org.rsmod.content.minigames.mahoganyhomes

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import net.rsprot.protocol.game.outgoing.sound.SynthSound
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.game.process.player.PlayerQueueProcessor
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.events.interact.LocEvents
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.minigames.mahoganyhomes.configs.MahoganyHomesFurniture
import org.rsmod.content.minigames.mahoganyhomes.configs.MahoganyHomesHouse
import org.rsmod.content.minigames.mahoganyhomes.configs.MahoganyHomesLayouts
import org.rsmod.content.minigames.mahoganyhomes.configs.MahoganyHomesTier
import org.rsmod.content.minigames.mahoganyhomes.core.MahoganyHomesHotspotKind
import org.rsmod.content.minigames.mahoganyhomes.core.MahoganyHomesHotspots
import org.rsmod.content.minigames.mahoganyhomes.core.MahoganyHomesManager
import org.rsmod.content.minigames.mahoganyhomes.planksack.PlankSack
import org.rsmod.content.minigames.mahoganyhomes.planksack.PlankType
import org.rsmod.content.minigames.mahoganyhomes.scripts.ContractScript
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import sun.misc.Unsafe

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class MahoganyHomesContractTest {
    @Test
    fun `all saved contract tier and house ordinals retain their meaning`() {
        for ((varbit, parent) in mapOf(
            "mahogany_homes_points" to "mahogany_homes_points",
            "mahogany_homes_contracts_completed" to "mahogany_homes_contracts_completed",
            "mahogany_homes_tier" to "mahogany_homes_contract_state",
            "mahogany_homes_client" to "mahogany_homes_contract_state",
            "mahogany_homes_layout" to "mahogany_homes_contract_state",
        )) {
            assertEquals("varp.$parent".asRSCM(), checkNotNull(ServerCacheManager.getVarbit("varbit.$varbit".asRSCM())).varp, varbit)
        }
        val houseNames = fixture("houses").map { it[0] }
        val tierNames = fixture("tiers").map { it[0] }
        for ((house, name) in houseNames.withIndex()) {
            for ((tier, tierName) in tierNames.withIndex()) {
                val player = player()
                val packed = tier or ((house + 1) shl 2) or (9 shl 6)
                VarPlayerIntMapSetter.set(player, "varp.mahogany_homes_contract_state", packed)
                assertTrue(MahoganyHomesManager.hasActiveContract(player))
                assertEquals(name, MahoganyHomesManager.currentHouse(player)?.name)
                assertEquals(tierName, MahoganyHomesManager.currentTier(player).name)
                assertEquals(9, player.vars["varbit.mahogany_homes_layout"])
                assertEquals(packed, player.vars["varp.mahogany_homes_contract_state"])
            }
        }
        val empty = player()
        assertFalse(MahoganyHomesManager.hasActiveContract(empty))
        assertNull(MahoganyHomesManager.currentHouse(empty))
        assertEquals(MahoganyHomesTier.BEGINNER, MahoganyHomesManager.currentTier(empty))
    }

    @Test
    fun `eligibility uses current level and assignment keeps random draw order and unused slots clear`() {
        val tiers = fixture("tiers")
        for (level in listOf(0, 1, 19, 20, 49, 50, 69, 70, 99)) {
            val player = player()
            player.statMap.setBaseLevel("stat.construction", 99)
            player.statMap.setCurrentLevel("stat.construction", level.toByte())
            val eligible = tiers.filter { level >= it[1].toInt() }.map { it[0] }
            assertEquals(eligible, MahoganyHomesManager.eligibleTiers(player).map { it.name })
            val draws = mutableListOf<Int>()
            val house = MahoganyHomesManager.rollAndAssign(player, { bound -> draws += bound; 0 }, false)
            if (eligible.isEmpty()) {
                assertNull(house)
                assertTrue(draws.isEmpty())
            } else {
                assertEquals(MahoganyHomesHouse.BOB, house)
                assertEquals(listOf(eligible.size, 12), draws)
                assertEquals(MahoganyHomesTier.BEGINNER, MahoganyHomesManager.currentTier(player))
            }
        }
        val player = player()
        for (house in MahoganyHomesHouse.entries) {
            for (slot in 1..8) MahoganyHomesManager.writeDisplaySlot(player, slot, 8)
            val draws = ArrayDeque(listOf(3, house.ordinal))
            assertEquals(house, MahoganyHomesManager.rollAndAssign(player, { draws.removeFirst() }, false))
            assertTrue(draws.isEmpty())
            val layout = MahoganyHomesLayouts.layoutFor(house)
            for (slot in 1..8) {
                val expected = when (layout.firstOrNull { it.hotspotIndex == slot }?.kind) {
                    MahoganyHomesHotspotKind.BUILD -> 3
                    MahoganyHomesHotspotKind.REPAIR -> 1
                    null -> 0
                }
                assertEquals(expected, MahoganyHomesManager.readDisplaySlot(player, slot), "${house.name} slot $slot")
            }
            assertEquals(layout.size, MahoganyHomesManager.remainingSlots(player).size)
        }
    }

    @Test
    fun `last tier assignment preserves one house draw while an ineligible tier is rerolled`() {
        val player = player()
        VarPlayerIntMapSetter.set(player, "varbit.mahogany_homes_tier", 2)
        val draws = mutableListOf<Int>()
        MahoganyHomesManager.rollAndAssign(player, { bound -> draws += bound; 0 }, true)
        assertEquals(listOf(12), draws)
        assertEquals(MahoganyHomesTier.ADEPT, MahoganyHomesManager.currentTier(player))
        player.statMap.setCurrentLevel("stat.construction", 20)
        draws.clear()
        MahoganyHomesManager.rollAndAssign(player, { bound -> draws += bound; bound - 1 }, true)
        assertEquals(listOf(2, 12), draws)
        assertEquals(MahoganyHomesTier.NOVICE, MahoganyHomesManager.currentTier(player))
        assertEquals(MahoganyHomesHouse.BARBARA, MahoganyHomesManager.currentHouse(player))
    }

    @Test
    fun `completion preserves original rewards outfit bonus and contract counters`() {
        for ((ordinal, tier) in fixture("tiers").withIndex()) {
            for (outfit in listOf(false, true)) {
                val player = player()
                assign(player, ordinal)
                MahoganyHomesManager.addPoints(player, 10)
                VarPlayerIntMapSetter.set(player, "varbit.mahogany_homes_contracts_completed", 29999)
                if (outfit) {
                    listOf("construction_hat", "construction_shirt", "construction_trousers", "construction_boots")
                        .forEachIndexed { slot, item -> player.worn[slot] = InvObj("obj.$item") }
                }
                val house = checkNotNull(MahoganyHomesManager.currentHouse(player))
                assertFalse(MahoganyHomesManager.isComplete(player))
                for (slot in MahoganyHomesLayouts.layoutFor(house)) {
                    val state = if (slot.kind == MahoganyHomesHotspotKind.BUILD) 5 + ordinal else 0
                    MahoganyHomesManager.writeDisplaySlot(player, slot.hotspotIndex, state)
                }
                assertTrue(MahoganyHomesManager.isComplete(player))
                val result = MahoganyHomesManager.completeContract(player)
                assertEquals(tier[5].toDouble() * if (outfit) 1.025 else 1.0, result.xp, 0.0000001)
                assertEquals(tier[6].toInt(), result.points)
                assertEquals(10 + tier[6].toInt(), MahoganyHomesManager.getPoints(player))
                assertEquals(30000, MahoganyHomesManager.getContractsCompleted(player))
                assertFalse(MahoganyHomesManager.hasActiveContract(player))
                assertEquals(ordinal, MahoganyHomesManager.currentTier(player).ordinal)
            }
        }
    }

    @Test
    fun `building through hotspot events consumes the tier plank and delays XP and state`() {
        for ((ordinal, tier) in fixture("tiers").withIndex()) {
            val f = Fixture(ordinal)
            val loc = MahoganyHomesHouse.BOB.hotspots.first {
                val def = checkNotNull(MahoganyHomesHotspots.get(it))
                def.kind == MahoganyHomesHotspotKind.BUILD && MahoganyHomesFurniture.buildPlankCost(checkNotNull(def.furnitureKind)) == 2
            }
            val slot = checkNotNull(MahoganyHomesHotspots.get(loc)).slot
            f.give("obj.hammer")
            f.give("obj.poh_saw")
            f.give(tier[2], 3)
            f.op(loc, 2)
            assertEquals(4, MahoganyHomesManager.readDisplaySlot(f.player, slot))
            f.op(loc, 4)
            assertEquals(1, f.player.inv.count(tier[2]))
            assertEquals(0, f.xp())
            assertEquals(4, MahoganyHomesManager.readDisplaySlot(f.player, slot))
            assertEquals("seq.human_poh_build".asRSCM(), f.player.pendingSequence.id)
            assertEquals(listOf(SynthSound("synth.POH_BUILD_WOOD".asRSCM(), 1, 0)), f.client.messages.filterIsInstance<SynthSound>())
            f.tick()
            assertEquals((tier[3].toDouble() * 2 * 10).toInt(), f.xp())
            assertEquals(5 + ordinal, MahoganyHomesManager.readDisplaySlot(f.player, slot))
            assertEquals(EntitySeq.ZERO, f.player.pendingSequence)
            assertEquals(0, f.player.weakQueueList.count("queue.mahogany_homes_build"))
        }
    }

    @Test
    fun `repair actions preserve per tier plank XP and flat steel bar XP`() {
        for ((ordinal, tier) in fixture("tiers").withIndex()) {
            for (bar in listOf(false, true)) {
                val target = MahoganyHomesHouse.entries.firstNotNullOf { house ->
                    house.hotspots.firstOrNull { loc ->
                        val def = checkNotNull(MahoganyHomesHotspots.get(loc))
                        def.kind == MahoganyHomesHotspotKind.REPAIR &&
                            MahoganyHomesFurniture.repairsWithBar(checkNotNull(def.furnitureKind)) == bar
                    }?.let { house to it }
                }
                val f = Fixture(ordinal, target.first)
                val slot = checkNotNull(MahoganyHomesHotspots.get(target.second)).slot
                f.give("obj.hammer")
                f.give("obj.poh_saw")
                val material = if (bar) "obj.steel_bar" else tier[2]
                f.give(material, 2)
                f.op(target.second, 3)
                assertEquals(1, f.player.inv.count(material))
                assertEquals(1, MahoganyHomesManager.readDisplaySlot(f.player, slot))
                assertEquals(0, f.xp())
                f.tick()
                assertEquals(if (bar) 1200 else (tier[4].toDouble() * 10).toInt(), f.xp())
                assertEquals(0, MahoganyHomesManager.readDisplaySlot(f.player, slot))
                assertEquals(EntitySeq.ZERO, f.player.pendingSequence)
                val sound = if (bar) "synth.POH_BUILD_METAL" else "synth.POH_BUILD_WOOD"
                assertEquals(listOf(SynthSound(sound.asRSCM(), 1, 0)), f.client.messages.filterIsInstance<SynthSound>())
            }
        }
    }

    @Test
    fun `wrong house missing tools and insufficient planks leave furniture and materials intact`() {
        val loc = MahoganyHomesHouse.BOB.hotspots.first {
            val def = checkNotNull(MahoganyHomesHotspots.get(it))
            def.kind == MahoganyHomesHotspotKind.BUILD && MahoganyHomesFurniture.buildPlankCost(checkNotNull(def.furnitureKind)) == 2
        }
        val slot = checkNotNull(MahoganyHomesHotspots.get(loc)).slot
        for (failure in listOf("wrong house", "hammer", "saw", "planks")) {
            val f = Fixture()
            MahoganyHomesManager.writeDisplaySlot(f.player, slot, 4)
            if (failure == "wrong house") VarPlayerIntMapSetter.set(f.player, "varbit.mahogany_homes_client", 2)
            if (failure != "hammer") f.give("obj.hammer")
            if (failure != "saw") f.give("obj.poh_saw")
            if (failure != "planks") f.give("obj.woodplank", 2)
            val before = f.player.inv.objs.toList()
            f.op(loc, 4)
            assertEquals(before, f.player.inv.objs.toList(), failure)
            assertEquals(4, MahoganyHomesManager.readDisplaySlot(f.player, slot), failure)
            assertEquals(0, f.xp(), failure)
            assertEquals(0, f.player.weakQueueList.count("queue.mahogany_homes_build"), failure)
            assertEquals(EntitySeq.NULL, f.player.pendingSequence, failure)
        }
    }

    @Test
    fun `build consumes available inventory planks before the remaining sack material`() {
        val house = MahoganyHomesHouse.BOB
        val loc = house.hotspots.first {
            val def = checkNotNull(MahoganyHomesHotspots.get(it))
            def.kind == MahoganyHomesHotspotKind.BUILD && MahoganyHomesFurniture.buildPlankCost(checkNotNull(def.furnitureKind)) == 2
        }
        val f = Fixture()
        f.give("obj.hammer")
        f.give("obj.poh_saw")
        f.give("obj.woodplank")
        f.give("obj.plank_sack")
        PlankSack.depositUpTo(f.player, PlankType.PLAIN, 3)
        f.op(loc, 2)
        f.op(loc, 4)
        assertEquals(0, f.player.inv.count("obj.woodplank"))
        assertEquals(2, PlankSack.stored(f.player, PlankType.PLAIN))
        f.tick()
        assertEquals(450, f.xp())
    }

    private class Fixture(tier: Int = 0, house: MahoganyHomesHouse = MahoganyHomesHouse.BOB) {
        val clock = MapClock(100)
        val client = RecordingClient()
        val player = player().apply { client = this@Fixture.client }
        private val events = EventBus()
        private val contextFactory = unused<ProtectedAccessContextFactory>().also {
            it.javaClass.getDeclaredField("eventBus").apply { isAccessible = true }.set(it, events)
        }
        private val launcher = ProtectedAccessLauncher(contextFactory)
        private val queues = PlayerQueueProcessor(events, launcher)

        init {
            assign(player, tier, house)
            with(ContractScript()) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
        }

        fun give(item: String, count: Int = 1) {
            repeat(count) { player.inv[player.inv.indices.first { player.inv[it] == null }] = InvObj(item) }
        }

        fun op(symbol: String, op: Int) {
            val type = checkNotNull(ServerCacheManager.getObject(symbol.asRSCM()))
            val loc = BoundLocInfo(LocInfo(2, player.coords, LocEntity(type.id, 10, 0)), type)
            val event = when (op) {
                2 -> LocEvents.Op2(loc, loc, type)
                3 -> LocEvents.Op3(loc, loc, type)
                4 -> LocEvents.Op4(loc, loc, type)
                else -> error("Unexpected test op")
            }
            assertTrue(launcher.launch(player) { assertTrue(events.publish(this, event), symbol) })
            queues.process(player)
        }

        fun tick() {
            clock.tick()
            player.currentMapClock = clock.cycle
            player.processedMapClock = clock.cycle
            queues.process(player)
        }

        fun xp() = player.statMap.getFineXP("stat.construction")
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
        checkNotNull(javaClass.getResourceAsStream("/mahogany-homes-$name.tsv"))
            .bufferedReader().use { reader -> reader.readLines().map { it.split('\t') } }

    companion object {
        private val restoreGlobals = mutableListOf<() -> Unit>()

        private fun player() = Player().apply {
            coords = CoordGrid(3200, 3200)
            currentMapClock = 100
            processedMapClock = 100
            pendingSequence = EntitySeq.NULL
            statMap.setBaseLevel("stat.construction", 99)
            statMap.setCurrentLevel("stat.construction", 99)
            inv = Inventory.create("inv.inv")
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
        }

        private fun assign(player: Player, tier: Int, house: MahoganyHomesHouse = MahoganyHomesHouse.BOB) {
            val draws = ArrayDeque(listOf(tier, house.ordinal))
            assertEquals(house, MahoganyHomesManager.rollAndAssign(player, { draws.removeFirst() }, false))
            assertTrue(draws.isEmpty())
        }

        @JvmStatic @BeforeAll fun cache() {
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
        }

        @JvmStatic @AfterAll fun restore() {
            restoreGlobals.asReversed().forEach { it() }
            restoreGlobals.clear()
        }

        private inline fun <reified T> unused(): T {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
            return (field.get(null) as Unsafe).allocateInstance(T::class.java) as T
        }
    }
}
