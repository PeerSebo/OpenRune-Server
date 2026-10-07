package org.rsmod.content.generic.shops

import jakarta.inject.Inject
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.shops.Shops
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class HerquinDialogue @Inject constructor(private val shops: Shops) : PluginScript() {
    override fun ScriptContext.startup() {
        val merchant = "npc.herquin"
        val binding = ShopkeeperCatalog.shops.single { merchant in it.merchants }
        onOpNpc1(merchant) {
            if (!canTrade(it.npc, binding)) return@onOpNpc1
            startDialogue(it.npc) {
                if (choice2("Do you wish to trade?", true, "No thanks, I'd rather leave.", false)) {
                    chatPlayer(quiz, "Do you wish to trade?")
                    chatNpc(happy, "Why yes, this a jewel shop after all.")
                    access.openShopBinding(shops, it.npc, binding)
                } else {
                    chatPlayer(neutral, "No thanks, I'd rather leave.")
                    chatNpc(angry, "Huh! Charming!")
                }
            }
        }
    }
}
