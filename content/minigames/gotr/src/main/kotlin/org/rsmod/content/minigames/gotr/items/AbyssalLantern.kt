package org.rsmod.content.minigames.gotr.items

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.firemakingLvl
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpHeld5
import org.rsmod.api.script.onOpHeldU
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// TODO: wire remaining lantern bonuses and recolours; only redwood degradation protection is active.
class AbyssalLantern : PluginScript() {
    override fun ScriptContext.startup() {
        for ((log, variant) in FUEL_TO_VARIANT) {
            onOpHeldU(log, UNLIT) { light(log, variant) }
        }
        for (variant in FUEL_TO_VARIANT.values) {
            registerExtinguish(variant)
            registerInspect(variant)
        }
    }

    private fun ProtectedAccess.light(log: String, variant: LanternVariant) {
        if (player.firemakingLvl < variant.levelReq) {
            mes("You need a Firemaking level of ${variant.levelReq} to light this lantern with ${variant.fuelName}.")
            return
        }
        if (invReplace(inv, UNLIT, 1, variant.obj).failure) {
            return
        }
        if (invDel(inv, log, 1).failure) {
            invReplace(inv, variant.obj, 1, UNLIT)
            return
        }
        anim(LIGHT_ANIM)
        // TODO: verify lantern-lighting synth; the sequence has no baked audio.
        mes("You light the lantern with ${variant.fuelName}.")
    }

    // Extinguish occupies different inventory slots across variants; resolve it from the cache.
    private fun ScriptContext.registerExtinguish(variant: LanternVariant) {
        val type = ServerCacheManager.getItem(variant.obj.asRSCM(RSCMType.OBJ)) ?: return
        when (type.interfaceOptions.indexOf("Extinguish") + 1) {
            1 -> onOpHeld1(variant.obj) { extinguish(variant) }
            2 -> onOpHeld2(variant.obj) { extinguish(variant) }
            3 -> onOpHeld3(variant.obj) { extinguish(variant) }
            4 -> onOpHeld4(variant.obj) { extinguish(variant) }
            5 -> onOpHeld5(variant.obj) { extinguish(variant) }
            else -> Unit
        }
    }

    private fun ScriptContext.registerInspect(variant: LanternVariant) {
        val type = ServerCacheManager.getItem(variant.obj.asRSCM(RSCMType.OBJ)) ?: return
        when (type.interfaceOptions.indexOf("Check") + 1) {
            1 -> onOpHeld1(variant.obj) { inspect(variant) }
            2 -> onOpHeld2(variant.obj) { inspect(variant) }
            3 -> onOpHeld3(variant.obj) { inspect(variant) }
            4 -> onOpHeld4(variant.obj) { inspect(variant) }
            5 -> onOpHeld5(variant.obj) { inspect(variant) }
            else -> Unit
        }
    }

    private fun ProtectedAccess.extinguish(variant: LanternVariant) {
        if (invReplace(inv, variant.obj, 1, UNLIT).success) {
            mes("You extinguish the abyssal lantern.")
        }
    }

    private fun ProtectedAccess.inspect(variant: LanternVariant) {
        mes(LANTERN_EFFECTS[variant.obj] ?: "This lantern is lit but has no special effect.")
    }

    private companion object {
        const val UNLIT = "obj.abyssal_lantern"
        const val LIGHT_ANIM = "seq.gotr_light_lantern"

        data class LanternVariant(val obj: String, val levelReq: Int, val fuelName: String)

        val FUEL_TO_VARIANT =
            mapOf(
                "obj.logs" to LanternVariant("obj.abyssal_lantern_normal", 1, "regular logs"),
                "obj.oak_logs" to LanternVariant("obj.abyssal_lantern_oak", 15, "oak logs"),
                "obj.willow_logs" to LanternVariant("obj.abyssal_lantern_willow", 30, "willow logs"),
                "obj.maple_logs" to LanternVariant("obj.abyssal_lantern_maple", 45, "maple logs"),
                "obj.yew_logs" to LanternVariant("obj.abyssal_lantern_yew", 60, "yew logs"),
                "obj.blisterwood_logs" to
                    LanternVariant("obj.abyssal_lantern_blisterwood", 62, "blisterwood logs"),
                "obj.magic_logs" to LanternVariant("obj.abyssal_lantern_magic", 75, "magic logs"),
                "obj.redwood_logs" to LanternVariant("obj.abyssal_lantern_redwood", 90, "redwood logs"),
            )

        // TODO: apply these lantern effects to gameplay.
        val LANTERN_EFFECTS =
            mapOf(
                "obj.abyssal_lantern_normal" to "Increases portal talisman chance by 10%.",
                "obj.abyssal_lantern_oak" to
                    "5% chance to double points earned, and increases your energy cap by 5%.",
                "obj.abyssal_lantern_willow" to "Increases rune output by 5%.",
                "obj.abyssal_lantern_maple" to "Increases portal talisman chance by 20%.",
                "obj.abyssal_lantern_yew" to
                    "10% chance to double points earned, and increases your energy cap by 10%.",
                "obj.abyssal_lantern_blisterwood" to "Increases blood rune output by 20%.",
                "obj.abyssal_lantern_magic" to "Increases rune output by 10%.",
                "obj.abyssal_lantern_redwood" to
                    "Essence pouches do not degrade while worn, plus the oak and willow effects.",
            )
    }
}
