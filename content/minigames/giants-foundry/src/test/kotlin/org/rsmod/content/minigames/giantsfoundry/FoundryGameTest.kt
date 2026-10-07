package org.rsmod.content.minigames.giantsfoundry

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.util.Wearpos
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.ironman.PlayerGamemode
import org.rsmod.api.player.ironman.setGamemode
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.minigames.giantsfoundry.configs.FoundryData
import org.rsmod.content.minigames.giantsfoundry.configs.FoundryMath
import org.rsmod.content.minigames.giantsfoundry.configs.FoundryMouldPart
import org.rsmod.content.minigames.giantsfoundry.configs.FoundryStation
import org.rsmod.content.minigames.giantsfoundry.state.*
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@OptIn(InternalApi::class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FoundryGameTest {
    @BeforeAll
    fun cache() {
        ServerCacheManager.init(241).close()
        with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
            ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
        }
    }

    @Test
    fun `bars are consumed exactly once and filling the crucible blocks further deposits`() {
        val f = Fixture()
        f.bars("obj.bronze_bar", 28)
        assertEquals(28, f.game.deposit(f.player, "obj.bronze_bar".asRSCM(), 28))
        assertEquals(0, f.player.inv.count("obj.bronze_bar"))
        assertEquals(listOf(28, 0, 0, 0, 0, 0), FoundryGame.metalCounts(f.player))
        assertEquals(2, f.player.vars[FoundryGame.CRUCIBLE_STATE])
        assertEquals(0, f.game.deposit(f.player, "obj.bronze_bar".asRSCM(), 28))
        assertEquals(28, FoundryGame.metalCounts(f.player).sum())
    }

    @Test
    fun `overflow requires consent and a confirmed deposit cannot exceed 28 bars`() {
        val f = Fixture()
        f.flag("varbit.giants_foundry_bronze_count", 27)
        val material = FoundryData.materials.entries.first { it.value.metal.key == "bronze" && it.value.bars > 1 }
        f.player.inv[0] = InvObj(requireNotNull(ServerCacheManager.getItem(material.key)))
        assertEquals(0, f.game.deposit(f.player, material.key, 1))
        assertEquals(1, f.player.inv.physicalCount(requireNotNull(ServerCacheManager.getItem(material.key)).internalName))
        assertEquals(27, FoundryGame.metalCounts(f.player).sum())

        assertEquals(1, f.game.deposit(f.player, material.key, 1, allowWaste = true))
        assertEquals(0, f.player.inv.physicalCount(requireNotNull(ServerCacheManager.getItem(material.key)).internalName))
        assertEquals(28, FoundryGame.metalCounts(f.player).sum())
    }

    @Test
    fun `unsupported items and nonpositive requests preserve inventory and crucible`() {
        val f = Fixture()
        f.player.inv[0] = InvObj("obj.knife")
        f.player.inv[1] = InvObj("obj.coins", 50)
        f.player.inv[2] = InvObj("obj.bronze_bar")
        val before = f.player.inv.toList()
        assertEquals(0, f.game.deposit(f.player, "obj.knife".asRSCM(), 1))
        assertEquals(0, f.game.deposit(f.player, "obj.coins".asRSCM(), 50))
        assertEquals(0, f.game.deposit(f.player, "obj.bronze_bar".asRSCM(), 0))
        assertEquals(0, f.game.deposit(f.player, "obj.bronze_bar".asRSCM(), -1))
        assertEquals(before, f.player.inv.toList())
        assertEquals(0, FoundryGame.metalCounts(f.player).sum())
    }

    @Test
    fun `the next crucible load can be prepared without changing the active preform`() {
        val f = Fixture()
        f.activeSword()
        f.player.inv[0] = InvObj("obj.bronze_bar")
        assertEquals(1, f.game.deposit(f.player, "obj.bronze_bar".asRSCM(), 1))
        assertEquals(0, f.player.inv.count("obj.bronze_bar"))
        assertEquals(listOf(1, 0, 0, 0, 0, 0), FoundryGame.metalCounts(f.player))
        assertEquals(50, f.player.foundryDifficulty)
        assertEquals(94, f.player.foundryStartQuality)
    }

    @Test
    fun `current smithing boost permits moulds and metal until it expires`() {
        val f = Fixture()
        val mould = requireNotNull(FoundryData.mould(FoundryMouldPart.FORTE, 8))
        f.player.statMap.setBaseLevel("stat.smithing", 29)
        f.player.statMap.setCurrentLevel("stat.smithing", 30)
        assertTrue(FoundryGame.canUseMould(f.player, mould))
        f.player.inv[0] = InvObj("obj.steel_bar")
        assertEquals(1, f.game.deposit(f.player, "obj.steel_bar".asRSCM(), 1))
        f.player.statMap.setCurrentLevel("stat.smithing", 29)
        assertFalse(FoundryGame.canUseMould(f.player, mould))
        f.player.inv[0] = InvObj("obj.steel_bar")
        assertEquals(0, f.game.deposit(f.player, "obj.steel_bar".asRSCM(), 1))
        assertEquals(1, f.player.inv.count("obj.steel_bar"))
        assertEquals(listOf(0, 0, 1, 0, 0, 0), FoundryGame.metalCounts(f.player))
    }

    @Test
    fun `each successful uniform proc adds one progress at all three machines without changing XP or temperature`() {
        for (station in FoundryStation.entries) {
            val plain = Fixture()
            val uniform = Fixture()
            for (f in listOf(plain, uniform)) {
                f.activeSword()
                f.player.foundryTemperature = FoundryMath.temperatureRange(station, 50).let { (it.first + it.last) / 2 }
                f.flag("varbit.giants_foundry_seed_0", station.ordinal)
            }
            assertEquals(FoundryGame.WorkResult.Progress, plain.game.work(plain.player, station))
            assertEquals(FoundryGame.WorkResult.Progress, uniform.game.work(uniform.player, station, uniformBonus = true))
            assertEquals(plain.player.foundryCompletion + 1, uniform.player.foundryCompletion)
            assertEquals(plain.player.foundryTemperature, uniform.player.foundryTemperature)
            assertEquals(plain.xp(), uniform.xp())
        }
    }

    @Test
    fun `uniform chance is twenty percent per piece with guaranteed complete outfit including ice gloves`() {
        val f = Fixture()
        val rolls = (0..4).map { roll ->
            object : GameRandom {
                override fun of(maxExclusive: Int): Int { assertEquals(5, maxExclusive); return roll }
                override fun of(minInclusive: Int, maxInclusive: Int): Int = error("Unexpected inclusive draw")
                override fun randomDouble(): Double = error("Unexpected floating point draw")
            }
        }
        assertEquals(0, rolls.count { FoundryGame.uniformBonus(f.player, it) })
        val outfit = FoundryData.uniformPieces.map { it.first() }
        for (pieces in 1..4) {
            for (index in 0 until pieces) f.player.worn[index] = InvObj(outfit[index])
            assertEquals(if (pieces == 4) 5 else pieces, rolls.count { FoundryGame.uniformBonus(f.player, it) })
        }
        f.player.worn[3] = InvObj("obj.smithing_uniform_gloves_ice")
        assertTrue(FoundryGame.uniformBonus(f.player, f.random))
    }

    @Test
    fun `captured adamant commission produces quality94 and cannot be cast twice`() {
        val f = Fixture()
        f.prepareCast()
        assertTrue(f.game.cast(f.player, f.random))
        assertEquals(50, f.player.foundryDifficulty)
        assertEquals(94, f.player.foundryQuality)
        assertEquals(94, f.player.foundryStartQuality)
        assertEquals(2, f.player.foundryMouldState)
        assertEquals(0, FoundryGame.metalCounts(f.player).sum())
        val nativeState = f.player.vars.backing.toMap()
        assertFalse(f.game.cast(f.player, f.random))
        assertEquals(nativeState, f.player.vars.backing.toMap())
    }

    @Test
    fun `incomplete or locked mould selections cannot consume a full crucible`() {
        val f = Fixture()
        f.prepareCast()
        f.flag(FoundryMouldPart.entries.first().selectedVarbit, 0)
        assertFalse(f.game.cast(f.player, f.random))
        assertEquals(28, FoundryGame.metalCounts(f.player).sum())
        val locked = FoundryData.moulds.first { it.part == FoundryMouldPart.entries.first() && it.unlockVarbit != null }
        f.flag(locked.part.selectedVarbit, locked.index)
        assertFalse(f.game.cast(f.player, f.random))
        assertEquals(28, FoundryGame.metalCounts(f.player).sum())
        assertEquals(0, f.player.foundryStartQuality)
    }

    @Test
    fun `forging keeps captured progress across section boundaries`() {
        val f = Fixture()
        f.activeSword()
        f.player.foundryCompletion = 240
        f.player.foundryTemperature = 809
        f.flag("varbit.giants_foundry_seed_0", FoundryStation.HAMMER.ordinal)
        assertEquals(FoundryGame.WorkResult.Progress, f.game.work(f.player, FoundryStation.HAMMER))
        assertEquals(260, f.player.foundryCompletion)
        assertEquals(784, f.player.foundryTemperature)
        assertEquals(94, f.player.foundryQuality)
    }

    @Test
    fun `incorrect heat loses five quality and still applies station heat`() {
        val f = Fixture()
        f.activeSword()
        f.player.foundryTemperature = 970
        f.flag("varbit.giants_foundry_seed_0", FoundryStation.HAMMER.ordinal)
        val xp = f.xp()
        assertEquals(FoundryGame.WorkResult.Damaged, f.game.work(f.player, FoundryStation.HAMMER))
        assertEquals(89, f.player.foundryQuality)
        assertEquals(945, f.player.foundryTemperature)
        assertEquals(0, f.player.foundryCompletion)
        assertEquals(xp, f.xp())
    }

    @Test
    fun `captured finished sword grants its reward once and keeps bank payment atomic`() {
        val f = Fixture()
        f.activeSword(quality = 79, completion = 1000)
        f.player.foundryStartedAt = 100
        val xp = f.xp()
        val reward = f.game.handIn(f.player, now = 451)
        assertEquals(FoundryGame.Reward(79, 6120, 12240, 351), reward)
        assertEquals(12240, f.player.invMap.getOrPut("inv.bank").count("obj.coins"))
        assertEquals(xp + 61200, f.xp())
        assertEquals(79, f.player.vars[FoundryGame.POINTS])
        assertEquals(1, f.player.foundryCompleted)
        assertEquals(79, f.player.foundryBestQuality)
        assertEquals(351, f.player.foundryBestTime)
        assertFalse(FoundryGame.isEquipped(f.player))
        assertEquals(0, f.player.foundryStartQuality)
        assertFalse(FoundryGame.hasCommission(f.player))
        assertNull(f.game.handIn(f.player, now = 452))
        assertEquals(xp + 61200, f.xp())
        assertEquals(12240, f.player.invMap.getOrPut("inv.bank").count("obj.coins"))
    }

    @Test
    fun `failed coin delivery retains the sword commission reputation and experience`() {
        val f = Fixture()
        f.activeSword(quality = 79, completion = 1000)
        f.player.invMap.getOrPut("inv.bank")[0] = InvObj("obj.coins", Int.MAX_VALUE)
        val xp = f.xp()
        assertNull(f.game.handIn(f.player))
        assertEquals(79, f.player.foundryQuality)
        assertTrue(FoundryGame.isEquipped(f.player))
        assertTrue(FoundryGame.hasCommission(f.player))
        assertEquals(0, f.player.foundryCompleted)
        assertEquals(0, f.player.vars[FoundryGame.POINTS])
        assertEquals(xp, f.xp())
    }

    @Test
    fun `ultimate ironman payment waits for inventory space rather than losing the preform`() {
        val f = Fixture()
        f.player.setGamemode(PlayerGamemode.ULTIMATE_IRONMAN)
        f.activeSword(quality = 79, completion = 1000)
        repeat(28) { f.player.inv[it] = InvObj("obj.knife") }
        assertNull(f.game.handIn(f.player))
        assertTrue(FoundryGame.isEquipped(f.player))
        assertEquals(0, f.player.foundryCompleted)
        f.player.inv[0] = null
        assertNotNull(f.game.handIn(f.player))
        assertEquals(12240, f.player.inv.count("obj.coins"))
        assertEquals(0, f.player.invMap.getOrPut("inv.bank").count("obj.coins"))
    }

    @Test
    fun `a ruined unfinished sword receives the recorded minimum reward without reputation`() {
        val f = Fixture()
        f.activeSword(quality = 0, completion = 670)
        val reward = f.game.handIn(f.player)
        assertNotNull(reward)
        assertEquals(30, reward!!.xp)
        assertEquals(60, reward.coins)
        assertEquals(0, f.player.vars[FoundryGame.POINTS])
        assertEquals(1, f.player.foundryCompleted)
    }

    @Test
    fun `unfinished sound swords cannot be handed in for an early reward`() {
        val f = Fixture()
        f.activeSword(quality = 79, completion = 999)
        assertNull(f.game.handIn(f.player))
        assertTrue(FoundryGame.isEquipped(f.player))
        assertEquals(999, f.player.foundryCompletion)
        assertEquals(0, f.player.foundryCompleted)
    }

    @Test
    fun `storing for logout preserves native sword and unlocks while clearing activity`() {
        val f = Fixture()
        f.activeSword(quality = 79, completion = 620)
        f.player.foundryTemperature = 350
        f.player.foundrySweetUntil = 110
        f.player.foundrySweetClaimed = true
        f.player.foundryWorkStation = 2
        f.flag("varbit.giants_foundry_unlocked_mould_ricasso_7", 1)
        f.flag("varbit.giants_foundry_reward_shop_filter", 5)
        f.interfaces.showHud(f.player)
        f.game.store(f.player)

        assertFalse(FoundryGame.isEquipped(f.player))
        assertEquals(1, f.player.foundryStored)
        assertEquals(0, f.player.foundryWorkStation)
        assertEquals(0, f.player.foundrySweetUntil)
        assertFalse(f.player.foundrySweetClaimed)
        assertFalse(f.player.ui.containsOverlay(FoundryInterfaces.HUD))
        val reloaded = Player()
        reloaded.vars.backing.putAll(f.player.vars.backing)
        assertEquals(79, reloaded.foundryQuality)
        assertEquals(94, reloaded.foundryStartQuality)
        assertEquals(620, reloaded.foundryCompletion)
        assertEquals(350, reloaded.foundryTemperature)
        assertEquals(4, reloaded.foundryWord1)
        assertEquals(5, reloaded.foundryWord2)
        assertEquals(1, reloaded.vars["varbit.giants_foundry_unlocked_mould_ricasso_7"])
        assertEquals(5, reloaded.vars["varbit.giants_foundry_reward_shop_filter"])
    }

    @Test
    fun `cancelling clears an active sword without clearing unlocks reputation or collected bars`() {
        val f = Fixture()
        f.activeSword()
        f.flag("varbit.giants_foundry_unlocked_mould_blade_11", 1)
        f.flag(FoundryGame.POINTS, 500)
        f.flag("varbit.giants_foundry_bronze_count", 12)
        f.game.cancel(f.player)
        assertFalse(FoundryGame.hasCommission(f.player))
        assertFalse(FoundryGame.isEquipped(f.player))
        assertEquals(0, f.player.foundryStartQuality)
        assertEquals(0, f.player.foundryStored)
        assertEquals(1, f.player.vars["varbit.giants_foundry_unlocked_mould_blade_11"])
        assertEquals(500, f.player.vars[FoundryGame.POINTS])
        assertEquals(12, f.player.vars["varbit.giants_foundry_bronze_count"])
    }

    private class Fixture {
        val events = EventBus()
        val interfaces = FoundryInterfaces(events)
        val game = FoundryGame(interfaces, events, XpModifiers(emptySet()))
        val player = Player().apply {
            inv = Inventory.create("inv.inv")
            worn = Inventory.create("inv.worn")
            statMap.setBaseLevel("stat.smithing", 99)
            statMap.setCurrentLevel("stat.smithing", 99)
            currentMapClock = 100
        }
        val random = object : GameRandom {
            override fun of(maxExclusive: Int): Int = 0
            override fun of(minInclusive: Int, maxInclusive: Int): Int = minInclusive
            override fun randomDouble(): Double = 0.0
        }

        fun flag(symbol: String, value: Int) = VarPlayerIntMapSetter.set(player, symbol, value)
        fun xp(): Int = player.statMap.getFineXP("stat.smithing")
        fun bars(symbol: String, count: Int) {
            repeat(count) { player.inv[it] = InvObj(symbol) }
        }

        fun prepareCast() {
            player.foundryWord1 = 4
            player.foundryWord2 = 5
            player.foundryMouldState = 1
            listOf(8, 8, 6).forEachIndexed { index, selected -> flag(FoundryMouldPart.entries[index].selectedVarbit, selected) }
            flag("varbit.giants_foundry_adamant_count", 28)
            flag(FoundryGame.CRUCIBLE_STATE, 2)
        }

        fun activeSword(quality: Int = 94, completion: Int = 0) {
            player.foundryWord1 = 4
            player.foundryWord2 = 5
            player.foundryQuality = quality
            player.foundryStartQuality = 94
            player.foundryDifficulty = 50
            player.foundryCompletion = completion
            player.foundryMouldState = 0
            player.worn[Wearpos.RightHand.slot] = InvObj(FoundryGame.PREFORM)
        }
    }
}
