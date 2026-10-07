package org.rsmod.content.minigames.blastfurnace

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.types.InventoryServerType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.events.PlayerTimerEvent
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.minigames.blastfurnace.configs.BlastFurnaceParams
import org.rsmod.content.minigames.blastfurnace.core.*
import org.rsmod.content.minigames.blastfurnace.scripts.ConveyorScript
import org.rsmod.content.minigames.blastfurnace.scripts.ForemanScript
import org.rsmod.content.minigames.blastfurnace.scripts.SmeltingProcess
import org.rsmod.content.minigames.blastfurnace.scripts.foremanFeeGp
import org.rsmod.content.minigames.blastfurnace.scripts.foremanPermission
import org.rsmod.content.minigames.blastfurnace.scripts.hasBlastFurnaceAccess
import org.rsmod.content.skills.smithing.coalbag.CoalBag
import org.rsmod.events.EventBus
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.InvVirtualStorage
import org.rsmod.game.inv.InvVirtualStorageHolder
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.plugin.scripts.ScriptContext

@ResourceLock("ServerCacheManager")
class BlastFurnaceProcessingTest {
    private val smelting = SmeltingProcess(XpModifiers(emptySet()))

    @Test
    @ResourceLock("InvVirtualStorageHolder")
    fun `conveyor counts loose and bagged coal exactly once`() {
        val conveyor = ConveyorScript(EventBus())
        val previousStorage = InvVirtualStorageHolder.instance
        try {
            InvVirtualStorageHolder.instance = InvVirtualStorage { player, _, ore ->
                if (ore == "obj.coal" && CoalBag.hasBagInInventory(player)) {
                    CoalBag.storedAmount(player)
                } else {
                    0
                }
            }
            for ((bag, loose, stored) in listOf(
                Triple(null, 9, 0),
                Triple("obj.coal_bag", 0, 27),
                Triple("obj.coal_bag", 9, 27),
                Triple("obj.coal_bag_open", 9, 27),
                Triple("obj.coal_bag", 9, 0),
                Triple(null, 9, 27),
            )) {
                val player = player()
                player.inv.owner = player
                repeat(loose) { player.inv[it] = InvObj("obj.coal") }
                if (bag != null) player.inv[loose] = InvObj(bag)
                CoalBag.addStored(player, stored)

                val expected = loose + if (bag != null) stored else 0
                assertEquals(expected, conveyor.heldAmount(player, "obj.coal"), "$bag: $loose loose, $stored stored")
                assertEquals(loose, player.inv.physicalCount("obj.coal"))
                assertEquals(stored, CoalBag.storedAmount(player))
            }
        } finally {
            InvVirtualStorageHolder.instance = previousStorage
        }
    }

    @Test
    fun `collection resumes waiting ore without a dialogue-closing queue`() {
        val player = player()
        val events = EventBus()
        with(smelting) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
        player.blastFurnaceGoldBars = 27
        player.blastFurnaceGoldOre = 2

        events.publish(BlastFurnaceSmeltRequest(player))

        assertEquals(28, player.blastFurnaceGoldBars)
        assertEquals(1, player.blastFurnaceGoldOre)
        assertEquals(225, player.statMap.getFineXP("stat.smithing"))
        assertTrue(player.queueList.isEmpty)
        events.publish(BlastFurnaceSmeltRequest(player))
        assertEquals(225, player.statMap.getFineXP("stat.smithing"))
        assertEquals(1, player.blastFurnaceGoldOre)
        assertTrue(player.queueList.isEmpty)
    }

    @Test
    fun `automatic smelting uses half coal for steel through rune`() {
        for ((metal, ore, coal) in listOf(
            Triple("steel", "iron", 1), Triple("mithril", "mithril", 2),
            Triple("adamantite", "adamantite", 3), Triple("runite", "runite", 4),
        )) {
            val player = player()
            player.setBlastFurnaceOreCount("obj.${ore}_ore", 5)
            player.blastFurnaceCoal = coal * 5
            smelting.processPot(player)
            assertEquals(5, player.blastFurnaceBarCount("obj.${metal}_bar"), metal)
            assertEquals(0, player.blastFurnaceOreCount("obj.${ore}_ore"), metal)
            assertEquals(0, player.blastFurnaceCoal, metal)
            assertEquals(SmeltingProcess.DISPENSER_POURING, player.blastFurnaceBarsHot)
        }
    }

    @Test
    fun `iron without coal always succeeds and cannot award twice`() {
        val player = player()
        player.blastFurnaceIronOre = 28
        smelting.processPot(player)
        assertEquals(28, player.blastFurnaceIronBars)
        assertEquals(0, player.blastFurnaceIronOre)
        val xp = player.statMap.getFineXP("stat.smithing")
        assertEquals(28 * 125, xp)
        smelting.processPot(player)
        assertEquals(xp, player.statMap.getFineXP("stat.smithing"))
        assertEquals(28, player.blastFurnaceIronBars)
    }

    @Test
    fun `insufficient smithing leaves ore and coal untouched`() {
        val player = player(49)
        player.blastFurnaceMithrilOre = 10
        player.blastFurnaceCoal = 20
        smelting.processPot(player)
        assertEquals(10, player.blastFurnaceMithrilOre)
        assertEquals(20, player.blastFurnaceCoal)
        assertEquals(0, player.blastFurnaceMithrilBars)
        assertEquals(0, player.statMap.getFineXP("stat.smithing"))
    }

    @Test
    fun `goldsmith gauntlets award the native alternate xp`() {
        val plain = player()
        val gloves = player()
        gloves.worn[0] = InvObj("obj.gauntlets_of_goldsmithing")
        for (player in listOf(plain, gloves)) {
            player.blastFurnaceGoldOre = 1
            smelting.processPot(player)
            assertEquals(1, player.blastFurnaceGoldBars)
        }
        assertEquals(225, plain.statMap.getFineXP("stat.smithing"))
        assertEquals(562, gloves.statMap.getFineXP("stat.smithing"))
    }

    @Test
    fun `catalyst doubles eligible xp without another coal discount`() {
        val player = player()
        player.blastFurnaceMithrilOre = 3
        player.blastFurnaceCoal = 4
        player.blastFurnaceCatalyst = 1
        smelting.processPot(player)
        assertEquals(2, player.blastFurnaceMithrilBars)
        assertEquals(1, player.blastFurnaceMithrilOre)
        assertEquals(0, player.blastFurnaceCoal)
        assertEquals(0, player.blastFurnaceCatalyst)
        assertEquals(900, player.statMap.getFineXP("stat.smithing"))
    }

    @Test
    fun `dispenser cap retains excess ore and player stocks stay separate`() {
        val player = player()
        val other = player()
        player.blastFurnaceGoldBars = 27
        player.blastFurnaceGoldOre = 5
        smelting.processPot(player)
        assertEquals(28, player.blastFurnaceGoldBars)
        assertEquals(4, player.blastFurnaceGoldOre)
        assertEquals(0, other.blastFurnaceGoldBars)
        assertEquals(0, other.blastFurnaceGoldOre)
        smelting.processPot(player)
        assertEquals(4, player.blastFurnaceGoldOre)
        assertEquals(225, player.statMap.getFineXP("stat.smithing"))
    }

    @Test
    fun `logout flushes reserved ore once before saving`() {
        val player = player()
        val events = EventBus()
        with(smelting) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
        player.blastFurnaceCoal = 10
        player.addBeltInFlight("obj.coal", 5)
        player.addBeltInFlight("obj.iron_ore", 5)
        events.publish(SessionStateEvent.Logout(player))
        assertEquals(15, player.blastFurnaceCoal)
        assertEquals(5, player.blastFurnaceIronOre)
        assertEquals(0, player.beltInFlight("obj.coal"))
        assertEquals(0, player.beltInFlight("obj.iron_ore"))
        assertEquals(0, player.statMap.getFineXP("stat.smithing"))
        events.publish(SessionStateEvent.Logout(player))
        assertEquals(15, player.blastFurnaceCoal)
        smelting.processPot(player)
        assertEquals(5, player.blastFurnaceSteelBars)
    }

    @Test
    fun `belt arrival releases reservation and bars cool automatically after pouring`() {
        val player = player()
        val events = EventBus()
        with(smelting) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
        player.addBeltInFlight("obj.gold_ore", 5)
        smelting.deliverLoad(player, BlastFurnaceBeltLoad(listOf(BlastFurnaceBeltLoad.BeltEntry("obj.gold_ore", 5))))
        assertEquals(0, player.beltInFlight("obj.gold_ore"))
        assertEquals(5, player.blastFurnaceGoldOre)
        smelting.processPot(player)
        events.publish(PlayerTimerEvent.Soft(player, BlastFurnaceParams.HOT_TIMER.asRSCM()))
        assertEquals(SmeltingProcess.DISPENSER_HOT, player.blastFurnaceBarsHot)
        assertTrue(BlastFurnaceParams.HOT_TIMER in player.softTimerMap)
        val xp = player.statMap.getFineXP("stat.smithing")
        events.publish(PlayerTimerEvent.Soft(player, BlastFurnaceParams.HOT_TIMER.asRSCM()))
        assertEquals(SmeltingProcess.DISPENSER_COOLED, player.blastFurnaceBarsHot)
        assertEquals(5, player.blastFurnaceGoldBars)
        assertEquals(xp, player.statMap.getFineXP("stat.smithing"))
        assertFalse(BlastFurnaceParams.HOT_TIMER in player.softTimerMap)
    }

    @Test
    fun `a stale heat timer cannot reheat cooled bars or refill an empty dispenser`() {
        val player = player()
        val events = EventBus()
        with(smelting) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
        for (state in listOf(SmeltingProcess.DISPENSER_COOLED, SmeltingProcess.DISPENSER_EMPTY)) {
            player.blastFurnaceBarsHot = state
            player.softTimer(BlastFurnaceParams.HOT_TIMER, 1)
            events.publish(PlayerTimerEvent.Soft(player, BlastFurnaceParams.HOT_TIMER.asRSCM()))
            assertEquals(state, player.blastFurnaceBarsHot)
            assertFalse(BlastFurnaceParams.HOT_TIMER in player.softTimerMap)
        }
    }

    @Test
    fun `foreman free access uses base level and paid access expires`() {
        val player = player(59)
        player.foremanPermission = true
        player.statMap.setCurrentLevel("stat.smithing", 65)
        assertFalse(player.hasBlastFurnaceAccess())
        player.softTimer(BlastFurnaceParams.FOREMAN_FEE_TIMER, BlastFurnaceParams.FOREMAN_FEE_WINDOW_TICKS)
        assertTrue(player.hasBlastFurnaceAccess())
        assertEquals(1000, BlastFurnaceParams.FOREMAN_FEE_WINDOW_TICKS)
        val events = EventBus()
        with(ForemanScript()) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
        events.publish(PlayerTimerEvent.Soft(player, BlastFurnaceParams.FOREMAN_FEE_TIMER.asRSCM()))
        assertFalse(player.hasBlastFurnaceAccess())
        player.statMap.setBaseLevel("stat.smithing", 60)
        assertTrue(player.hasBlastFurnaceAccess())
        assertEquals(2500, player.foremanFeeGp())
        player.worn[0] = InvObj("obj.ring_of_charos_unlocked")
        assertEquals(1250, player.foremanFeeGp())
    }

    private fun player(level: Int = 99) = Player().apply {
        inv = Inventory(InventoryServerType(size = 28), arrayOfNulls(28))
        worn = Inventory(InventoryServerType(size = 14), arrayOfNulls(14))
        xpRate = 1.0
        globalXpRate = 1.0
        statMap.setBaseLevel("stat.smithing", level.toByte())
        statMap.setCurrentLevel("stat.smithing", level.toByte())
        client = object : Client<Any, Any> {
            override fun write(message: Any) {}
            override fun close() {}
            override fun read(player: Player) {}
            override fun flush() {}
            override fun flushHighPriority() {}
            override fun unregister(service: Any, player: Player) {}
        }
    }

    companion object {
        @JvmStatic @BeforeAll fun cache() = BlastFurnaceCacheContractTest.loadCache()
    }
}
