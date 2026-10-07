package org.rsmod.content.other.commands

import dev.openrune.ServerCacheManager
import dev.openrune.types.NpcServerType
import dev.openrune.types.ProjAnimType
import dev.openrune.types.aconverted.SpotanimType
import dev.openrune.types.aconverted.SynthType
import net.rsprot.protocol.game.outgoing.sound.SynthSound
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class StaffEffectPrivacyTest {
    @Test fun `all actor sound overloads conceal coordinates while keeping direct owner audio`() {
        val f = Fixture()
        val calls: List<() -> Unit> = listOf(
            { f.world.soundArea(f.staff, 7, delay = 3, loops = 2) },
            { f.world.soundArea(f.staff, "synth.PILLORY_WRONG", delay = 3, loops = 2) },
            { f.world.soundAreaExcept(f.staff, SynthType(7), f.staff) },
            { f.world.soundAreaExcept(f.staff, SynthType(7), listOf(f.staff)) },
            { f.world.soundAreaExcept(f.staff, "synth.PILLORY_WRONG", f.staff) },
        )
        for ((index, call) in calls.withIndex()) {
            f.staff.staffInvisible = false
            f.updates.clear()
            call()
            assertEquals(1, f.count(), "Visible actor sound overload $index must broadcast")
            f.staff.staffInvisible = true
            f.updates.clear()
            f.client.messages.clear()
            call()
            assertEquals(0, f.count(), "Hidden actor sound overload $index must not broadcast")
            assertEquals(if (index < 2) 1 else 0, f.client.messages.filterIsInstance<SynthSound>().size)
        }
        f.world.soundArea(f.staff.coords, 7)
        assertEquals(1, f.count())
    }

    @Test fun `hidden player projectiles preserve timing but emit no source or target data`() {
        val f = Fixture()
        val spot = SpotanimType(42)
        val calls: List<() -> ProjAnim> = listOf(
            { f.world.projAnim(f.staff, f.npc, spot, "projanim.dragonfire") },
            { f.world.projAnimSourced(f.staff, f.npc, spot, "projanim.dragonfire") },
            { f.world.projAnim(f.staff, f.other, spot, "projanim.dragonfire") },
            { f.world.projAnimSourced(f.staff, f.other, spot, "projanim.dragonfire") },
            { f.world.projAnim(f.staff, f.other.coords, spot, "projanim.dragonfire") },
            { f.world.projAnimSourced(f.staff, f.other.coords, spot, "projanim.dragonfire") },
            { f.world.projAnim(f.npc, f.staff, spot, "projanim.dragonfire") },
            { f.world.projAnimSourced(f.npc, f.staff, spot, ProjAnimType(id = 1, delay = 10)) },
            { f.world.projAnim(f.other, f.staff, spot, "projanim.dragonfire") },
            { f.world.projAnimSourced(f.other, f.staff, spot, "projanim.dragonfire") },
        )
        for ((index, call) in calls.withIndex()) {
            f.staff.staffInvisible = false
            f.updates.clear()
            val visible = call()
            assertEquals(1, f.count(), "Visible projectile overload $index must broadcast")
            f.staff.staffInvisible = true
            f.updates.clear()
            val hidden = call()
            assertEquals(visible, hidden, "Suppressing a visual must retain projectile timing and game data")
            assertEquals(0, f.count(), "Hidden projectile overload $index must not broadcast")
        }
    }

    @Test fun `hidden player loc merge does not publish player index`() {
        val f = Fixture()
        val loc = LocInfo(2, f.staff.coords, LocEntity(1, 10, 0))
        f.world.locMerge(loc, f.staff, 0, 30)
        assertEquals(1, f.count())
        f.updates.clear()
        f.staff.staffInvisible = true
        f.world.locMerge(loc, f.staff, 0, 30)
        assertEquals(0, f.count())
    }

    private class Fixture {
        val updates = ZoneUpdateMap()
        val world = WorldRepository(updates)
        val client = RecordingClient()
        val staff = Player().apply { slotId = 1; coords = CoordGrid(3200, 3200); this.client = this@Fixture.client }
        val other = Player().apply { slotId = 2; coords = CoordGrid(3201, 3200) }
        val npc = Npc(NpcServerType(id = 1), CoordGrid(3202, 3200)).apply { slotId = 3 }
        fun count(): Int = updates.updatedZones.values.sumOf { it.size }
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
