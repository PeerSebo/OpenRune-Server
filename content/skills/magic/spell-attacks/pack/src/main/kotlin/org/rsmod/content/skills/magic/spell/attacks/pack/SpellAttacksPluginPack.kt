package org.rsmod.content.skills.magic.spell.attacks.pack

import dev.openrune.pack.PluginPack

class SpellAttacksPluginPack : PluginPack() {
    override fun dbTables() = listOf(ancientSpellTable())
}
