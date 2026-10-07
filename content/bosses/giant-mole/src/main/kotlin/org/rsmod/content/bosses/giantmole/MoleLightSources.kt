package org.rsmod.content.bosses.giantmole

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.game.inv.Inventory

internal object MoleLightSources {
    private val replacements by lazy {
        listOf(
            "obj.lit_candle" to "obj.unlit_candle",
            "obj.lit_black_candle" to "obj.unlit_black_candle",
            "obj.torch_lit" to "obj.torch_unlit",
            "obj.oil_lamp_lit" to "obj.oil_lamp_unlit",
        ).associate { (lit, unlit) -> lit.asRSCM(RSCMType.OBJ) to unlit.asRSCM(RSCMType.OBJ) }
    }

    fun extinguish(inv: Inventory): Boolean = extinguish(inv, replacements)

    fun extinguish(inv: Inventory, replacements: Map<Int, Int>): Boolean {
        var changed = false
        for (slot in inv.indices) {
            val item = inv[slot] ?: continue
            val unlit = replacements[item.id] ?: continue
            inv[slot] = item.copy(id = unlit)
            changed = true
        }
        return changed
    }
}
