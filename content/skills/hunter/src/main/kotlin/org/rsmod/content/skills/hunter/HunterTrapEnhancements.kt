package org.rsmod.content.skills.hunter

import org.rsmod.api.invtx.invDel
import org.rsmod.content.skills.hunter.configs.HunterPrey
import org.rsmod.content.skills.hunter.configs.HunterPreyTable
import org.rsmod.content.skills.hunter.configs.NetTrapColour
import org.rsmod.game.entity.Player

internal object HunterTrapEnhancements {
    val torches = setOf("obj.torch_lit", "obj.wint_torch", "obj.wint_torch_offhand")
    fun canSmoke(trap: ActiveTrap): Boolean = trap.netColour != NetTrapColour.MOUNTAIN
    fun baitFor(prey: HunterPrey): String? = when (prey.locSuffix) {
        "chinchompa" -> "obj.bowl_spicytomato"
        "chinchompa_big", "chinchompa_black" -> "obj.bowl_spicymeat"
        "claw", "sabre" -> "obj.raw_beef"
        "barbed" -> "obj.hunting_raw_fish_special"
        "spike" -> "obj.barley"
        "fennec" -> "obj.hunting_jerboa_tail"
        "green" -> "obj.salamander_tar_green"
        "orange" -> "obj.salamander_tar_orange"
        "red" -> "obj.salamander_tar_red"
        "black" -> "obj.salamander_tar_black"
        else -> null
    }

    fun apply(player: Player, trap: ActiveTrap, item: String): Boolean {
        if (trap.state != TrapState.SET || trap.owner != player.uid) return false
        if (item in torches) {
            if (!canSmoke(trap) || trap.smoked || player.inv.count(item) == 0) return false
            trap.smoked = true
            return true
        }
        if (trap.bait != null) return false
        val accepted = HunterPreyTable.ALL.any { it.family == trap.family &&
            (trap.netColour == null || it.npc == trap.netColour.prey) && baitFor(it) == item }
        if (!accepted || !player.invDel(player.inv, item).success) return false
        trap.bait = item
        return true
    }
}
