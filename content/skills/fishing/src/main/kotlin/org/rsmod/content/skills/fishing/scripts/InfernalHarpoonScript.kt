package org.rsmod.content.skills.fishing.scripts

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.fishingLvl
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.script.onOpHeldU
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class InfernalHarpoonScript : PluginScript() {
    override fun ScriptContext.startup() {
        onOpHeldU(SMOULDERING_STONE, DRAGON_HARPOON) { createHarpoon() }
        onOpHeldU(HAMMER, INFERNAL_EEL) { crushInfernalEel() }
    }

    private fun ProtectedAccess.createHarpoon() {
        if (player.statBase("stat.fishing") < FISHING_REQ) {
            mes("You need a Fishing level of $FISHING_REQ to make an infernal harpoon.")
            return
        }
        if (player.statBase("stat.cooking") < COOKING_REQ) {
            mes("You need a Cooking level of $COOKING_REQ to make an infernal harpoon.")
            return
        }
        val deleteStone = invDel(inv, SMOULDERING_STONE, count = 1)
        if (!deleteStone.success) {
            return
        }
        val deleteHarpoon = invDel(inv, DRAGON_HARPOON, count = 1)
        if (!deleteHarpoon.success) {
            invAdd(inv, SMOULDERING_STONE, count = 1)
            return
        }
        val add = invAdd(inv, INFERNAL_HARPOON, count = 1, vars = 5_000)
        if (!add.success) {
            invAdd(inv, SMOULDERING_STONE, count = 1)
            invAdd(inv, DRAGON_HARPOON, count = 1)
            return
        }
        statAdvance("stat.cooking", COOKING_XP)
        statAdvance("stat.fishing", FISHING_XP)
        mes("You combine the smouldering stone with your dragon harpoon.")
    }

    private fun ProtectedAccess.crushInfernalEel() {
        if (player.fishingLvl < CRUSH_LEVEL) {
            mes("You need a Fishing level of $CRUSH_LEVEL to crush infernal eels.")
            return
        }
        val delete = invDel(inv, INFERNAL_EEL, count = 1)
        if (!delete.success) {
            return
        }
        when (random.of(1, 35)) {
            in 1..29 -> invAdd(inv, TZHAAR_TOKEN, count = random.of(14, 20))
            in 30..31 -> invAdd(inv, ONYX_BOLT_TIPS, count = 1)
            else -> invAdd(inv, LAVA_SHARD, count = random.of(1, 5))
        }
        spam("You smash the infernal eel apart.")
    }

    private companion object {
        private const val SMOULDERING_STONE = "obj.smouldering_stone"
        private const val DRAGON_HARPOON = "obj.dragon_harpoon"
        private const val INFERNAL_HARPOON = "obj.infernal_harpoon"
        private const val FISHING_REQ = 75
        private const val COOKING_REQ = 85
        private const val COOKING_XP = 350.0
        private const val FISHING_XP = 200.0

        private const val HAMMER = "obj.hammer"
        private const val INFERNAL_EEL = "obj.infernal_eel"
        private const val TZHAAR_TOKEN = "obj.tzhaar_token"
        private const val ONYX_BOLT_TIPS = "obj.xbows_bolt_tips_onyx"
        private const val LAVA_SHARD = "obj.lava_shard"
        private const val CRUSH_LEVEL = 80
    }
}
