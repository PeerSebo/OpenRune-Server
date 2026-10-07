package org.rsmod.content.generic.shops

import jakarta.inject.Inject
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.shops.Shops
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class RommikDialogue @Inject constructor(private val shops: Shops) : PluginScript() {
    override fun ScriptContext.startup() {
        val merchant = "npc.rommik"
        val binding = ShopkeeperCatalog.shops.single { merchant in it.merchants }
        onOpNpc1(merchant) {
            if (!canTrade(it.npc, binding)) return@onOpNpc1
            startDialogue(it.npc) {
                chatNpc(happy, "Would you like to buy some crafting equipment?")
                if (choice2("Let's see what you've got, then.", true, "No thanks.", false)) {
                    access.openShopBinding(shops, it.npc, binding)
                } else {
                    chatPlayer(neutral, "No thanks.")
                    chatNpc(neutral, "Okay. Fare well on your travels.")
                }
            }
        }
    }
}
