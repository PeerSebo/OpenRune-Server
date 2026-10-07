package org.rsmod.api.account.persistence

import org.rsmod.api.account.character.CharacterDataStage
import org.rsmod.api.attr.AttributeKey
import org.rsmod.game.entity.Player

/** Keep unavailable definitions in their original SQL/journal form until a later cache resolves them. */
internal data class UnresolvedVarps(val values: Map<String, Int>) : CharacterDataStage.Segment {
    companion object : CharacterDataStage.Applier<UnresolvedVarps> {
        private val key = AttributeKey<UnresolvedVarps>()

        fun get(player: Player): Map<String, Int> = player.attr[key]?.values.orEmpty()

        override fun apply(player: Player, data: UnresolvedVarps) {
            player.attr[key] = UnresolvedVarps(data.values.toMap())
        }
    }
}
