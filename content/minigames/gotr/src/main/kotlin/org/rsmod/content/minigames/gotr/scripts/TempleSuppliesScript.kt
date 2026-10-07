package org.rsmod.content.minigames.gotr.scripts

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.shops.Shops
import org.rsmod.api.shops.operation.ShopOperationMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class TempleSuppliesScript
@Inject
constructor(
    private val shops: Shops,
    private val shopOps: ShopOperationMap,
) : PluginScript() {
    override fun ScriptContext.startup() {
        shopOps.costOf(CURRENCY, ::pearlCost)
        onOpNpc1(TRADER) { talk(it.npc) }
        onOpNpc3(TRADER) { openShop() }
    }

    private suspend fun ProtectedAccess.talk(npc: Npc) = startDialogue(npc) { greeting(npc) }

    private suspend fun Dialogue.greeting(npc: Npc) {
        chatNpc(happy, "Welcome to the Temple of the Eye. Can I interest you in some supplies?")
        when (choice2("Yes, let's trade.", 1, "Not right now.", 2)) {
            1 -> {
                chatPlayer(happy, "Yes, let's trade.")
                player.openTempleSupplies()
            }
            2 -> chatPlayer(neutral, "Not right now.")
        }
    }

    private fun ProtectedAccess.openShop() {
        player.openTempleSupplies()
    }

    private fun Player.openTempleSupplies() {
        shops.open(
            player = this,
            title = "Temple Supplies",
            shopInv = SHOP_INV,
            buyPercentage = 0.0,
            sellPercentage = 100.0,
            changePercentage = 0.0,
            currency = CURRENCY,
            infiniteStock = true,
            allowSelling = false,
        )
    }

    private companion object {
        const val TRADER = "npc.gotr_reward_trader"
        const val CURRENCY = "currency.abyssal_pearls"
        const val SHOP_INV = "inv.gotr_reward_shop_all"

        val FIXED_PRICES =
            mapOf(
                "obj.hat_of_the_eye" to 400,
                "obj.robe_top_of_the_eye" to 350,
                "obj.robe_bottom_of_the_eye" to 350,
                "obj.boots_of_the_eye" to 250,
                "obj.ring_of_elements" to 400,
                "obj.guardians_eye" to 3000,
                "obj.abyssal_lantern" to 1500,
                "obj.abyssal_needle" to 750,
                "obj.air_talisman" to 10,
                "obj.water_talisman" to 10,
                "obj.earth_talisman" to 10,
                "obj.fire_talisman" to 10,
                "obj.mind_talisman" to 10,
                "obj.body_talisman" to 10,
                "obj.chaos_talisman" to 30,
                "obj.cosmic_talisman" to 30,
                "obj.nature_talisman" to 50,
                "obj.law_talisman" to 50,
                "obj.death_talisman" to 80,
                "obj.blood_talisman" to 100,
            )

        fun pearlCost(type: ItemServerType): Int {
            val name = RSCM.getReverseMapping(RSCMType.OBJ, type.id)
            return FIXED_PRICES[name] ?: type.cost.coerceAtLeast(1)
        }
    }
}
