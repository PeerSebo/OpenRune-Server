package org.rsmod.content.skills.slayer

import dev.openrune.definition.type.widget.IfEvent
import org.rsmod.api.enums.SlayerItemRewardsEnums.slayer_item_rewards_ids
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.shops.Shops
import org.rsmod.content.skills.slayer.core.SlayerTaskManager
import org.rsmod.content.skills.slayer.rewards.SlayerRewardsPoints
import org.rsmod.content.skills.slayer.rewards.SlayerRewardsScript.Companion.BUY_COMPONENT
import org.rsmod.content.skills.slayer.rewards.SlayerRewardsScript.Companion.CONFIRM_COMPONENT
import org.rsmod.content.skills.slayer.rewards.SlayerRewardsScript.Companion.UNLOCK_COMPONENT
import org.rsmod.game.entity.Npc

object SlayerInterfaces {

    // Equipment opens the coin shop and requires npc/shops; other calls open points rewards.
    fun openInterface(
        access: ProtectedAccess,
        npcId: String,
        equipment: Boolean = false,
        npc: Npc? = null,
        shops: Shops? = null,
    ) {
        val master = SlayerTaskManager.findMasterByNpc(npcId) ?: return

        if (equipment) {
            if (npc != null && shops != null) {
                shops.open(access.player, npc, "Slayer Equipment", "inv.slayershop")
            }
            return
        }

        access.ifOpenMainModal("interface.slayer_rewards")
        access.runClientScript(SLAYER_REWARDS_INIT_CS)
        access.runClientScript(SLAYER_REWARDS_TASKS_INIT_CS)

        // Changing the shop focus must not replace the player's assigned task master.
        VarPlayerIntMapSetter.set(access.player, "varbit.slayer_master_in_focus", master.masterId)

        access.player.ifSetEvents(CONFIRM_COMPONENT, 0..200, IfEvent.Op1)
        access.player.ifSetEvents(UNLOCK_COMPONENT, 0..200, IfEvent.Op1)

        access.player.ifSetEvents(
            BUY_COMPONENT,
            0..slayer_item_rewards_ids.values.size,
            IfEvent.Op2,
            IfEvent.Op3,
            IfEvent.Op4,
            IfEvent.Op5,
            IfEvent.Op10,
        )

        SlayerRewardsPoints.syncPoints(access)
        SlayerRewardsPoints.update(access.player)
    }

    private const val SLAYER_REWARDS_INIT_CS = 405
    private const val SLAYER_REWARDS_TASKS_INIT_CS = 328
}
