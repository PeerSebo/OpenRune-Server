package org.rsmod.content.skills.runecrafting.configs

import dev.openrune.ServerCacheManager
import dev.openrune.types.InventoryServerType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.random.GameRandom
import org.rsmod.api.stats.xpmod.XpMod
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.table.runecrafting.RunecraftingRunesRow
import org.rsmod.content.skills.runecrafting.action.RunecraftAction
import org.rsmod.content.skills.runecrafting.essencepouch.EssencePouch
import org.rsmod.coroutine.GameCoroutine
import org.rsmod.events.EventBus
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class OuraniaRatesTest {
    private val restore = mutableListOf<() -> Unit>()

    @BeforeEach
    fun inventoryServices() {
        replaceStatic("org.rsmod.api.invtx.InvTransactionsScriptKt", "cachedInventoryTransactions", InvTransactions.from())
        replaceStatic("org.rsmod.api.invtx.VirtualInvTransactionsKt", "cachedPlayerItemStorage", PlayerItemStorage(emptySet()))
    }

    @AfterEach
    fun restoreServices() {
        restore.asReversed().forEach { it() }
        restore.clear()
    }

    @Test
    fun `packed bands preserve every literal rune weight and all level boundaries`() {
        assertEquals(listOf(1, 10, 20, 30, 40, 50, 60, 70, 80, 90, 99), OuraniaRateData.bands.map { it.level })
        assertEquals(FrozenOuraniaRates.WEIGHT_TOTAL, OuraniaRates.WEIGHT_TOTAL)
        for (level in levels) {
            val expected = FrozenOuraniaRates.weightsForLevel(level).map { it.rune to it.weight }
            val actual = OuraniaRates.weightsForLevel(level).map { it.rune to it.weight }
            assertEquals(expected, actual, "level $level")
            assertEquals(expected.sumOf { it.second }, actual.sumOf { it.second }, "level $level total")
        }
    }

    @Test
    fun `existing selector preserves every cumulative roll boundary with one draw at every level`() {
        val fixture = Fixture()
        for (level in levels) {
            val weights = FrozenOuraniaRates.weightsForLevel(level)
            val boundaries = weights.runningFold(0) { total, entry -> total + entry.weight }
                .flatMap { listOf(it - 1, it) }
                .filter { it in 0 until 10000 }
                .toSet() + 9999
            for (roll in boundaries) {
                fixture.random.expect(10000, roll)
                val result = selector.invoke(RunecraftAction, fixture.access(), level) as RunecraftingRunesRow
                assertEquals(expectedRune(level, roll), result.output.internalName, "level $level roll $roll")
                fixture.random.assertConsumed()
            }
        }
    }

    @Test
    fun `selector keeps the first rune fallback when a draw reaches past the last entry`() {
        val fixture = Fixture()
        for (level in listOf(1, 50, 99)) {
            fixture.random.expect(10000, 10000)
            val result = selector.invoke(RunecraftAction, fixture.access(), level) as RunecraftingRunesRow
            assertEquals("obj.airrune", result.output.internalName)
            fixture.random.assertConsumed()
        }
    }

    @Test
    fun `rune production uses base level rather than boosted or drained level`() {
        for ((base, current, rune) in listOf(Triple(1, 99, "obj.airrune"), Triple(99, 1, "obj.mindrune"))) {
            val fixture = Fixture(base, current)
            fixture.item(0, EssencePouch.PURE_ESSENCE)
            fixture.random.expect(10000, 100)
            fixture.craft()
            assertEquals(0, fixture.count(EssencePouch.PURE_ESSENCE))
            assertEquals(1, fixture.count(rune))
            assertEquals(1, fixture.player.inv.sumOf { it?.count ?: 0 })
            fixture.random.assertConsumed()
        }
    }

    @Test
    fun `mixed essence keeps batch order fractional XP soul override and XP modifiers`() {
        val fixture = Fixture()
        fixture.item(0, EssencePouch.DAEYALT_ESSENCE)
        fixture.item(1, EssencePouch.DAEYALT_ESSENCE)
        fixture.item(2, EssencePouch.PURE_ESSENCE)
        fixture.item(3, EssencePouch.PURE_ESSENCE)
        fixture.item(4, EssencePouch.GUARDIAN_ESSENCE)
        for (roll in listOf(9999, 0, 100, 7800, 9999)) fixture.random.expect(10000, roll)
        fixture.craft(XpModifiers(setOf(XpMod { 1.0 })))
        assertEquals(1, fixture.count("obj.airrune"))
        assertEquals(1, fixture.count("obj.mindrune"))
        assertEquals(1, fixture.count("obj.bloodrune"))
        assertEquals(2, fixture.count("obj.soulrune"))
        assertEquals(0, fixture.count(EssencePouch.DAEYALT_ESSENCE))
        assertEquals(0, fixture.count(EssencePouch.PURE_ESSENCE))
        assertEquals(0, fixture.count(EssencePouch.GUARDIAN_ESSENCE))
        val daeyaltXp = 11.0 * (1.5 * 1.7) + 5.0 * (1.5 * 1.7)
        val standardXp = 5.5 * 1.7 + 10.5 * 1.7 + 11.0 * 1.7
        assertEquals((daeyaltXp * 2.0 * 10).toInt() + (standardXp * 2.0 * 10).toInt(), fixture.xp())
        fixture.random.assertConsumed()
    }

    @Test
    fun `outfit rolls stay between rune draws and bonus runes do not multiply XP`() {
        val fixture = Fixture()
        fixture.item(0, EssencePouch.PURE_ESSENCE)
        fixture.item(1, EssencePouch.PURE_ESSENCE)
        for ((slot, name) in listOf("hat_of_the_eye", "robe_top_of_the_eye", "robe_bottom_of_the_eye", "boots_of_the_eye").withIndex()) {
            fixture.player.worn[slot] = InvObj("obj.$name")
        }
        fixture.random.expect(10000, 9999)
        fixture.random.expect(10, 0)
        fixture.random.expect(10000, 0)
        fixture.random.expect(10, 9)
        fixture.craft()
        assertEquals(7, fixture.count("obj.soulrune"))
        assertEquals(1, fixture.count("obj.airrune"))
        assertEquals(((11.0 * 1.7 + 5.0 * 1.7) * 10).toInt(), fixture.xp())
        fixture.random.assertConsumed()
    }

    private fun expectedRune(level: Int, roll: Int): String {
        val weights = FrozenOuraniaRates.weightsForLevel(level)
        var total = 0
        for (entry in weights) {
            total += entry.weight
            if (roll < total) return entry.rune
        }
        return weights.first().rune
    }

    private fun replaceStatic(owner: String, name: String, value: Any) {
        val field = Class.forName(owner).getDeclaredField(name).apply { isAccessible = true }
        val previous = field.get(null)
        restore += { field.set(null, previous) }
        field.set(null, value)
    }

    private class Draws : GameRandom {
        private val values = ArrayDeque<Pair<Int, Int>>()
        fun expect(bound: Int, value: Int) { values.addLast(bound to value) }
        fun assertConsumed() { assertTrue(values.isEmpty(), "Not all expected random draws were made.") }
        override fun of(maxExclusive: Int): Int {
            assertFalse(values.isEmpty(), "Unexpected extra random draw.")
            val (bound, value) = values.removeFirst()
            assertEquals(bound, maxExclusive, "Random draw order or bound changed.")
            return value
        }
        override fun of(minInclusive: Int, maxInclusive: Int): Int = error("Unexpected ranged random draw.")
        override fun randomDouble(): Double = error("Unexpected floating-point random draw.")
    }

    private class Fixture(baseLevel: Int = 99, currentLevel: Int = baseLevel) {
        val random = Draws()
        val player = Player().apply {
            uuid = 1L
            slotId = 1
            displayName = "Ourania test"
            currentMapClock = 100
            coords = CoordGrid(3056, 5579)
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
            invMap["inv.bank"] = Inventory(InventoryServerType(size = 800), arrayOfNulls(800))
            statMap.setBaseLevel("stat.runecrafting", baseLevel.toByte())
            statMap.setCurrentLevel("stat.runecrafting", currentLevel.toByte())
        }

        fun item(slot: Int, symbol: String) { player.inv[slot] = InvObj(symbol) }
        fun count(symbol: String): Int = player.inv.count(symbol)
        fun xp(): Int = player.statMap.getFineXP("stat.runecrafting")
        fun craft(xpMods: XpModifiers = XpModifiers(emptySet())) {
            val coroutine = player.launch { with(RunecraftAction) { access(this@launch).craftOurania(xpMods) } }
            assertTrue(coroutine.isIdle, "Ourania production unexpectedly suspended.")
        }

        fun access(coroutine: GameCoroutine = GameCoroutine()) = ProtectedAccess(player, coroutine, ProtectedAccessContext(
            getRandom = { random }, getEventBus = { EventBus() }, getNpcList = { NpcList() }, getPlayerList = { PlayerList() },
            getCollision = { CollisionFlagMap() }, getAreaChecker = { error("unused") }, getAlignment = { error("unused") },
            getLocInteractions = { error("unused") }, getNpcInteractions = { error("unused") }, getPlayerInteractions = { error("unused") },
            getHeldInteractions = { error("unused") }, getWornInteractions = { error("unused") }, getMusicPlayer = { error("unused") },
            getMarketPrices = { error("unused") }, getInstantHitProcessor = { error("unused") }, getTeleportValidator = { error("unused") },
            getWorldRepo = { error("unused") }, getHitModifier = { error("unused") },
        ))
    }

    companion object {
        private val levels = listOf(Int.MIN_VALUE, -1, 0) + (1..255) + Int.MAX_VALUE
        private val selector = RunecraftAction::class.java.declaredMethods.single { it.name == "rollOuraniaRune" }
            .apply { isAccessible = true }

        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
