package org.rsmod.api.account.saver.journal

import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.api.attr.AttributeKey
import org.rsmod.game.entity.Player

public data class SaveRevision(val generation: String, val sequence: Long)

/** A runtime write capability, bound to the exact database snapshot used for login. */
public data class AccountSaveLease(val revision: SaveRevision, val claimed: Boolean = false) :
    CharacterDataStage.Segment {
    public companion object : CharacterDataStage.Applier<AccountSaveLease> {
        private val key = AttributeKey<AccountSaveLease>()

        public fun get(player: Player): AccountSaveLease =
            checkNotNull(player.attr[key]) { "Character has no save lease: ${player.characterId}" }

        override fun apply(player: Player, data: AccountSaveLease) {
            player.attr[key] = data
        }
    }
}
