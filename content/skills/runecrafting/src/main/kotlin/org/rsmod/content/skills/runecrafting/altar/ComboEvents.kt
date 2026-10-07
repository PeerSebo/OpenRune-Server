package org.rsmod.content.skills.runecrafting.altar

import jakarta.inject.Inject
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.table.runecrafting.RunecraftingAltarsRow
import org.rsmod.content.skills.runecrafting.action.RunecraftAction.craftCombination
import org.rsmod.content.skills.runecrafting.magic.MagicImbue.isActive
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class ComboEvents @Inject constructor(private val xpMods: XpModifiers) : PluginScript() {
    override fun ScriptContext.startup() {
        RunecraftingAltarsRow.all().forEach { altar ->
            if (altar.combo.isEmpty()) {
                return@forEach
            }

            val altarLoc = altar.altarObject.internalName
            registerTalismanRoutes(altarLoc, altar)
            registerMagicImbueRoutes(altarLoc, altar)
        }
    }

    private fun ScriptContext.registerTalismanRoutes(altarLoc: String, altar: RunecraftingAltarsRow) {
        val registeredTalismans = hashSetOf<String>()

        altar.combo.forEach { combo ->
            val talisman = combo.talisman?.internalName ?: return@forEach
            if (!registeredTalismans.add(talisman)) {
                return@forEach
            }

            onOpLocU(altarLoc, talisman) {
                val matchingCombos = altar.combo.filter { it.talisman?.internalName == talisman }
                val selectedCombo =
                    matchingCombos.firstOrNull { candidate ->
                        val input = candidate.input?.internalName
                        input != null &&
                            inv.contains(input) &&
                            (inv.contains("obj.blankrune_high") ||
                                inv.contains("obj.gotr_guardian_essence"))
                    } ?: matchingCombos.firstOrNull() ?: return@onOpLocU

                craftCombination(selectedCombo, xpMods, altar.rune.output.internalName)
            }
        }
    }

    private fun ScriptContext.registerMagicImbueRoutes(altarLoc: String, altar: RunecraftingAltarsRow) {
        val registeredInputs = hashSetOf<String>()

        altar.combo.forEach { combo ->
            val input = combo.input?.internalName ?: return@forEach
            if (!registeredInputs.add(input)) {
                return@forEach
            }

            onOpLocU(altarLoc, input) {
                if (!player.isActive()) {
                    return@onOpLocU
                }

                val selectedCombo =
                    altar.combo.firstOrNull { it.input?.internalName == input } ?: return@onOpLocU

                craftCombination(selectedCombo, xpMods, altar.rune.output.internalName)
            }
        }
    }
}
