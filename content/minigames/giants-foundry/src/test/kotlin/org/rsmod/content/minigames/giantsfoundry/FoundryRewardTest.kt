package org.rsmod.content.minigames.giantsfoundry

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.util.UncheckedType
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.random.GameRandom
import org.rsmod.content.minigames.giantsfoundry.configs.FoundryData
import org.rsmod.content.minigames.giantsfoundry.configs.FoundryOrePackData
import org.rsmod.content.minigames.giantsfoundry.state.foundryReputation
import org.rsmod.content.quest.manager.QuestRequirementMode
import org.rsmod.content.quest.manager.QuestRequirementPolicy
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@ResourceLock("QuestRequirements")
@OptIn(UncheckedType::class)
class FoundryRewardTest {
    @Test fun `all native item rewards exchange exactly their catalogue cost`() {
        withPolicy(QuestRequirementPolicy()) {
            for (reward in FoundryData.rewards.filter { it.item != null }) {
                val f = Fixture()
                f.player.foundryReputation = reward.cost
                assertTrue(f.service.purchase(f.player, reward.index), reward.item)
                assertEquals(0, f.player.foundryReputation)
                assertEquals(1, f.player.inv.count(requireNotNull(reward.item)))
            }
        }
    }

    @Test fun `mould unlock works with a full inventory and cannot charge twice`() {
        val f = Fixture()
        f.fill()
        val reward = FoundryData.rewards.first { it.mould != null }
        val unlock = requireNotNull(reward.mould?.unlockVarbit)
        f.player.foundryReputation = reward.cost * 2
        assertTrue(f.service.purchase(f.player, reward.index))
        assertEquals(1, f.player.vars[unlock])
        assertEquals(reward.cost, f.player.foundryReputation)
        assertFalse(f.service.purchase(f.player, reward.index))
        assertEquals(reward.cost, f.player.foundryReputation)
        assertEquals(28, f.player.inv.count("obj.bucket_empty"))
    }

    @Test fun `insufficient points and full inventory preserve both sides of purchase`() {
        val f = Fixture()
        val reward = FoundryData.rewards.first { it.item == FoundryRewardService.GROG }
        f.player.foundryReputation = reward.cost - 1
        assertFalse(f.service.purchase(f.player, reward.index))
        assertTrue(f.player.inv.isEmpty())
        f.player.foundryReputation = reward.cost
        f.fill()
        assertFalse(f.service.purchase(f.player, reward.index))
        assertEquals(reward.cost, f.player.foundryReputation)
        assertEquals(28, f.player.inv.count("obj.bucket_empty"))
    }

    @Test fun `bulk purchase preserves exact count and rejects forged amounts`() {
        val f = Fixture()
        val reward = FoundryData.rewards.first { it.item == "obj.smithing_catalyst" }
        f.player.foundryReputation = reward.cost * 10
        f.fill()
        f.player.inv[0] = InvObj("obj.smithing_catalyst")
        f.player.inv[1] = InvObj("obj.smithing_catalyst")
        assertFalse(f.service.purchase(f.player, reward.index, 10))
        assertEquals(2, f.player.inv.count("obj.smithing_catalyst"))
        assertEquals(reward.cost * 10, f.player.foundryReputation)
        for (slot in 2..11) f.player.inv[slot] = null
        for (amount in listOf(0, -1, 11, Int.MAX_VALUE)) assertFalse(f.service.purchase(f.player, reward.index, amount))
        assertFalse(f.service.purchase(f.player, 0))
        assertFalse(f.service.purchase(f.player, 25))
        assertTrue(f.service.purchase(f.player, reward.index, 10))
        assertEquals(12, f.player.inv.count("obj.smithing_catalyst"))
        assertEquals(0, f.player.foundryReputation)
    }

    @Test fun `double mould honours actual native completion and configured quest policy`() {
        val reward = FoundryData.rewards.first { it.item == FoundryRewardService.DOUBLE_MOULD }
        withPolicy(QuestRequirementPolicy(QuestRequirementMode.RespectProgress)) {
            val f = Fixture()
            f.player.foundryReputation = reward.cost
            assertFalse(f.service.purchase(f.player, reward.index))
            assertEquals(reward.cost, f.player.foundryReputation)
            VarPlayerIntMapSetter.set(f.player, "varp.mcannon", 10)
            assertFalse(f.service.purchase(f.player, reward.index))
            VarPlayerIntMapSetter.set(f.player, "varp.mcannon", 11)
            assertTrue(f.service.purchase(f.player, reward.index))
        }
        withPolicy(QuestRequirementPolicy(QuestRequirementMode.VirtualCompletions, setOf("quest_dwarfcannon"))) {
            val f = Fixture()
            f.player.foundryReputation = reward.cost
            assertTrue(f.service.purchase(f.player, reward.index))
            assertEquals(0, f.player.vars["varp.mcannon"])
        }
    }

    @Test fun `ore distribution has measured weights and each pack delivers exactly thirty noted ores`() {
        val counts = (0 until 10_000).groupingBy(FoundryOrePackData::outcome).eachCount()
        assertEquals(FoundryOrePackData.outcomes.associate { it.item to it.weight }, counts)
        val f = Fixture()
        f.player.inv[0] = InvObj(FoundryOrePackData.PACK)
        assertTrue(f.service.openOrePack(f.player, 0))
        assertEquals(30, f.random.calls)
        assertEquals(0, f.player.inv.count(FoundryOrePackData.PACK))
        assertEquals(30, f.player.inv.filterNotNull().sumOf { it.count })
        val noted = FoundryOrePackData.outcomes.map { requireNotNull(ServerCacheManager.getItem(it.item)).certlink }
        assertTrue(f.player.inv.filterNotNull().all { it.id in noted })
    }

    @Test fun `an ore pack cannot reroll while insufficient space prevents opening`() {
        val f = Fixture()
        f.fill()
        f.player.inv[0] = InvObj(FoundryOrePackData.PACK)
        repeat(3) { assertFalse(f.service.openOrePack(f.player, 0)) }
        assertEquals(0, f.random.calls)
        assertEquals(1, f.player.inv.count(FoundryOrePackData.PACK))
        assertEquals(27, f.player.inv.count("obj.bucket_empty"))
        for (slot in 1..4) f.player.inv[slot] = null
        assertTrue(f.service.openOrePack(f.player, 0))
        assertEquals(30, f.random.calls)
    }

    @Test fun `ore pack merges with existing noted stacks even in a full inventory`() {
        val f = Fixture()
        f.fill()
        f.player.inv[0] = InvObj(FoundryOrePackData.PACK)
        for ((index, ore) in FoundryOrePackData.outcomes.withIndex()) {
            f.player.inv[index + 1] = InvObj(requireNotNull(ServerCacheManager.getItem(ore.item)).certlink, 1)
        }
        assertTrue(f.service.openOrePack(f.player, 0))
        assertEquals(35, f.player.inv.filterNotNull().filter { it.id != "obj.bucket_empty".asRSCM() }.sumOf { it.count })
    }

    @Test fun `glove combination and separation are reversible with inventory rollback`() {
        val f = Fixture()
        f.fill()
        f.player.inv[0] = InvObj(FoundryRewardService.SMITHS_GLOVES)
        f.player.inv[1] = InvObj(FoundryRewardService.ICE_GLOVES)
        assertTrue(f.service.combineGloves(f.player))
        assertEquals(1, f.player.inv.count(FoundryRewardService.ICED_GLOVES))
        assertEquals(0, f.player.inv.count(FoundryRewardService.SMITHS_GLOVES))
        val slot = f.player.inv.objs.indexOfFirst { it?.id == FoundryRewardService.ICED_GLOVES.asRSCM() }
        val free = f.player.inv.objs.indexOfFirst { it == null }
        f.player.inv[free] = InvObj("obj.bucket_empty")
        assertFalse(f.service.separateGloves(f.player, slot))
        assertEquals(1, f.player.inv.count(FoundryRewardService.ICED_GLOVES))
        f.player.inv[free] = null
        assertTrue(f.service.separateGloves(f.player, slot))
        assertEquals(0, f.player.inv.count(FoundryRewardService.ICED_GLOVES))
        assertEquals(1, f.player.inv.count(FoundryRewardService.SMITHS_GLOVES))
        assertEquals(1, f.player.inv.count(FoundryRewardService.ICE_GLOVES))
    }

    @Test fun `buyback refunds eighty percent and rejects excluded items stale slots and overflow`() {
        val f = Fixture()
        val item = FoundryRewardService.SMITHS_GLOVES
        val reward = FoundryData.rewards.first { it.item == item }
        val refund = reward.cost * 4 / 5
        f.player.inv[0] = InvObj(item)
        assertFalse(f.service.refund(f.player, item, 1))
        f.player.foundryReputation = Int.MAX_VALUE - refund + 1
        assertFalse(f.service.refund(f.player, item, 0))
        assertEquals(1, f.player.inv.count(item))
        f.player.foundryReputation = 0
        assertTrue(f.service.refund(f.player, item, 0))
        assertEquals(refund, f.player.foundryReputation)
        assertEquals(0, f.player.inv.count(item))
        assertNull(f.service.refundValue(FoundryRewardService.GROG))
        assertNull(f.service.refundValue(FoundryRewardService.BLADE))
        assertNull(f.service.refundValue("obj.bucket_empty"))
    }

    private class Fixture {
        val player = Player().apply { inv = Inventory.create("inv.inv") }
        val random = RecordingRandom()
        val service = FoundryRewardService(FoundryInterfaces(EventBus()), random)
        fun fill() { for (slot in player.inv.indices) player.inv[slot] = InvObj("obj.bucket_empty") }
    }

    private class RecordingRandom : GameRandom by DefaultGameRandom(1) {
        var calls = 0
        override fun of(maxExclusive: Int): Int = ((calls++ % 5) * 2499).coerceAtMost(maxExclusive - 1)
    }

    companion object {
        private val restore = mutableListOf<() -> Unit>()

        @JvmStatic @BeforeAll fun setup() {
            ServerCacheManager.init(241).close()
            for ((owner, name) in listOf(
                "org.rsmod.api.invtx.InvTransactionsScriptKt" to "cachedInventoryTransactions",
                "org.rsmod.api.invtx.VirtualInvTransactionsKt" to "cachedPlayerItemStorage",
            )) {
                val field = Class.forName(owner).getDeclaredField(name).apply { isAccessible = true }
                val previous = field.get(null)
                restore += { field.set(null, previous) }
            }
            val previousStorage = InvVirtualStorageHolder.instance
            restore += { InvVirtualStorageHolder.instance = previousStorage }
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
                ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        @JvmStatic @AfterAll fun teardown() { restore.asReversed().forEach { it() } }

        private fun withPolicy(policy: QuestRequirementPolicy, block: () -> Unit) {
            val previous = QuestRequirements.activePolicy()
            try { QuestRequirements.install(policy); block() } finally { QuestRequirements.install(previous) }
        }
    }
}
