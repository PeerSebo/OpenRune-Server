package org.rsmod.api.account.character.appearance

import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.game.entity.Player

/** Apply before world registration so the first appearance block contains the restored values. */
public class CharacterAppearanceApplier : CharacterDataStage.Applier<CharacterAppearanceData> {
    override fun apply(player: Player, data: CharacterAppearanceData) {
        val stored = data.appearance ?: return
        CharacterAppearanceCodec.applyTo(player.appearance, stored)
    }
}
