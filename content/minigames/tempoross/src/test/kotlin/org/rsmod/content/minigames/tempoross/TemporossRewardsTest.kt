package org.rsmod.content.minigames.tempoross

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactions
import org.rsmod.api.random.GameRandom
import org.rsmod.content.interfaces.collectionlog.collectionTransmit
import org.rsmod.content.minigames.tempoross.configs.temporossBestPoints
import org.rsmod.content.minigames.tempoross.configs.temporossBestTicks
import org.rsmod.content.minigames.tempoross.configs.temporossRewardSearches
import org.rsmod.content.minigames.tempoross.scripts.TemporossRewardScript
import org.rsmod.content.quest.manager.QuestRequirementMode
import org.rsmod.content.quest.manager.QuestRequirementPolicy
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TemporossRewardsTest {
    @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    private val restore = mutableListOf<() -> Unit>()

    @BeforeEach fun inventories() {
        val previousPolicy = QuestRequirements.activePolicy()
        restore += { QuestRequirements.install(previousPolicy) }
        QuestRequirements.install(QuestRequirementPolicy(QuestRequirementMode.RespectProgress))
        replace("org.rsmod.api.invtx.InvTransactionsScriptKt", "cachedInventoryTransactions", InvTransactions.from())
        replace("org.rsmod.api.invtx.VirtualInvTransactionsKt", "cachedPlayerItemStorage", PlayerItemStorage(emptySet()))
    }

    @AfterEach fun cleanup() { restore.asReversed().forEach { it() }; restore.clear() }
    private fun replace(owner: String, name: String, value: Any) {
        val field = Class.forName(owner).getDeclaredField(name).apply { isAccessible = true }
        val old = field.get(null)
        restore += { field.set(null, old) }
        field.set(null, value)
    }

    @Test fun `fish tiers change at each documented base level boundary`() {
        val boundaries = listOf(35 to "herring", 40 to "mackerel", 46 to "pike", 50 to "salmon", 76 to "tuna", 79 to "lobster", 81 to "bass")
        for ((index, row) in boundaries.withIndex()) {
            assertEquals("obj.raw_${row.second}", TemporossRewards.fishBand(row.first).first())
            if (index > 0) assertEquals("obj.raw_${boundaries[index - 1].second}", TemporossRewards.fishBand(row.first - 1).first())
        }
        assertEquals("obj.raw_mantaray", TemporossRewards.fishBand(99).last())
    }

    @Test fun `every published casket row resolves and can be noted`() {
        assertEquals(230, TemporossRewards.casketTable.sumOf { it.weight })
        for (entry in TemporossRewards.casketTable) {
            val type = checkNotNull(ServerCacheManager.getItem(entry.obj.asRSCM()))
            assertTrue(entry.quantity.first > 0)
            if (entry.noted) assertTrue(type.certlink > 0, entry.obj)
        }
    }

    @Test fun `each unique has its exact number of rare outcomes`() {
        val counts = (0 until 201).map { TemporossRewards.rollPool(99, Rolls(it)) { false }.obj }.groupingBy { it }.eachCount()
        assertEquals(1, counts[TemporossRewards.PET])
        assertEquals(1, counts["obj.dragon_harpoon"])
        assertEquals(20, counts[TemporossRewards.BARREL])
        assertEquals(20, counts[TemporossRewards.TACKLE_BOX])
        assertEquals(149, counts[TemporossRewards.PAGES])
        assertEquals(5, counts["obj.tome_of_water_uncharged"])
        assertEquals(5, counts["obj.poh_trophydrop_harpoonfish"])
    }

    @Test fun `duplicate untradeable uniques become 25 pages`() {
        assertEquals(TemporossRewards.Reward(TemporossRewards.PAGES, 25), TemporossRewards.rollPool(99, Rolls(12)) { true })
        assertEquals(TemporossRewards.Reward(TemporossRewards.PAGES, 25), TemporossRewards.rollPool(99, Rolls(32)) { true })
    }

    @Test fun `fish rewards are noted with complete quantities`() {
        val p = player()
        assertTrue(TemporossRewards.collect(p, 81, Rolls(7999, 0, 0)))
        val fish = checkNotNull(ServerCacheManager.getItem("obj.raw_bass".asRSCM()))
        assertEquals(fish.certlink, p.inv[0]?.id)
        assertEquals(30, p.inv[0]?.count)
        assertEquals(1, p.temporossRewardSearches)
    }

    @Test fun `failed inventory insertion retains the same reward instead of rerolling`() {
        val p = player(1)
        item(p, 0, "obj.harpoon")
        assertFalse(TemporossRewards.collect(p, 99, Rolls(12)))
        assertEquals(0, p.temporossRewardSearches)
        p.inv[0] = null
        assertTrue(TemporossRewards.collect(p, 99, Rolls(0)))
        assertEquals(TemporossRewards.BARREL.asRSCM(), p.inv[0]?.id)
        assertEquals(1, p.collectionTransmit.count(TemporossRewards.BARREL))
    }

    @Test fun `barrel ownership includes bank worn and open sack variants`() {
        val p = player()
        p.invMap.getValue("inv.bank")[0] = obj("obj.fish_sack_barrel_open")
        assertTrue(TemporossRewards.ownsUnique(p, TemporossRewards.BARREL))
        p.invMap.getValue("inv.bank")[0] = null
        p.worn[3] = obj("obj.fish_sack_barrel_closed")
        assertTrue(TemporossRewards.ownsUnique(p, TemporossRewards.BARREL))
    }

    @Test fun `pending barrel changes to pages if one is obtained before making inventory space`() {
        val p = player(1)
        item(p, 0, "obj.harpoon")
        assertFalse(TemporossRewards.collect(p, 99, Rolls(12)))
        p.invMap.getValue("inv.bank")[0] = obj(TemporossRewards.BARREL)
        p.inv[0] = null
        assertTrue(TemporossRewards.collect(p, 99, Rolls(0)))
        assertEquals(25, p.inv.count(TemporossRewards.PAGES))
        assertEquals(0, p.inv.count(TemporossRewards.BARREL))
    }

    @Test fun `pending casket clue rechecks bank ownership without rerolling main loot`() {
        val p = player(2)
        val clue = checkNotNull(TemporossRewardScript.casketClue(p, Rolls(0)))
        item(p, 0, TemporossRewards.CASKET, 2)
        item(p, 1, "obj.harpoon")
        assertFalse(TemporossRewards.openCasket(p, Rolls(0)) { clue })
        p.invMap.getValue("inv.bank")[0] = obj(clue.obj)
        p.inv[1] = null
        assertTrue(TemporossRewards.openCasket(p, Rolls(100)) { error("Pending loot must not reroll") })
        assertEquals(0, p.inv.count(clue.obj))
        assertEquals(checkNotNull(ServerCacheManager.getItem("obj.rune_med_helm".asRSCM())).certlink, p.inv[1]?.id)
        assertEquals(1, p.inv.count(TemporossRewards.CASKET))
    }

    @Test fun `empty bank entries cannot discard a pending casket clue`() {
        val p = player(2)
        val clue = checkNotNull(TemporossRewardScript.casketClue(p, Rolls(0)))
        item(p, 0, TemporossRewards.CASKET)
        item(p, 1, "obj.harpoon")
        assertFalse(TemporossRewards.openCasket(p, Rolls(0)) { clue })
        val type = checkNotNull(ServerCacheManager.getItem(clue.obj.asRSCM()))
        p.invMap.getValue("inv.bank")[0] = InvObj(type, 0)
        p.inv[1] = null
        assertTrue(TemporossRewards.openCasket(p, Rolls(100)) { error("Pending loot must not reroll") })
        assertEquals(1, p.inv.count(clue.obj))
    }

    @Test fun `unopened clue containers block their tier but completed reward caskets do not`() {
        val p = player()
        p.invMap.getValue("inv.bank")[0] = obj("obj.wc_clue_nest_easy")
        assertNull(TemporossRewardScript.casketClue(p, Rolls(0)))
        assertNotNull(TemporossRewardScript.casketClue(p, Rolls(5)))
        val finished = ServerCacheManager.getItems().values.first { it.name == "Reward casket (easy)" && !it.isPlaceholder }
        p.invMap.getValue("inv.bank")[0] = InvObj(finished)
        val reward = checkNotNull(TemporossRewardScript.casketClue(p, Rolls(0)))
        assertFalse(checkNotNull(ServerCacheManager.getItem(reward.obj.asRSCM())).isPlaceholder)
    }

    @Test fun `casket clues use stackable boxes after X Marks the Spot and claimed CA bonuses`() {
        val p = player()
        org.rsmod.api.player.vars.VarPlayerIntMapSetter.set(p, "varbit.cluequest", 8)
        assertEquals("obj.league_clue_box_easy", TemporossRewardScript.casketClue(p, Rolls(0))?.obj)
        assertEquals("obj.league_clue_box_medium", TemporossRewardScript.casketClue(p, Rolls(5))?.obj)
        assertEquals("obj.league_clue_box_hard", TemporossRewardScript.casketClue(p, Rolls(8))?.obj)
        val bonus = object : GameRandom {
            override fun of(maxExclusive: Int) = maxExclusive - 1
            override fun of(minInclusive: Int, maxInclusive: Int) = minInclusive
            override fun randomDouble() = 0.0
        }
        assertNull(TemporossRewardScript.casketClue(p, bonus))
        org.rsmod.api.player.vars.VarPlayerIntMapSetter.set(p, "varbit.ca_lamp_claimed_easy", 1)
        assertEquals("obj.league_clue_box_easy", TemporossRewardScript.casketClue(p, bonus)?.obj)
    }

    @Test fun `stackable casket clues honor configured quest completions`() {
        val p = player()
        assertFalse(TemporossRewards.hasStackableClues(p))
        QuestRequirements.install(QuestRequirementPolicy(QuestRequirementMode.AssumeCompleted))
        assertEquals("obj.league_clue_box_easy", TemporossRewardScript.casketClue(p, Rolls(0))?.obj)
        QuestRequirements.install(QuestRequirementPolicy(QuestRequirementMode.VirtualCompletions, setOf("quest_xmarksthespot")))
        assertEquals("obj.league_clue_box_hard", TemporossRewardScript.casketClue(p, Rolls(8))?.obj)
    }

    @Test fun `casket consumption and main plus clue loot are atomic`() {
        val p = player(2)
        item(p, 0, TemporossRewards.CASKET, 2)
        item(p, 1, "obj.harpoon")
        assertFalse(TemporossRewards.openCasket(p, Rolls(0)) { TemporossRewards.Reward("obj.league_clue_box_easy") })
        assertEquals(2, p.inv.count(TemporossRewards.CASKET))
        p.inv[1] = null
        assertFalse(TemporossRewards.openCasket(p, Rolls(1)) { null })
        p.inv[0] = obj(TemporossRewards.CASKET)
        assertTrue(TemporossRewards.openCasket(p, Rolls(1)) { null })
        assertEquals(0, p.inv.count(TemporossRewards.CASKET))
        assertEquals(1, p.inv.count("obj.league_clue_box_easy"))
    }

    @Test fun `last casket can use its freed slot for a noted reward`() {
        val p = player(1)
        item(p, 0, TemporossRewards.CASKET)
        assertTrue(TemporossRewards.openCasket(p, Rolls(0)) { null })
        assertEquals(checkNotNull(ServerCacheManager.getItem("obj.rune_med_helm".asRSCM())).certlink, p.inv[0]?.id)
    }

    @Test fun `shop debits flakes atomically and logs a purchase once`() {
        val p = player(1)
        item(p, 0, TemporossRewards.FLAKES, 6001)
        assertFalse(TemporossRewardScript.purchase(p, TemporossRewards.BARREL))
        assertEquals(6001, p.inv.count(TemporossRewards.FLAKES))
        item(p, 0, TemporossRewards.FLAKES, 6000)
        assertTrue(TemporossRewardScript.purchase(p, TemporossRewards.BARREL))
        assertEquals(1, p.collectionTransmit.count(TemporossRewards.BARREL))
        assertFalse(TemporossRewardScript.purchase(p, TemporossRewards.BARREL))
    }

    @Test fun `outfit conversion is charged once per piece and reverting unlocks free upgrade`() {
        val p = player()
        item(p, 0, "obj.trawler_reward_hat")
        item(p, 1, TemporossRewards.FLAKES, 1199)
        assertFalse(TemporossRewardScript.convert(p, "hat", "obj.trawler_reward_hat"))
        assertEquals(1199, p.inv.count(TemporossRewards.FLAKES))
        item(p, 1, TemporossRewards.FLAKES, 1200)
        assertTrue(TemporossRewardScript.convert(p, "hat", "obj.trawler_reward_hat"))
        assertEquals(0, p.inv.count(TemporossRewards.FLAKES))
        assertTrue(TemporossRewardScript.convert(p, "hat", "obj.spirit_angler_hat"))
        assertTrue(TemporossRewardScript.convert(p, "hat", "obj.trawler_reward_hat"))
        assertEquals(1, p.inv.count("obj.spirit_angler_hat"))
        item(p, 1, "obj.trawler_reward_top")
        assertFalse(TemporossRewardScript.convert(p, "top", "obj.trawler_reward_top"))
    }

    @Test fun `victory records eligible kills personal best and fastest time`() {
        val p = player()
        TemporossStats.recordWin(p, 1999, 300)
        assertEquals(0, p.vars[TemporossStats.KILLS])
        TemporossStats.recordWin(p, 3000, 500)
        TemporossStats.recordWin(p, 2700, 400)
        assertEquals(2, p.vars[TemporossStats.KILLS])
        assertEquals(3000, p.temporossBestPoints)
        assertEquals(400, p.temporossBestTicks)
        assertEquals("4:00", TemporossStats.time(p.temporossBestTicks))
    }

    private fun player(size: Int = 28) = Player().apply {
        uuid = 1L
        inv = Inventory(InventoryServerType(size = size), arrayOfNulls(size))
        invMap["inv.bank"] = Inventory(InventoryServerType(size = 800), arrayOfNulls(800))
        worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
        invMap["inv.collection_transmit"] = Inventory(InventoryServerType(size = 2000), arrayOfNulls(2000))
    }
    private fun obj(type: String, count: Int = 1) = InvObj(checkNotNull(ServerCacheManager.getItem(type.asRSCM())), count)
    private fun item(player: Player, slot: Int, type: String, count: Int = 1) { player.inv[slot] = obj(type, count) }
    private class Rolls(vararg values: Int) : GameRandom {
        private val pending = ArrayDeque(values.toList())
        override fun of(maxExclusive: Int) = (pending.removeFirstOrNull() ?: 0).coerceIn(0, maxExclusive - 1)
        override fun of(minInclusive: Int, maxInclusive: Int) = minInclusive
        override fun randomDouble() = 0.99
    }
}
