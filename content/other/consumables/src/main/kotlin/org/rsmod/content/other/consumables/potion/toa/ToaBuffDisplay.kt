package org.rsmod.content.other.consumables.potion.toa

import org.rsmod.api.player.output.runClientScript
import org.rsmod.game.entity.Player

internal enum class ToaBuffDisplay(private val struct: Int) {
    Silk(4373),
    Scarab(4374),
    Adrenaline(4375),
    Salts(4376),
    ;

    fun update(player: Player, expiry: Int) {
        player.runClientScript("clientscript.[clientscript,buff_bar_start]", struct, expiry)
    }
}
