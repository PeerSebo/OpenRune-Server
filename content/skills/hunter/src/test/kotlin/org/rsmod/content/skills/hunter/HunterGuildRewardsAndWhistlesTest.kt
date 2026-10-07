@file:OptIn(org.rsmod.annotations.InternalApi::class)

package org.rsmod.content.skills.hunter

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
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.content.interfaces.collectionlog.collectionTransmit
import org.rsmod.content.quest.manager.QuestRequirementPolicy
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.skills.hunter.configs.HunterSackLoot
import org.rsmod.content.skills.hunter.configs.HunterSackTier
import org.rsmod.content.skills.hunter.configs.QuetzalDestination
import org.rsmod.content.skills.hunter.configs.QuetzalWhistle
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.map.CoordGrid

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HunterGuildRewardsAndWhistlesTest {
    private val restore = mutableListOf<() -> Unit>()

    @BeforeAll fun cache() { ServerCacheManager.init(241).close() }

    @BeforeEach fun setup() {
        replaceStatic("org.rsmod.api.invtx.InvTransactionsScriptKt", "cachedInventoryTransactions", InvTransactions.from())
        replaceStatic("org.rsmod.api.invtx.VirtualInvTransactionsKt", "cachedPlayerItemStorage", PlayerItemStorage(emptySet()))
        val policy = QuestRequirements.activePolicy()
        restore += { QuestRequirements.install(policy) }
        QuestRequirements.install(QuestRequirementPolicy())
    }

    @AfterEach fun cleanup() { restore.asReversed().forEach { it() }; restore.clear() }

    @Test fun `native item options and published resource table sizes match the cache`() {
        for (tier in QuetzalWhistle.entries) {
            val type = checkNotNull(ServerCacheManager.getItem(tier.obj.asRSCM()))
            assertEquals("Signal", type.interfaceOptions[0])
            assertEquals("Rumour", type.interfaceOptions[2])
            assertEquals("Last-destination", type.interfaceOptions[3])
        }
        assertEquals(listOf(7, 12, 15, 15, 15), HunterSackTier.entries.map(HunterSackLoot::tableSize))
        assertEquals(listOf(5, 7, 9, 11, 11), HunterSackTier.entries.map { it.rolls })
    }

    @Test fun `cascading reward charts round before adding one and clamp impossible tiers`() {
        assertEquals(0, HunterSackLoot.chartChance(-60, 60, 49))
        assertEquals(1, HunterSackLoot.chartChance(-60, 60, 50))
        assertEquals(61, HunterSackLoot.chartChance(-60, 60, 99))
        assertEquals(71, HunterSackLoot.chartChance(70, -20, 1))
        assertEquals(0, HunterSackLoot.chartChance(70, -20, 99))
        assertEquals(256, HunterSackLoot.chartChance(999, 999, 1))
        assertEquals(61, HunterSackLoot.chartChance(-60, 60, 120))
    }

    @Test fun `each sack rolls its resources and each tertiary at most once`() {
        for (tier in HunterSackTier.entries) {
            val f = Fixture()
            val loot = f.rewards.roll(f.player, tier)
            assertEquals(tier.rolls, loot.count { it.obj == "obj.hg_seedsack" })
            assertTrue(loot.groupBy { it.obj }.filterKeys { it != "obj.hg_seedsack" }.values.all { it.size == 1 })
            assertEquals(if (tier.uniques) 1 else 0, loot.count { it.obj == HunterGuildRewards.KIT })
            assertEquals(if (tier.petDenominator != null) 1 else 0, loot.count { it.obj == HunterGuildRewards.PET })
        }
    }

    @Test fun `full inventory preserves the unopened sack and exact rolled contents`() {
        val f = Fixture()
        f.player.inv.objs.fill(item("obj.logs"))
        f.player.inv[0] = item(HunterSackTier.MASTER.obj)
        assertFalse(f.rewards.open(f.player, HunterSackTier.MASTER))
        val calls = f.random.calls
        assertFalse(f.rewards.open(f.player, HunterSackTier.MASTER))
        assertEquals(calls, f.random.calls)
        assertEquals(1, f.player.inv.count(HunterSackTier.MASTER.obj))
        assertEquals(0, f.player.collectionTransmit.count(HunterGuildRewards.PET))
        for (slot in 1..8) f.player.inv[slot] = null
        assertTrue(f.rewards.open(f.player, HunterSackTier.MASTER))
        assertEquals(calls, f.random.calls)
        assertEquals(11, f.player.inv.count("obj.hg_seedsack"))
        assertEquals(1, f.player.inv.count(HunterGuildRewards.PET))
        assertEquals(0, f.player.inv.count(HunterSackTier.MASTER.obj))
    }

    @Test fun `kit and lifetime pet ownership suppress duplicate awards`() {
        val f = Fixture()
        f.player.inv[0] = item(HunterGuildRewards.KIT)
        f.player.collectionTransmit[0] = item(HunterGuildRewards.PET)
        val loot = f.rewards.roll(f.player, HunterSackTier.MASTER)
        assertFalse(loot.any { it.obj == HunterGuildRewards.KIT || it.obj == HunterGuildRewards.PET })
    }

    @Test fun `two hundred million Hunter XP gives the official fifteen-fold Quetzin chance`() {
        val f = Fixture()
        f.random.value = 14
        assertFalse(f.rewards.roll(f.player, HunterSackTier.MASTER).any { it.obj == HunterGuildRewards.PET })
        f.player.statMap.setXP("stat.hunter", 200_000_000)
        assertTrue(f.rewards.roll(f.player, HunterSackTier.MASTER).any { it.obj == HunterGuildRewards.PET })
        f.random.value = 15
        assertFalse(f.rewards.roll(f.player, HunterSackTier.MASTER).any { it.obj == HunterGuildRewards.PET })
    }

    @Test fun `outfit pieces follow missing-piece order across worn and bank inventories`() {
        val f = Fixture()
        f.player.worn[0] = item(HunterGuildRewards.OUTFIT[0])
        f.player.invMap["inv.bank"] = inventory(50).apply { this[0] = item(HunterGuildRewards.OUTFIT[1]) }
        assertEquals(HunterGuildRewards.OUTFIT[2], f.rewards.roll(f.player, HunterSackTier.ADEPT).single { it.obj in HunterGuildRewards.OUTFIT }.obj)
    }

    @Test fun `guaranteed blueprints arrive at published milestones including supplies`() {
        val f = Fixture()
        f.random.value = 49
        f.player.rumoursCompleted = 99
        assertFalse(f.rewards.roll(f.player, HunterSackTier.ADEPT).any { it.obj == HunterGuildRewards.TORN_ENHANCED })
        f.player.rumoursCompleted = 100
        assertTrue(f.rewards.roll(f.player, HunterSackTier.SUPPLIES).any { it.obj == HunterGuildRewards.TORN_ENHANCED })
        f.player.rumoursCompleted = 250
        val loot = f.rewards.roll(f.player, HunterSackTier.ADEPT)
        assertTrue(loot.any { it.obj == HunterGuildRewards.TORN_PERFECTED })
        f.player.perfectedBlueprintSeen = true
        assertFalse(f.rewards.roll(f.player, HunterSackTier.ADEPT).any { it.obj == HunterGuildRewards.TORN_PERFECTED })
    }

    @Test fun `first basic blueprint requires ten rumours and subsequent copies cost five hundred`() {
        val f = Fixture()
        assertFalse(QuetzalWhistleService.blueprint(f.player))
        f.player.rumoursCompleted = 10
        assertTrue(QuetzalWhistleService.blueprint(f.player))
        assertFalse(QuetzalWhistleService.blueprint(f.player))
        f.player.inv[1] = item("obj.coins", 500)
        assertTrue(QuetzalWhistleService.blueprint(f.player))
        assertEquals(0, f.player.inv.count("obj.coins"))
        assertEquals(2, f.player.inv.count(checkNotNull(QuetzalWhistle.BASIC.blueprint)))
    }

    @Test fun `whistle recipes are atomic and retain charges on upgrade`() {
        val f = Fixture()
        f.player.inv[0] = item("obj.knife")
        f.player.inv[1] = item("obj.willow_logs")
        assertFalse(QuetzalWhistleService.craft(f.player, checkNotNull(QuetzalWhistle.BASIC.blueprint)))
        assertEquals(1, f.player.inv.count("obj.willow_logs"))
        f.player.inv[2] = item(checkNotNull(QuetzalWhistle.BASIC.blueprint))
        assertTrue(QuetzalWhistleService.craft(f.player, checkNotNull(QuetzalWhistle.BASIC.blueprint)))
        val slot = f.player.inv.objs.indexOfFirst { it?.id == QuetzalWhistle.BASIC.obj.asRSCM() }
        f.player.inv[slot] = item(QuetzalWhistle.BASIC.obj, vars = 4)
        f.player.inv[2] = item(HunterGuildRewards.TORN_ENHANCED)
        f.player.inv[3] = item("obj.yew_logs")
        assertTrue(QuetzalWhistleService.craft(f.player, HunterGuildRewards.TORN_ENHANCED))
        assertEquals(4, f.player.inv.objs.filterNotNull().single { it.id == QuetzalWhistle.ENHANCED.obj.asRSCM() }.vars)
        assertEquals(0, f.player.inv.count(QuetzalWhistle.BASIC.obj))
        assertEquals(1, f.player.inv.count("obj.knife"))
    }

    @Test fun `recharge fills all whistles without sharing food or duplicating overfill`() {
        val f = Fixture()
        f.player.inv[0] = item(QuetzalWhistle.BASIC.obj, vars = 4)
        f.player.inv[1] = item(QuetzalWhistle.ENHANCED.obj, vars = 19)
        f.player.inv[2] = item("obj.hunting_antelopemoon_meat")
        assertTrue(QuetzalWhistleService.recharge(f.player))
        assertEquals(5, f.player.inv[0]?.vars)
        assertEquals(19, f.player.inv[1]?.vars)
        assertEquals(3, QuetzalWhistleService.feedCredit(f.player))
        f.player.inv[2] = item("obj.hg_seedsack", 5)
        assertTrue(QuetzalWhistleService.recharge(f.player))
        assertEquals(20, f.player.inv[1]?.vars)
        assertEquals(4, f.player.inv.count("obj.hg_seedsack"))
    }

    @Test fun `ordinary recharge rejects noted food and does not spend food when full`() {
        val f = Fixture()
        f.player.inv[0] = item(QuetzalWhistle.BASIC.obj)
        f.player.inv[1] = item("obj.cert_hunting_antelopemoon_meat", 100)
        assertFalse(QuetzalWhistleService.recharge(f.player))
        f.player.inv[0] = item(QuetzalWhistle.BASIC.obj, vars = 5)
        f.player.inv[2] = item("obj.hg_seedsack", 20)
        assertFalse(QuetzalWhistleService.recharge(f.player))
        assertEquals(20, f.player.inv.count("obj.hg_seedsack"))
        assertEquals(0, QuetzalWhistleService.feedCredit(f.player))
    }

    @Test fun `imbuing accepts noted meat and preserves partial credit across visits`() {
        val f = Fixture()
        f.player.inv[0] = item(QuetzalWhistle.PERFECTED.obj, vars = 40)
        f.player.inv[1] = item("obj.cert_hunting_antelopemoon_meat", 3000)
        assertTrue(QuetzalWhistleService.imbue(f.player))
        assertEquals(9000, QuetzalWhistleService.feedCredit(f.player))
        assertEquals(1, f.player.inv.count(QuetzalWhistle.PERFECTED.obj))
        f.player.inv[1] = item("obj.cert_hunting_antelopemoon_meat", 1000)
        assertTrue(QuetzalWhistleService.imbue(f.player))
        assertEquals(10_000, QuetzalWhistleService.feedCredit(f.player))
        assertEquals(666, f.player.inv.count("obj.cert_hunting_antelopemoon_meat"))
        assertEquals(1, f.player.inv.count(QuetzalWhistle.INFINITE.obj))
        assertEquals(0, f.player.inv.count(QuetzalWhistle.PERFECTED.obj))
        assertEquals(true, f.player.infiniteWhistleUnlocked)
    }

    @Test fun `infinite replacement requires fifty thousand even after previous unlock`() {
        val f = Fixture()
        f.player.infiniteWhistleUnlocked = true
        assertFalse(QuetzalWhistleService.reclaim(f.player))
        f.player.inv[0] = item("obj.coins", 50_000)
        assertTrue(QuetzalWhistleService.reclaim(f.player))
        assertEquals(1, f.player.inv.count(QuetzalWhistle.INFINITE.obj))
        assertEquals(0, f.player.inv.count("obj.coins"))
    }

    @Test fun `stale menu selections cannot spend a swapped or already used whistle`() {
        val f = Fixture()
        f.player.inv[0] = item(QuetzalWhistle.BASIC.obj, vars = 3)
        val selection = checkNotNull(QuetzalWhistleService.selection(f.player, 0))
        f.player.inv[0] = item(QuetzalWhistle.BASIC.obj, vars = 3)
        assertFalse(QuetzalWhistleService.spend(f.player, selection))
        val current = checkNotNull(QuetzalWhistleService.selection(f.player, 0))
        assertTrue(QuetzalWhistleService.spend(f.player, current))
        assertFalse(QuetzalWhistleService.spend(f.player, current))
        assertEquals(2, f.player.inv[0]?.vars)
    }

    @Test fun `native destination mapping rejects forged children and uses native unlock bits`() {
        val f = Fixture()
        assertEquals(CoordGrid(1585, 3053), QuetzalDestination.fromButton(3)?.coords)
        assertNull(QuetzalDestination.fromButton(-1)); assertNull(QuetzalDestination.fromButton(14))
        assertEquals(8, QuetzalDestination.entries.count { it.unlocked(f.player) })
        VarPlayerIntMapSetter.set(f.player, "varp.quetzals_unlocked", 1 shl 5)
        assertTrue(QuetzalDestination.CAM_TORUM.unlocked(f.player))
        assertFalse(QuetzalDestination.WYRM.unlocked(f.player))
    }

    @Test fun `building landing sites consumes exact materials once and updates personal model and menu`() {
        val f = Fixture()
        val site = QuetzalDestination.CAM_TORUM
        f.player.inv[0] = item("obj.hg_seedsack", 20)
        assertFalse(QuetzalWhistleService.buildLanding(f.player, site))
        assertEquals(20, f.player.inv.count("obj.hg_seedsack"))
        for (slot in 1..3) f.player.inv[slot] = item("obj.limestonebrick")
        for (slot in 4..7) f.player.inv[slot] = item("obj.softclay")
        assertTrue(QuetzalWhistleService.buildLanding(f.player, site))
        assertEquals(1, f.player.vars[site.varbit])
        assertTrue(site.unlocked(f.player))
        assertFalse(QuetzalWhistleService.buildLanding(f.player, site))
        assertEquals(10, f.player.inv.count("obj.hg_seedsack"))
        assertEquals(0, f.player.inv.count("obj.softclay"))
    }

    @Test fun `Verity accepts only spare outfit pieces and never takes a loaded kit`() {
        val f = Fixture()
        val hood = HunterGuildRewards.OUTFIT[0]
        f.player.inv[0] = item(hood)
        assertFalse(f.rewards.exchange(f.player, hood))
        f.player.worn[0] = item(hood)
        assertTrue(f.rewards.exchange(f.player, hood))
        f.player.inv[1] = item(HunterGuildRewards.KIT)
        val kit = inventory(50).apply { this[0] = item("obj.hunting_box_trap") }
        f.player.invMap["inv.huntsmans_kit"] = kit
        assertFalse(f.rewards.exchange(f.player, HunterGuildRewards.KIT))
        kit[0] = null
        assertTrue(f.rewards.exchange(f.player, HunterGuildRewards.KIT))
        assertEquals(2, f.player.inv.count(HunterSackTier.SUPPLIES.obj))
    }

    private class Random : GameRandom {
        var value = 0
        var calls = 0
        override fun of(maxExclusive: Int): Int { calls++; return value.coerceAtMost(maxExclusive - 1) }
        override fun of(minInclusive: Int, maxInclusive: Int): Int { calls++; return minInclusive + value.coerceAtMost(maxInclusive - minInclusive) }
        override fun randomDouble(): Double = value / 100.0
    }
    private class Fixture {
        val random = Random()
        val rewards = HunterGuildRewards(random)
        val player = Player().apply {
            uuid = 1; observerUUID = 1; slotId = 1; assignUid()
            inv = inventory(28); worn = inventory(14)
            statMap.setBaseLevel("stat.woodcutting", 99)
            statMap.setBaseLevel("stat.herblore", 99)
        }
    }
    private fun replaceStatic(owner: String, name: String, value: Any) {
        val field = Class.forName(owner).getDeclaredField(name).apply { isAccessible = true }
        val previous = field.get(null); restore += { field.set(null, previous) }; field.set(null, value)
    }
    companion object {
        private fun inventory(size: Int) = Inventory(InventoryServerType(size = size), arrayOfNulls(size))
        private fun item(obj: String, count: Int = 1, vars: Int = 0) = InvObj(checkNotNull(ServerCacheManager.getItem(obj.asRSCM())), count, vars)
    }
}
