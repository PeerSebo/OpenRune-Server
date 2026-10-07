package org.rsmod.content.other.consumables

import org.rsmod.api.account.saver.PlayerSavePreparationHook
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.player.stat.PlayerStatResetHook
import org.rsmod.content.other.consumables.potion.AntifireDeathCleanupHook
import org.rsmod.content.other.consumables.potion.ConsumablePotionSavePreparationHook
import org.rsmod.content.other.consumables.potion.ConsumableStatResetHook
import org.rsmod.content.other.consumables.potion.DivineDeathCleanupHook
import org.rsmod.content.other.consumables.potion.MenaphiteRemedyDeathCleanupHook
import org.rsmod.content.other.consumables.potion.PrayerRegenerationDeathCleanupHook
import org.rsmod.content.other.consumables.potion.StaminaDeathCleanupHook
import org.rsmod.content.other.consumables.potion.cox.CoxPrayerEnhanceDeathCleanupHook
import org.rsmod.content.other.consumables.potion.overload.OverloadDeathCleanupHook
import org.rsmod.plugin.module.PluginModule

class ConsumablesModule : PluginModule() {
    override fun bind() {
        addSetBinding<PlayerDeathCleanupHook>(OverloadDeathCleanupHook::class.java)
        addSetBinding<PlayerStatResetHook>(ConsumableStatResetHook::class.java)
        addSetBinding<PlayerDeathCleanupHook>(ConsumableDeathCleanupHook::class.java)
        addSetBinding<PlayerSavePreparationHook>(ConsumablePotionSavePreparationHook::class.java)
        addSetBinding<PlayerDeathCleanupHook>(StaminaDeathCleanupHook::class.java)
        addSetBinding<PlayerDeathCleanupHook>(AntifireDeathCleanupHook::class.java)
        addSetBinding<PlayerDeathCleanupHook>(PrayerRegenerationDeathCleanupHook::class.java)
        addSetBinding<PlayerDeathCleanupHook>(MenaphiteRemedyDeathCleanupHook::class.java)
        addSetBinding<PlayerDeathCleanupHook>(DivineDeathCleanupHook::class.java)
        addSetBinding<PlayerDeathCleanupHook>(CoxPrayerEnhanceDeathCleanupHook::class.java)
    }
}
