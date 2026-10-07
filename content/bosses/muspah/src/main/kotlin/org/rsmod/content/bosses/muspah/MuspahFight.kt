package org.rsmod.content.bosses.muspah

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.region.Region
import org.rsmod.map.CoordGrid

internal class MuspahFight(val npc: Npc, val region: Region? = null) {
    val activeSpikes: MutableList<LocInfo> = mutableListOf()
    val transientSpikes: MutableSet<LocInfo> = mutableSetOf()
    val clouds: MutableSet<Npc> = mutableSetOf()
    var damageSinceSwitch by state("damage_since_switch")
    var hitsSinceSwitch by state("hits_since_switch")
    var specialCooldownUntilCycle by state("special_cooldown")
    var preSoulsplitHp by state("pre_soulsplit_hp")
    var preSoulsplitMaxHp by state("pre_soulsplit_max_hp")
    var shieldHp by state("shield_hp")
    var finalAttackCount by state("final_attack_count")
    var lastCountedAttackTick by state("last_counted_attack_tick", -1)
    private var lastCoordsPacked by state("last_coords", CoordGrid.NULL.packed)
    private var finalPhase by state("final_phase")
    private var shieldBroken by state("shield_broken")

    var lastCoords: CoordGrid?
        get() = lastCoordsPacked.takeUnless { it == CoordGrid.NULL.packed }?.let(::CoordGrid)
        set(value) { lastCoordsPacked = value?.packed ?: CoordGrid.NULL.packed }

    var finalPhaseTriggered: Boolean
        get() = finalPhase != 0
        set(value) { finalPhase = if (value) 1 else 0 }

    var soulsplitBroken: Boolean
        get() = shieldBroken != 0
        set(value) { shieldBroken = if (value) 1 else 0 }

    private fun state(name: String, initial: Int = 0): ReadWriteProperty<MuspahFight, Int> {
        val symbol = "varn.muspah_$name"
        npc.vars[symbol] = initial
        return object : ReadWriteProperty<MuspahFight, Int> {
            override fun getValue(thisRef: MuspahFight, property: KProperty<*>): Int =
                npc.vars[symbol]

            override fun setValue(thisRef: MuspahFight, property: KProperty<*>, value: Int) {
                npc.vars[symbol] = value
            }
        }
    }
}
