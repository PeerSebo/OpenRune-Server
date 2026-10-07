package org.rsmod.api.game.process.player

import dev.openrune.ServerCacheManager
import net.rsprot.protocol.game.outgoing.misc.player.UpdateRunEnergy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.player.blockRunEnergyRestore
import org.rsmod.api.player.clearRunEnergyRestoreBlock
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.setActiveMoveSpeed
import org.rsmod.api.player.vars.varMoveSpeed
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory
import org.rsmod.game.movement.MoveSpeed

@ResourceLock("ServerCacheManager")
class PlayerRunUpdateProcessorTest {
    @Test
    fun `standing and walking recover raw units at the established agility rates`() {
        for ((agility, restored) in listOf(1 to 15, 10 to 16, 99 to 24)) {
            for (steps in listOf(0, 1)) {
                val f = Fixture(energy = 5_000, agility = agility, steps = steps)
                f.process()
                assertEquals(5_000 + restored, f.player.runEnergy)
                assertEquals(listOf(UpdateRunEnergy(5_000 + restored)), f.energyUpdates())
            }
        }
    }

    @Test
    fun `passive recovery starts from zero and caps at ten thousand without redundant updates`() {
        val f = Fixture(energy = 0)
        f.process()
        assertEquals(15, f.player.runEnergy)
        f.player.runEnergy = 9_999
        f.process()
        assertEquals(10_000, f.player.runEnergy)
        assertEquals(listOf(UpdateRunEnergy(15), UpdateRunEnergy(10_000)), f.energyUpdates())
        f.process()
        assertEquals(10_000, f.player.runEnergy)
        assertEquals(2, f.energyUpdates().size)
    }

    @Test
    fun `running preserves fractional weight and agility drain in raw units`() {
        val cases = listOf(
            Triple(1, -1_000, 59),
            Triple(1, 0, 59),
            Triple(1, 500, 60),
            Triple(1, 32_000, 93),
            Triple(1, 64_000, 126),
            Triple(1, 128_000, 126),
            Triple(99, 0, 40),
            Triple(99, 64_000, 85),
        )
        for ((agility, grams, loss) in cases) {
            val f = Fixture(energy = 10_000, agility = agility, steps = 2, grams = grams)
            f.process()
            assertEquals(10_000 - loss, f.player.runEnergy, "agility=$agility grams=$grams")
            assertEquals(listOf(UpdateRunEnergy(10_000 - loss)), f.energyUpdates())
        }
    }

    @Test
    fun `stamina reductions and improved stamina precedence remain unchanged`() {
        for ((stamina, improved, loss) in listOf(
            Triple(0, 0, 126),
            Triple(1, 0, 37),
            Triple(0, 1, 107),
            Triple(1, 1, 107),
        )) {
            val f = Fixture(energy = 5_000, steps = 2, grams = 64_000)
            VarPlayerIntMapSetter.set(f.player, "varbit.stamina_active", stamina)
            VarPlayerIntMapSetter.set(f.player, "varbit.improved_stamina_passive", improved)
            f.process()
            assertEquals(5_000 - loss, f.player.runEnergy)
            assertEquals(listOf(UpdateRunEnergy(5_000 - loss)), f.energyUpdates())
        }
    }

    @Test
    fun `restoration suppression blocks standing and walking until its deadline`() {
        for (steps in listOf(0, 1)) {
            val f = Fixture(energy = 5_000, steps = steps)
            f.player.currentMapClock = 100
            f.player.blockRunEnergyRestore(2)
            f.process()
            f.player.currentMapClock = 101
            f.process()
            assertEquals(5_000, f.player.runEnergy)
            assertTrue(f.energyUpdates().isEmpty())
            f.player.currentMapClock = 102
            f.process()
            assertEquals(5_015, f.player.runEnergy)
            assertEquals(listOf(UpdateRunEnergy(5_015)), f.energyUpdates())
        }
    }

    @Test
    fun `clearing restoration suppression resumes recovery without skipping running drain`() {
        val f = Fixture(energy = 5_000, steps = 2)
        f.player.currentMapClock = 100
        f.player.blockRunEnergyRestore(100)
        f.process()
        assertEquals(4_941, f.player.runEnergy)
        f.player.pendingStepCount = 1
        f.process()
        assertEquals(4_941, f.player.runEnergy)
        f.player.clearRunEnergyRestoreBlock()
        f.process()
        assertEquals(4_956, f.player.runEnergy)
        assertEquals(listOf(UpdateRunEnergy(4_941), UpdateRunEnergy(4_956)), f.energyUpdates())
    }

    @Test
    fun `running out of energy clamps at zero and switches all movement settings to walk`() {
        val f = Fixture(energy = 10, steps = 2)
        f.player.setActiveMoveSpeed(MoveSpeed.Run)
        f.process()
        assertEquals(0, f.player.runEnergy)
        assertEquals(MoveSpeed.Walk, f.player.varMoveSpeed)
        assertEquals(MoveSpeed.Walk, f.player.moveSpeed)
        assertEquals(MoveSpeed.Walk, f.player.tempMoveSpeed)
        assertEquals(listOf(UpdateRunEnergy(0)), f.energyUpdates())
        f.process()
        assertEquals(0, f.player.runEnergy)
        assertEquals(1, f.energyUpdates().size)
    }

    private class Fixture(energy: Int, agility: Int = 1, steps: Int = 0, grams: Int = 0) {
        private val client = RecordingClient()
        val player = Player(client).apply {
            worn = Inventory.create("inv.worn")
            runEnergy = energy
            runWeight = grams
            pendingStepCount = steps
            statMap.setCurrentLevel("stat.agility", agility.toByte())
        }
        private val processor = PlayerRunUpdateProcessor()
        fun process() = processor.process(player)
        fun energyUpdates(): List<UpdateRunEnergy> = client.messages.filterIsInstance<UpdateRunEnergy>()
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
        @JvmStatic @BeforeAll fun cache() { ServerCacheManager.init(241).close() }
    }
}
