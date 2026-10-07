package org.rsmod.api.player.events

import dev.openrune.types.SequenceServerType
import org.rsmod.events.KeyedEvent
import org.rsmod.game.entity.Player

/**
 * Emote keys are sequence ids, with one handler each; looped emotes need their own key.
 * Handlers cannot suspend or take protected access while the emote runs; defer dialogue.
 */
public class PlayerEmoteEvent(public val player: Player, public val seq: SequenceServerType) :
    KeyedEvent {
    override val id: Long = seq.id.toLong()
}
