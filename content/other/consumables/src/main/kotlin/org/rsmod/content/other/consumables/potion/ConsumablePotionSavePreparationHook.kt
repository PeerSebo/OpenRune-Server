package org.rsmod.content.other.consumables.potion

import jakarta.inject.Inject
import org.rsmod.api.account.saver.PlayerSavePreparationHook
import org.rsmod.content.other.consumables.heart.HeartEffectService
import org.rsmod.game.entity.Player

class ConsumablePotionSavePreparationHook
@Inject
constructor(
    private val stamina: StaminaEffectService,
    private val antifire: AntifireEffectService,
    private val prayerRegeneration: PrayerRegenerationEffectService,
    private val menaphiteRemedy: MenaphiteRemedyEffectService,
    private val divine: DivineEffectService,
    private val heart: HeartEffectService,
) : PlayerSavePreparationHook {
    override fun prepare(player: Player) {
        if (player.loggingOut) return
        stamina.snapshot(player)
        antifire.snapshot(player)
        prayerRegeneration.snapshot(player)
        menaphiteRemedy.snapshot(player)
        divine.snapshot(player)
        heart.snapshot(player)
    }
}
