package org.rsmod.content.minigames.gotr

import dev.openrune.ServerCacheManager
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
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContext
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.table.ComboruneRecipeRow
import org.rsmod.api.table.runecrafting.RunecraftingRunesRow
import org.rsmod.content.skills.runecrafting.action.RunecraftAction
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
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GotrAltarTest {
    @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    private val restore = mutableListOf<() -> Unit>()

    @BeforeEach fun inventoryServices() {
        replaceStatic("org.rsmod.api.invtx.InvTransactionsScriptKt", "cachedInventoryTransactions", InvTransactions.from())
        replaceStatic("org.rsmod.api.invtx.VirtualInvTransactionsKt", "cachedPlayerItemStorage", PlayerItemStorage(emptySet()))
    }

    @AfterEach fun restoreServices() { restore.asReversed().forEach { it() }; restore.clear() }
    private fun replaceStatic(owner: String, name: String, value: Any) {
        val field = Class.forName(owner).getDeclaredField(name).apply { isAccessible = true }
        val previous = field.get(null)
        restore += { field.set(null, previous) }
        field.set(null, value)
    }

    @Test fun `every ordinary altar can charge its native cell tier without essence or XP`() {
        val tiers = listOf(listOf("air", "mind", "body"), listOf("water", "cosmic", "chaos"),
            listOf("earth", "nature", "law"), listOf("fire", "death", "blood"))
        for ((index, runes) in tiers.withIndex()) for (rune in runes) {
            val f = Fixture()
            f.item(0, "obj.gotr_cell_uncharged", 10)
            f.craft(rune)
            assertEquals(9, f.count("obj.gotr_cell_uncharged"), rune)
            assertEquals(1, f.count("obj.gotr_cell_tier${index + 1}"), rune)
            assertEquals(0, f.count("obj.${rune}rune"), rune)
            assertEquals(0, f.xp(), rune)
        }
    }

    @Test fun `a full inventory preserves stacked uncharged cells but replaces its final cell`() {
        val f = Fixture()
        f.fill()
        f.item(0, "obj.gotr_cell_uncharged", 2)
        f.craft("fire")
        assertEquals(2, f.count("obj.gotr_cell_uncharged"))
        assertEquals(0, f.count("obj.gotr_cell_tier4"))
        assertEquals(0, f.xp())
        f.item(0, "obj.gotr_cell_uncharged", 1)
        f.craft("fire")
        assertEquals(0, f.count("obj.gotr_cell_uncharged"))
        assertEquals(1, f.count("obj.gotr_cell_tier4"))
        assertEquals(0, f.xp())
    }

    @Test fun `mixed ordinary and guardian essence grants stones only for consumed guardian essence`() {
        val f = Fixture()
        repeat(5) { f.item(it, "obj.blankrune_high") }
        repeat(3) { f.item(it + 5, GUARDIAN_ESSENCE) }
        f.item(8, "obj.gotr_cell_uncharged", 10)
        f.craft("earth")
        assertEquals(0, f.count("obj.blankrune_high"))
        assertEquals(0, f.count(GUARDIAN_ESSENCE))
        assertEquals(32, f.count("obj.earthrune"))
        assertEquals(3, f.count(GotrGame.STONE_ELEMENTAL))
        assertEquals(1, f.count("obj.gotr_cell_tier3"))
        assertEquals(9, f.count("obj.gotr_cell_uncharged"))
    }

    @Test fun `single guardian essence in a full inventory preserves the craft until both outputs fit`() {
        val f = Fixture()
        f.fill()
        f.item(0, GUARDIAN_ESSENCE)
        f.craft("air")
        assertEquals(1, f.count(GUARDIAN_ESSENCE))
        assertEquals(0, f.count("obj.airrune"))
        assertEquals(0, f.count(GotrGame.STONE_ELEMENTAL))
        assertEquals(0, f.xp())
        f.player.inv[1] = null
        f.craft("air")
        assertEquals(0, f.count(GUARDIAN_ESSENCE))
        assertEquals(10, f.count("obj.airrune"))
        assertEquals(1, f.count(GotrGame.STONE_ELEMENTAL))
        assertTrue(f.xp() > 0)
    }

    @Test fun `full guardian inventory frees its consumed slots before reserving both outputs`() {
        val f = Fixture()
        repeat(28) { f.item(it, GUARDIAN_ESSENCE) }
        f.craft("air")
        assertEquals(0, f.count(GUARDIAN_ESSENCE))
        assertEquals(280, f.count("obj.airrune"))
        assertEquals(28, f.count(GotrGame.STONE_ELEMENTAL))
    }

    @Test fun `mixed ordinary essence has no guardian-stone reward outside the encounter`() {
        val f = Fixture()
        f.flag("varbit.gotr_is_playing", 0)
        f.item(0, "obj.blankrune_high")
        f.item(1, "obj.gotr_cell_uncharged", 10)
        f.craft("earth")
        assertEquals(4, f.count("obj.earthrune"))
        assertEquals(0, f.count(GotrGame.STONE_ELEMENTAL))
        assertEquals(10, f.count("obj.gotr_cell_uncharged"))
    }

    @Test fun `existing charged cell and completed round prevent another cell charge`() {
        for (completed in listOf(false, true)) {
            val f = Fixture()
            f.item(0, "obj.blankrune_high")
            f.item(1, "obj.gotr_cell_uncharged", 10)
            if (completed) f.flag("varbit.gotr_player_has_finished_game", 1)
            else f.item(2, "obj.gotr_cell_tier1")
            f.craft("fire")
            assertEquals(10, f.count("obj.gotr_cell_uncharged"))
            assertEquals(0, f.count("obj.gotr_cell_tier4"))
            assertEquals(3, f.count("obj.firerune"))
        }
    }

    @Test fun `combination craft preserves guardian essence and input runes when output slots do not fit`() {
        val f = Fixture(randomValue = 0)
        f.fill()
        f.flag("varbit.magic_imbue_active", 1)
        f.item(0, GUARDIAN_ESSENCE)
        f.item(1, "obj.earthrune", 10)
        f.combo()
        assertEquals(1, f.count(GUARDIAN_ESSENCE))
        assertEquals(10, f.count("obj.earthrune"))
        assertEquals(0, f.xp())
        f.player.inv[2] = null
        f.combo()
        assertEquals(0, f.count(GUARDIAN_ESSENCE))
        assertEquals(9, f.count("obj.earthrune"))
        assertEquals(1, f.count("obj.lavarune"))
        assertEquals(1, f.count(GotrGame.STONE_POLYELEMENTAL))
    }

    @Test fun `aether craft reserves rune and stone outputs before consuming guardian soul or catalyst`() {
        val f = Fixture(randomValue = 0)
        f.fill()
        f.flag("varbit.magic_imbue_active", 1)
        f.item(0, GUARDIAN_ESSENCE)
        f.item(1, "obj.soulrune", 10)
        f.item(2, "obj.cosmic_soul_catalyst", 10)
        f.aether()
        assertEquals(1, f.count(GUARDIAN_ESSENCE))
        assertEquals(10, f.count("obj.soulrune"))
        assertEquals(10, f.count("obj.cosmic_soul_catalyst"))
        assertEquals(0, f.xp())
        f.player.inv[3] = null
        f.aether()
        assertEquals(0, f.count(GUARDIAN_ESSENCE))
        assertEquals(9, f.count("obj.soulrune"))
        assertEquals(9, f.count("obj.cosmic_soul_catalyst"))
        assertEquals(1, f.count("obj.aetherrune"))
        assertEquals(1, f.count(GotrGame.STONE_POLYCATALYTIC))
    }

    @Test fun `failed aether attempt grants consumed guardian stone and cell without consuming catalyst or granting XP`() {
        val f = Fixture(randomValue = 99)
        f.flag("varbit.magic_imbue_active", 1)
        f.item(0, GUARDIAN_ESSENCE)
        f.item(1, "obj.soulrune", 10)
        f.item(2, "obj.cosmic_soul_catalyst", 10)
        f.item(3, "obj.gotr_cell_uncharged", 10)
        f.aether()
        assertEquals(0, f.count(GUARDIAN_ESSENCE))
        assertEquals(9, f.count("obj.soulrune"))
        assertEquals(10, f.count("obj.cosmic_soul_catalyst"))
        assertEquals(0, f.count("obj.aetherrune"))
        assertEquals(1, f.count(GotrGame.STONE_POLYCATALYTIC))
        assertEquals(1, f.count("obj.gotr_cell_tier2"))
        assertEquals(0, f.xp())
    }

    private class Fixture(randomValue: Int = 249) {
        val events = EventBus()
        val random = object : GameRandom {
            override fun of(maxExclusive: Int) = randomValue.coerceAtMost(maxExclusive - 1)
            override fun of(minInclusive: Int, maxInclusive: Int) = minInclusive
            override fun randomDouble() = 0.0
        }
        val player = Player().apply {
            uuid = 1L; slotId = 1; displayName = "Altar test"; currentMapClock = 100
            coords = CoordGrid(2841, 4829)
            inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
            worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
            invMap["inv.bank"] = Inventory(InventoryServerType(size = 800), arrayOfNulls(800))
            statMap.setBaseLevel("stat.runecrafting", 99)
            statMap.setCurrentLevel("stat.runecrafting", 99)
        }
        init { flag("varbit.gotr_is_playing", 1) }
        fun flag(symbol: String, value: Int) = VarPlayerIntMapSetter.set(player, symbol, value)
        fun item(slot: Int, symbol: String, count: Int = 1) { player.inv[slot] = InvObj(symbol, count) }
        fun fill() { repeat(28) { item(it, "obj.harpoon") } }
        fun count(symbol: String) = player.inv.count(symbol)
        fun xp() = player.statMap.getFineXP("stat.runecrafting")
        fun craft(rune: String) = run { with(RunecraftAction) { craftRune(RunecraftingRunesRow.all().first { it.output.internalName == "obj.${rune}rune" }, XpModifiers(emptySet())) } }
        fun combo() = run { with(RunecraftAction) { craftCombination(ComboruneRecipeRow.all().first { it.output?.internalName == "obj.lavarune" && it.input?.internalName == "obj.earthrune" }, XpModifiers(emptySet()), "obj.firerune") } }
        fun aether() = run { with(RunecraftAction) { craftAether(XpModifiers(emptySet())) } }
        fun run(block: suspend ProtectedAccess.() -> Unit): GameCoroutine = player.launch {
            val access = ProtectedAccess(player, this, ProtectedAccessContext(
                getRandom = { random }, getEventBus = { events }, getNpcList = { NpcList() }, getPlayerList = { PlayerList() },
                getCollision = { CollisionFlagMap() }, getAreaChecker = { error("unused") }, getAlignment = { error("unused") },
                getLocInteractions = { error("unused") }, getNpcInteractions = { error("unused") }, getPlayerInteractions = { error("unused") },
                getHeldInteractions = { error("unused") }, getWornInteractions = { error("unused") }, getMusicPlayer = { error("unused") },
                getMarketPrices = { error("unused") }, getInstantHitProcessor = { error("unused") }, getTeleportValidator = { error("unused") },
                getWorldRepo = { error("unused") }, getHitModifier = { error("unused") },
            ))
            access.block()
        }
    }
    companion object { private const val GUARDIAN_ESSENCE = "obj.gotr_guardian_essence" }
}
