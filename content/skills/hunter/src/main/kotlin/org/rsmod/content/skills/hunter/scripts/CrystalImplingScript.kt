package org.rsmod.content.skills.hunter.scripts

import jakarta.inject.Inject
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onEvent
import org.rsmod.content.skills.hunter.configs.NetPreyTable
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class CrystalImplingScript @Inject constructor(private val npcRepo: NpcRepository, private val random: GameRandom) : PluginScript() {
    private val schedule = CrystalImplingSchedule()
    private var active: Npc? = null
    private var lastMinute = Long.MIN_VALUE

    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.LateCycle> {
            val minute = System.currentTimeMillis() / 60_000L
            if (minute != lastMinute) {
                lastMinute = minute
                refresh(minute)
            }
        }
    }

    private fun refresh(minute: Long) {
        val change = schedule.update(minute)
        if (change.reset) {
            active?.takeIf { it.isSlotAssigned }?.let { npcRepo.del(it, Int.MAX_VALUE) }
            active = null
        }
        if (change.spawn) {
            val variants = NetPreyTable.ALL.single { it.filledJar == "obj.ii_captured_impling_12" }.npcs
            val npc = Npc(variants[random.of(variants.size)], SPAWNS[random.of(SPAWNS.size)])
            npcRepo.add(npc, Int.MAX_VALUE)
            active = npc
        }
    }

    companion object {
        val SPAWNS = listOf(3290 to 6117, 3307 to 6041, 3210 to 6110, 3213 to 6049,
            3237 to 6102, 3308 to 6117, 3240 to 6046, 3214 to 6113, 3290 to 6049,
            3234 to 6072, 3240 to 6120, 3234 to 6129).map { (x, z) -> CoordGrid(x, z) }
    }
}

// Consume the spawn window even if caught; clock rollback must not respawn it.
internal class CrystalImplingSchedule {
    data class Change(val reset: Boolean, val spawn: Boolean)
    private var period = Long.MIN_VALUE
    private var spawned = false

    fun update(epochMinute: Long): Change {
        val next = Math.floorDiv(epochMinute, 30L)
        if (next < period) return Change(false, false)
        val reset = next != period
        if (reset) { period = next; spawned = false }
        val spawn = !spawned && Math.floorMod(epochMinute, 30L) >= 3L
        if (spawn) spawned = true
        return Change(reset, spawn)
    }
}
