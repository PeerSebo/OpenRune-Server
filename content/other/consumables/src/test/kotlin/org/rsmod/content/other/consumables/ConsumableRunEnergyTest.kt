package org.rsmod.content.other.consumables

import com.google.inject.AbstractModule
import com.google.inject.Guice
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import net.rsprot.protocol.game.outgoing.misc.player.UpdateRunEnergy
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.inv.storage.PlayerItemStorage
import org.rsmod.api.invtx.InvTransactionsScript
import org.rsmod.api.player.events.interact.HeldObjEvents
import org.rsmod.api.player.hook.PlayerRestrictions
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessContextFactory
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.table.FoodRow
import org.rsmod.api.table.PotionEffectRow
import org.rsmod.api.table.PotionRow
import org.rsmod.content.other.consumables.food.ConsumeFoodScript
import org.rsmod.content.other.consumables.food.FoodEffectService
import org.rsmod.content.other.consumables.food.FoodSpecialEffectService
import org.rsmod.content.other.consumables.potion.ConsumePotionScript
import org.rsmod.content.other.consumables.potion.PotionEffectService
import org.rsmod.content.other.consumables.potion.staminaExpiresAt
import org.rsmod.content.other.consumables.potion.toa.ToaSmellingSaltsEffect
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.area.AreaIndex
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.EngineQueueCache
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext
import sun.misc.Unsafe

@OptIn(InternalApi::class)
@ResourceLock("ServerCacheManager")
class ConsumableRunEnergyTest {
    @Test
    fun `unimplemented activity supplies retain their dose and cannot apply effects`() {
        for (symbol in listOf(
            "obj.nzone4doseoverloadpotion", "obj.raids_vial_xericaid_weak_4",
            "obj.toa_supply_heal_4", "obj.4dosemoonlightpotion",
        )) {
            val f = Fixture()
            val beforeVars = f.player.vars.backing.toMap()
            f.consume(symbol)
            assertEquals(InvObj(symbol), f.player.inv[0], symbol)
            assertEquals(5000, f.player.runEnergy, symbol)
            assertEquals(beforeVars, f.player.vars.backing.toMap(), symbol)
            assertTrue(f.client.messages.any { it.toString().contains("can only") }, symbol)
        }
    }

    @Test
    fun `registered energy potion doses restore authored percentages and consume one dose`() {
        val cases = listOf("energy_potion" to 10, "super_energy" to 20, "extreme_energy_potion" to 40)
        for ((name, percent) in cases) {
            val f = Fixture()
            val potion = PotionRow.getRow("dbrow.$name")
            f.consume(potion.items.first().internalName)
            assertEquals(potion.items[1].id, f.player.inv[0]?.id, name)
            assertEquals(5000 + percent * 100, f.player.runEnergy, name)
            assertEquals(listOf(5000 + percent * 100), f.energyPackets(), name)
            assertEquals("seq.human_eat".asRSCM(), f.player.pendingSequence.id)
            val after = f.player.inv.objs.toList()
            f.consumeExisting()
            assertEquals(after, f.player.inv.objs.toList(), "The second click must respect the drink delay.")
            assertEquals(1, f.energyPackets().size)
        }
    }

    @Test
    fun `stamina restores twenty percent while keeping its native countdown and expiry`() {
        val f = Fixture()
        val potion = PotionRow.getRow("dbrow.stamina_potion")
        val effect = PotionEffectRow.getRow("dbrow.effect_stamina")
        f.consume(potion.items.first().internalName)
        assertEquals(7000, f.player.runEnergy)
        assertEquals(listOf(7000), f.energyPackets())
        assertEquals(1, f.player.vars[PotionEffectService.STAMINA_VARBIT])
        assertEquals(f.clock.cycle + effect.duration, f.player.staminaExpiresAt)
        assertEquals((effect.duration + 9) / 10, f.player.vars[PotionEffectService.STAMINA_DURATION_VARBIT])
        assertEquals(potion.items[1].id, f.player.inv[0]?.id)
    }

    @Test
    fun `Guthix rest restores five percent and retains its cup dose transition`() {
        val f = Fixture()
        val potion = PotionRow.getRow("dbrow.guthix_rest")
        f.consume(potion.items.first().internalName)
        assertEquals(5500, f.player.runEnergy)
        assertEquals(listOf(5500), f.energyPackets())
        assertEquals(potion.items[1].id, f.player.inv[0]?.id)
        assertEquals(30, f.player.statMap.getCurrentLevel("stat.hitpoints").toInt())
    }

    @Test
    fun `food operations retain their authored energy gains and maximum clamp`() {
        val cases = listOf("summer_pie" to 10, "papaya_fruit" to 5, "yellowfin" to 20,
            "strange_fruit" to 30, "mint_cake" to 50)
        for ((name, percent) in cases) {
            for (start in listOf(0, 5000, 9950, 10000)) {
                val f = Fixture(start)
                val food = FoodRow.getRow("dbrow.$name")
                f.consume(food.items.first().internalName)
                val expected = minOf(10000, start + percent * 100)
                assertEquals(expected, f.player.runEnergy, "$name/$start")
                assertEquals(if (expected == start) emptyList() else listOf(expected), f.energyPackets())
                assertNotEquals(food.items.first().id, f.player.inv[0]?.id)
                if (name == "summer_pie") {
                    assertEquals("obj.half_summer_pie".asRSCM(), f.player.inv[0]?.id)
                    assertEquals(55, f.player.statMap.getCurrentLevel("stat.agility").toInt())
                }
            }
        }
    }

    @Test
    fun `purple sweets restore one percent without lowering existing energy`() {
        for (start in listOf(0, 5000, 9950, 10000)) {
            val f = Fixture(start)
            f.consume("obj.trail_sweets", count = 2)
            val expected = minOf(10000, start + 100)
            assertEquals(expected, f.player.runEnergy)
            assertTrue(f.player.runEnergy >= start)
            assertEquals(if (start == expected) emptyList() else listOf(expected), f.energyPackets())
            assertEquals(1, f.player.inv.count("obj.trail_sweets"))
            assertEquals(listOf(1 to 3), f.random.ranges)
        }
    }

    @Test
    fun `white tree fruit uses the original random five and ten percent endpoints`() {
        for (upper in listOf(false, true)) {
            val f = Fixture(5000, upper)
            f.consume("obj.garden_white_tree_fruit")
            val expected = if (upper) 6000 else 5500
            assertEquals(expected, f.player.runEnergy)
            assertEquals(listOf(expected), f.energyPackets())
            assertEquals(listOf(5 to 10), f.random.ranges)
        }
    }

    @Test
    fun `dashing kebbit energy arrives only with the delayed heal and cannot repeat`() {
        val f = Fixture()
        f.consume("obj.dashingkebbit_cooked")
        assertEquals(5000, f.player.runEnergy)
        assertTrue(f.energyPackets().isEmpty())
        val timer = checkNotNull(f.player.timerMap[FoodSpecialEffectService.DELAYED_HEAL_TIMER.asRSCM().toShort()])
        assertEquals(1007, f.player.timerMap.extractExpiry(timer))
        f.at(1006)
        f.run { f.specialFoods.processDelayedHeals(this) }
        assertEquals(5000, f.player.runEnergy)
        f.at(1007)
        f.run { f.specialFoods.processDelayedHeals(this) }
        assertEquals(6000, f.player.runEnergy)
        assertEquals(listOf(6000), f.energyPackets())
        f.run { f.specialFoods.processDelayedHeals(this) }
        assertEquals(listOf(6000), f.energyPackets())
    }

    @Test
    fun `smelling salts restores twenty five percent on the existing refresh cadence`() {
        val f = Fixture()
        val salts = f.injector.getInstance(ToaSmellingSaltsEffect::class.java)
        f.run { salts.apply(this) }
        assertEquals(7500, f.player.runEnergy)
        assertEquals(listOf(7500), f.energyPackets())
        f.at(1025)
        f.run { salts.process(this) }
        assertEquals(10000, f.player.runEnergy)
        assertEquals(listOf(7500, 10000), f.energyPackets())
        f.at(1050)
        f.run { salts.process(this) }
        assertEquals(listOf(7500, 10000), f.energyPackets())
    }

    private class Fixture(energy: Int = 5000, upperRoll: Boolean = false) {
        val clock = MapClock(1000)
        val injector = Guice.createInjector(ConsumablesModule(), object : AbstractModule() {
            override fun configure() { bind(MapClock::class.java).toInstance(clock) }
        })
        val client = RecordingClient()
        val player = Player(client).apply {
            inv = Inventory.create("inv.inv")
            worn = Inventory.create("inv.worn")
            coords = CoordGrid(3222, 3218, 0)
            runEnergy = energy
            currentMapClock = clock.cycle
            processedMapClock = clock.cycle
            for (stat in listOf("attack", "strength", "defence", "ranged", "magic", "hitpoints", "prayer", "agility")) {
                statMap.setBaseLevel("stat.$stat", 50)
                statMap.setCurrentLevel("stat.$stat", 50)
            }
            statMap.setCurrentLevel("stat.hitpoints", 25)
        }
        val events = EventBus()
        val random = EndpointRandom(upperRoll)
        val specialFoods = injector.getInstance(FoodSpecialEffectService::class.java)
        private val areas = AreaChecker(unused(), AreaIndex())
        private val context = ProtectedAccessContextFactory.empty().copy(
            getEventBus = { events }, getRandom = { random }, getAreaChecker = { areas },
        )

        init {
            val scripts = listOf(
                ConsumeFoodScript(areas, PlayerRestrictions(emptySet()), FoodEffectService(), specialFoods),
                ConsumePotionScript(areas, PlayerRestrictions(emptySet()), injector.getInstance(PotionEffectService::class.java), ConsumableActivityAccess()),
            )
            for (script in scripts) with(script) { ScriptContext(events, CheatCommandMap(), EngineQueueCache()).startup() }
        }

        fun at(cycle: Int) { clock.cycle = cycle; player.currentMapClock = cycle; player.processedMapClock = cycle }

        fun consume(symbol: String, count: Int = 1) {
            player.inv[0] = InvObj(symbol, count)
            consumeExisting()
        }

        fun consumeExisting() {
            val item = checkNotNull(player.inv[0])
            val type = checkNotNull(ServerCacheManager.getItem(item.id))
            val option = type.interfaceOptions.indexOfFirst { it?.lowercase() in setOf("eat", "drink") } + 1
            run {
                val dispatched = when (option) {
                    1 -> events.publish(this, HeldObjEvents.Op1(0, item, type, player.inv))
                    2 -> events.publish(this, HeldObjEvents.Op2(0, item, type, player.inv))
                    3 -> events.publish(this, HeldObjEvents.Op3(0, item, type, player.inv))
                    4 -> events.publish(this, HeldObjEvents.Op4(0, item, type, player.inv))
                    else -> error("No consumable option: ${type.internalName}")
                }
                assertTrue(dispatched, type.internalName)
            }
        }

        fun run(action: suspend ProtectedAccess.() -> Unit) {
            val coroutine = player.launch { ProtectedAccess(player, this, context).action() }
            assertTrue(coroutine.isIdle)
        }

        fun energyPackets() = client.messages.filterIsInstance<UpdateRunEnergy>().map { it.runenergy }
    }

    private class EndpointRandom(private val upper: Boolean) : GameRandom {
        val ranges = mutableListOf<Pair<Int, Int>>()
        override fun of(maxExclusive: Int): Int = if (upper) maxExclusive - 1 else 0
        override fun of(minInclusive: Int, maxInclusive: Int): Int {
            ranges += minInclusive to maxInclusive
            return if (upper) maxInclusive else minInclusive
        }
        override fun randomDouble(): Double = error("unused")
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

    companion object {
        @BeforeAll @JvmStatic fun cache() {
            ServerCacheManager.init(241).close()
            with(InvTransactionsScript(PlayerItemStorage(emptySet()))) {
                ScriptContext(EventBus(), CheatCommandMap(), EngineQueueCache()).startup()
            }
        }

        private fun unused(): RegionRegistry =
            (Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null) as Unsafe)
                .allocateInstance(RegionRegistry::class.java) as RegionRegistry
    }
}
