package org.rsmod.content.minigames.giantsfoundry

import dev.openrune.definition.type.widget.IfEvent
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.ui.ifCloseModal
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenMainModal
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player

@Singleton
public class FoundryInterfaces @Inject constructor(private val eventBus: EventBus) {
    public fun openMould(player: Player) {
        player.ifOpenMainModal(MOULD, eventBus = eventBus)
        player.ifSetEvents(MOULD_TABS, 0..63, IfEvent.Op1)
        player.ifSetEvents(MOULD_LIST, 0..544, IfEvent.Op1)
    }

    public fun refreshMould(player: Player) {
        player.runClientScript("clientscript.[clientscript,giants_foundry_mould_setup_content]")
    }

    public fun closeMould(player: Player) {
        player.ifCloseModal(MOULD, eventBus)
    }

    public fun openRewards(player: Player) {
        player.ifOpenMainModal(REWARDS, eventBus = eventBus)
        player.runClientScript("clientscript.[clientscript,reward_shop_init]")
        player.ifSetEvents(REWARDS_LIST, 0..312, IfEvent.Op1, IfEvent.Op2, IfEvent.Op3, IfEvent.Op4)
        player.ifSetEvents(REWARDS_FILTER, 0..12, IfEvent.Op1)
    }

    public fun refreshRewards(player: Player) {
        player.runClientScript("clientscript.[clientscript,giants_foundry_reward_shop_setup]", 0)
    }

    public fun selectReward(player: Player, index: Int) {
        player.runClientScript("clientscript.[clientscript,script6088]", index)
    }

    public fun showHud(player: Player) {
        if (!player.ui.containsOverlay(HUD)) {
            player.ifOpenOverlay(HUD, HUD_TARGET, eventBus)
        }
    }

    public fun hideHud(player: Player) {
        player.ifCloseOverlay(HUD, eventBus)
    }

    public fun sweetSpot(player: Player, state: SweetSpot) {
        player.runClientScript("clientscript.[clientscript,script6122]", state.ordinal)
    }

    public enum class SweetSpot { Hidden, Available, Claimed }

    public companion object {
        public const val MOULD: String = "interface.giants_foundry_mould"
        public const val MOULD_LIST: String = "component.giants_foundry_mould:content"
        public const val MOULD_TABS: String = "component.giants_foundry_mould:side_menu"
        public const val MOULD_RESET: String = "component.giants_foundry_mould:reset_button"
        public const val MOULD_SET: String = "component.giants_foundry_mould:set_button"
        public const val REWARDS: String = "interface.giants_foundry_reward_shop"
        public const val REWARDS_LIST: String = "component.giants_foundry_reward_shop:list"
        public const val REWARDS_FILTER: String = "component.giants_foundry_reward_shop:dropdown_content"
        public const val REWARDS_BUY: String = "component.giants_foundry_reward_shop:buy_button"
        public const val HUD: String = "interface.giants_foundry_hud"
        public const val HUD_TARGET: String = "component.toplevel_osrs_stretch:overlay_hud"

        public fun mouldTab(slot: Int): Int? =
            if (slot in 0..18 && slot % 9 == 0) slot / 9 else null

        public fun mouldIndex(slot: Int, entryCount: Int): Int? = rowIndex(slot, 17, entryCount)

        public fun rewardIndex(slot: Int, entryCount: Int): Int? = rowIndex(slot, 13, entryCount)

        public fun rewardFilter(slot: Int): Int? =
            if (slot in 1..11 && slot % 2 == 1) slot / 2 else null

        private fun rowIndex(slot: Int, stride: Int, entryCount: Int): Int? =
            if (slot >= 0 && slot % stride == 0) {
                (slot / stride + 1).takeIf { it in 1..entryCount }
            } else null
    }
}
