package org.rsmod.content.skills.woodcutting

import org.rsmod.api.stats.xpmod.StatXpMod
import org.rsmod.content.skills.woodcutting.WoodcuttingEquipment.hasFullLumberjackOutfit
import org.rsmod.content.skills.woodcutting.WoodcuttingEquipment.hasLumberjackBoots
import org.rsmod.content.skills.woodcutting.WoodcuttingEquipment.hasLumberjackHat
import org.rsmod.content.skills.woodcutting.WoodcuttingEquipment.hasLumberjackLegs
import org.rsmod.content.skills.woodcutting.WoodcuttingEquipment.hasLumberjackTop
import org.rsmod.game.entity.Player

// Do not also apply generic xpmod params: whole-percent modifiers would double-count the outfit.
class WoodcuttingLumberjackXpModifiers : StatXpMod("stat.woodcutting") {
    override fun Player.modifier(): Double {
        var bonus = 0.0
        if (hasLumberjackHat()) bonus += 0.004
        if (hasLumberjackTop()) bonus += 0.008
        if (hasLumberjackLegs()) bonus += 0.006
        if (hasLumberjackBoots()) bonus += 0.002
        if (hasFullLumberjackOutfit()) bonus += 0.005
        return bonus
    }
}
