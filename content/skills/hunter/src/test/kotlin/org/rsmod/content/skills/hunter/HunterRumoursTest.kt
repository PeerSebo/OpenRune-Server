@file:OptIn(org.rsmod.annotations.InternalApi::class)

package org.rsmod.content.skills.hunter

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
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
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.quest.manager.QuestRequirementMode
import org.rsmod.content.quest.manager.QuestRequirementPolicy
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.skills.hunter.configs.*
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HunterRumoursTest {
    private val restore = mutableListOf<() -> Unit>()

    @BeforeAll fun cache() { ServerCacheManager.init(241).close() }

    @BeforeEach fun services() {
        replace("org.rsmod.api.invtx.InvTransactionsScriptKt", "cachedInventoryTransactions", InvTransactions.from())
        replace("org.rsmod.api.invtx.VirtualInvTransactionsKt", "cachedPlayerItemStorage", PlayerItemStorage(emptySet()))
        val policy = QuestRequirements.activePolicy()
        restore += { QuestRequirements.install(policy) }
        QuestRequirements.install(QuestRequirementPolicy())
    }

    @AfterEach fun cleanup() { restore.asReversed().forEach { it() }; restore.clear() }
    private fun replace(owner: String, name: String, value: Any) {
        val field = Class.forName(owner).getDeclaredField(name).apply { isAccessible = true }
        val old = field.get(null); restore += { field.set(null, old) }; field.set(null, value)
    }

    @Test fun `every assignment resolves native prey part sack and matching hunting method`() {
        for (prey in HunterRumourPrey.entries) {
            if (prey.npc.startsWith("loc.")) assertNotNull(ServerCacheManager.getObject(prey.npc.asRSCM(RSCMType.LOC)))
            else assertNotNull(ServerCacheManager.getNpc(prey.npc.asRSCM(RSCMType.NPC)))
            assertNotNull(ServerCacheManager.getItem(prey.part.asRSCM()))
        }
        for (master in RumourMaster.entries) {
            assertNotNull(ServerCacheManager.getNpc(master.npc.asRSCM(RSCMType.NPC)))
            assertNotNull(ServerCacheManager.getItem(master.sack.asRSCM()))
        }
        val supported = HunterPreyTable.ALL.map { it.npc } + NetPreyTable.ALL.flatMap { it.npcs } + FalconryPrey.entries.map { it.npc } + PitfallPrey.entries.map { it.npc }
        for (prey in HunterRumourPrey.entries.filter { it.method !in listOf(RumourMethod.TRACKING, RumourMethod.HERBIBOAR) }) assertTrue(prey.npc in supported, prey.name)
    }

    @Test fun `newer ordinary creatures have native lifecycle objects and correct rewards`() {
        for ((suffix, xp) in listOf("jerboa" to 137.0, "fennec" to 177.6)) {
            val prey = HunterPreyTable.ALL.single { it.locSuffix == suffix }
            assertEquals(xp, prey.xp)
            prey.loot.forEach { assertNotNull(ServerCacheManager.getItem(it.asRSCM())) }
            val loc = if (suffix == "jerboa") TrapLocs.boxFull(suffix) else TrapLocs.deadfallFull(suffix)
            assertNotNull(ServerCacheManager.getObject(loc.asRSCM(RSCMType.LOC)))
        }
    }

    @Test fun `guild and master requirements use unboosted Hunter and configured quest policy`() {
        val p = player(45)
        assertFalse(HunterRumours.canUse(p, RumourMaster.GILMAN))
        p.statMap.setCurrentLevel("stat.hunter", 99)
        assertFalse(HunterRumours.canUse(p, RumourMaster.GILMAN))
        p.statMap.setBaseLevel("stat.hunter", 91)
        assertTrue(HunterRumours.canUse(p, RumourMaster.WOLF))
        QuestRequirements.install(QuestRequirementPolicy(mode = QuestRequirementMode.RespectProgress))
        assertFalse(HunterRumours.canUse(p, RumourMaster.WOLF))
        assertTrue(HunterRumours.canUse(p, RumourMaster.ACO))
        assertFalse(HunterRumours.eligible(p, RumourMaster.GILMAN).any { it.quest != null })
    }

    @Test fun `assigned blocks prevent duplicates while inactive progress survives switching`() {
        val p = player()
        seed(p, RumourMaster.GILMAN, HunterRumourPrey.RED, count = 9)
        assertFalse(HunterRumours.eligible(p, RumourMaster.WOLF).contains(HunterRumourPrey.RED))
        HunterRumours.assign(p, RumourMaster.WOLF, FixedRandom())
        HunterRumours.caught(p, HunterRumourPrey.RED.npc, FixedRandom()) { fail("Wrong active assignment") }
        assertEquals(9, HunterRumours.count(p, RumourMaster.GILMAN))
        assertEquals(HunterRumourPrey.RED, HunterRumours.assign(p, RumourMaster.GILMAN, FixedRandom()))
        assertEquals(9, HunterRumours.count(p, RumourMaster.GILMAN))
    }

    @Test fun `rare creature credit cannot turn a wild kebbit tuft into a different kebbit completion`() {
        val p = player()
        seed(p, RumourMaster.GILMAN, HunterRumourPrey.WILD)
        HunterRumours.caught(p, HunterRumourPrey.WILD.npc, FixedRandom()) { fail("Should fit") }
        seed(p, RumourMaster.CERVUS, HunterRumourPrey.SPOTTED)
        assertFalse(HunterRumours.complete(p, RumourMaster.CERVUS, XpModifiers(emptySet())))
        assertEquals(1, p.inv.count("obj.hg_kebbittuft"))
        assertTrue(HunterRumours.complete(p, RumourMaster.GILMAN, XpModifiers(emptySet())))
        assertEquals(1, HunterRumours.completed(p))
    }

    @Test fun `pity retries after full inventory while normal rare rolls fall to owned ground`() {
        val p = player()
        p.inv.objs.fill(item("obj.logs"))
        seed(p, RumourMaster.GILMAN, HunterRumourPrey.WAGTAIL, 39)
        var drops = 0
        HunterRumours.caught(p, HunterRumourPrey.WAGTAIL.npc, FixedRandom(Int.MAX_VALUE)) { drops++ }
        assertEquals(0, drops)
        assertEquals(40, HunterRumours.count(p, RumourMaster.GILMAN))
        p.inv[0] = null
        HunterRumours.caught(p, HunterRumourPrey.WAGTAIL.npc, FixedRandom(Int.MAX_VALUE)) { drops++ }
        assertTrue(HunterRumours.hasEarnedPart(p, HunterRumourPrey.WAGTAIL))
        p.inv[0] = item("obj.logs")
        seed(p, RumourMaster.GILMAN, HunterRumourPrey.RED)
        HunterRumours.caught(p, HunterRumourPrey.RED.npc, FixedRandom()) { drops++ }
        assertEquals(1, drops)
    }

    @Test fun `destroying an earned tuft cannot authorise a later tuft caught from another species`() {
        val p = player()
        seed(p, RumourMaster.GILMAN, HunterRumourPrey.WILD)
        HunterRumours.caught(p, HunterRumourPrey.WILD.npc, FixedRandom()) { fail("Fits") }
        p.inv[0] = null
        seed(p, RumourMaster.CERVUS, HunterRumourPrey.SPOTTED)
        HunterRumours.caught(p, HunterRumourPrey.SPOTTED.npc, FixedRandom()) { fail("Fits") }
        assertFalse(HunterRumours.complete(p, RumourMaster.GILMAN, XpModifiers(emptySet())))
        assertTrue(HunterRumours.complete(p, RumourMaster.CERVUS, XpModifiers(emptySet())))
    }

    @Test fun `an existing different rare part blocks drops but not pity progress`() {
        val p = player()
        seed(p, RumourMaster.GILMAN, HunterRumourPrey.WAGTAIL, 38)
        p.inv[0] = item(HunterRumourPrey.RED.part)
        repeat(2) { HunterRumours.caught(p, HunterRumourPrey.WAGTAIL.npc, FixedRandom()) { fail("Blocked") } }
        assertEquals(40, HunterRumours.count(p, RumourMaster.GILMAN))
        assertEquals(0, p.inv.count(HunterRumourPrey.WAGTAIL.part))
        p.inv[0] = null
        HunterRumours.caught(p, HunterRumourPrey.WAGTAIL.npc, FixedRandom(Int.MAX_VALUE)) { fail("Pity fits") }
        assertEquals(1, p.inv.count(HunterRumourPrey.WAGTAIL.part))
    }

    @Test fun `handin exchanges last occupied slot atomically and awards XP and completion once`() {
        val p = player()
        seed(p, RumourMaster.WOLF, HunterRumourPrey.RED)
        HunterRumours.caught(p, HunterRumourPrey.RED.npc, FixedRandom()) { fail("Fits") }
        for (slot in 1..27) p.inv[slot] = item("obj.logs")
        assertTrue(HunterRumours.complete(p, RumourMaster.WOLF, XpModifiers(emptySet())))
        assertEquals(1, p.inv.count(RumourMaster.WOLF.sack))
        assertEquals(62400, p.statMap.getFineXP("stat.hunter"))
        assertEquals(1, HunterRumours.completed(p))
        assertFalse(HunterRumours.complete(p, RumourMaster.WOLF, XpModifiers(emptySet())))
        assertEquals(62400, p.statMap.getFineXP("stat.hunter"))
        assertFalse(HunterRumours.eligible(p, RumourMaster.WOLF).contains(HunterRumourPrey.RED))
        p.rumoursBackToBack = true
        assertTrue(HunterRumours.eligible(p, RumourMaster.WOLF).contains(HunterRumourPrey.RED))
    }

    @Test fun `guild reset requires an inactive Gilman rumour and completing the replacement`() {
        val p = player()
        seed(p, RumourMaster.GILMAN, HunterRumourPrey.WAGTAIL)
        assertFalse(HunterRumours.reset(p, FixedRandom()))
        seed(p, RumourMaster.WOLF, HunterRumourPrey.RED)
        assertTrue(HunterRumours.reset(p, FixedRandom()))
        assertNull(HunterRumours.assigned(p, RumourMaster.WOLF))
        assertFalse(HunterRumours.canUse(p, RumourMaster.WOLF))
        val replacement = checkNotNull(HunterRumours.assigned(p, RumourMaster.GILMAN))
        assertNotEquals(HunterRumourPrey.WAGTAIL, replacement)
        HunterRumours.caught(p, replacement.npc, FixedRandom()) { fail("Fits") }
        assertTrue(HunterRumours.complete(p, RumourMaster.GILMAN, XpModifiers(emptySet())))
        assertTrue(HunterRumours.canUse(p, RumourMaster.WOLF))
    }

    @Test fun `individual reroll restores original block and progress when switched away`() {
        val p = player()
        seed(p, RumourMaster.CERVUS, HunterRumourPrey.FOX, 5)
        seed(p, RumourMaster.WOLF, HunterRumourPrey.RED)
        HunterRumours.caught(p, HunterRumourPrey.RED.npc, FixedRandom()) { fail("Fits") }
        assertTrue(HunterRumours.complete(p, RumourMaster.WOLF, XpModifiers(emptySet())))
        val temporary = HunterRumours.assign(p, RumourMaster.CERVUS, FixedRandom())
        assertNotEquals(HunterRumourPrey.FOX, temporary)
        HunterRumours.assign(p, RumourMaster.GILMAN, FixedRandom())
        assertEquals(HunterRumourPrey.FOX, HunterRumours.assigned(p, RumourMaster.CERVUS))
        assertEquals(5, HunterRumours.count(p, RumourMaster.CERVUS))
    }

    @Test fun `outfit published pity examples and owned-set bonus are preserved`() {
        val p = player()
        p.worn[0] = item(HunterGuildOutfit.pieces[0])
        assertEquals(19, HunterRumours.pity(p, HunterRumourPrey.DASHING))
        assertEquals(29, HunterRumours.pity(p, HunterRumourPrey.MOON_ANTELOPE))
        assertEquals(79, HunterRumours.pity(p, HunterRumourPrey.MOON_MOTH))
        p.worn[0] = null
        for ((i, piece) in HunterGuildOutfit.pieces.withIndex()) p.invMap.getValue("inv.bank")[i] = item(piece)
        p.worn[10] = item(HunterGuildOutfit.pieces[3])
        assertEquals(180, HunterGuildOutfit.rareBonus(p))
        assertEquals(19, HunterRumours.pity(p, HunterRumourPrey.DASHING))
        assertEquals(28, HunterRumours.pity(p, HunterRumourPrey.MOON_ANTELOPE))
        assertEquals(78, HunterRumours.pity(p, HunterRumourPrey.MOON_MOTH))
        for ((i, piece) in HunterGuildOutfit.pieces.withIndex()) p.worn[i] = item(piece)
        for (prey in HunterRumourPrey.entries) assertEquals(prey.method.fullOutfitPity, HunterRumours.pity(p, prey))
    }

    private fun player(level: Int = 99) = Player().apply {
        uuid = 1; observerUUID = 1
        inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
        worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
        invMap["inv.bank"] = Inventory(InventoryServerType(size = 100), arrayOfNulls(100))
        statMap.setBaseLevel("stat.hunter", level.toByte()); statMap.setCurrentLevel("stat.hunter", level.toByte())
        statMap.setBaseLevel("stat.herblore", 99)
        rumoursUnlocked = true
    }
    private fun item(symbol: String) = InvObj(checkNotNull(ServerCacheManager.getItem(symbol.asRSCM())))
    private fun seed(p: Player, master: RumourMaster, prey: HunterRumourPrey, count: Int = 0) {
        p.rumourActiveMaster = master.saveCode
        p.setRumourPrey(master, prey)
        p.setRumourCatchCount(master, count)
    }
    private class FixedRandom(val roll: Int = 0) : GameRandom {
        override fun of(maxExclusive: Int): Int = roll.coerceAtMost(maxExclusive - 1)
        override fun of(minInclusive: Int, maxInclusive: Int): Int = minInclusive
        override fun randomDouble(): Double = 0.0
    }
}
