package org.rsmod.content.skills.magic.spellbookaltars

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.spells.autocast.MagicSpellbookManager
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SpellbookAltarScript @Inject constructor(private val spellbooks: MagicSpellbookManager) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc2("loc.astral_altar") { switchTo(Spellbook.Lunars, "Lunar") }
        onOpLoc1("loc.arceuus_altar") { switchTo(Spellbook.Arceuus, "Arceuus") }
    }

    private fun ProtectedAccess.switchTo(spellbook: Spellbook, name: String) {
        val result = spellbooks.setSpellbook(player, spellbook)
        if (result is MagicSpellbookManager.ChangeResult.Unchanged) {
            mes("You are already attuned to the $name spellbook.")
            return
        }
        mes("You feel the power of the $name spellbook flow through you.")
    }
}
