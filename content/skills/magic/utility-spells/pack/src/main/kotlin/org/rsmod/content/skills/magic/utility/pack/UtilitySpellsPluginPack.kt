package org.rsmod.content.skills.magic.utility.pack

import dev.openrune.pack.PluginPack

class UtilitySpellsPluginPack : PluginPack() {
    override fun dbTables() = listOf(
        jewelleryEnchantTiers(), jewelleryEnchantProducts(), utilitySpellRules(), utilitySpellConversions(),
        utilityOfferingBonuses(),
    )
}
