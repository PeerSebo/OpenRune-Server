package org.rsmod.api.farming.persistence

import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.api.farming.FarmingAttributes
import org.rsmod.game.entity.Player

public class FarmingApplier : CharacterDataStage.Applier<FarmingCharacterData> {
    override fun apply(player: Player, data: FarmingCharacterData) {
        data.patches?.let { player.attr[FarmingAttributes.PATCHES] = it }
        data.bins?.let { player.attr[FarmingAttributes.BINS] = it }
    }
}
