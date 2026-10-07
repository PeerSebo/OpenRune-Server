package org.rsmod.api.net.rsprot.handlers

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import net.rsprot.protocol.game.incoming.npcs.OpNpc6
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.mes
import org.rsmod.game.entity.Player

class OpNpc6Handler : MessageHandler<OpNpc6> {
    private val logger = InlineLogger()

    override fun handle(player: Player, message: OpNpc6) {
        val type = ServerCacheManager.getNpc(message.id) ?: return
        if (type.examine.isEmpty()) {
            logger.debug { "OpNpc6 (examine) has no text: npc=${message.id} (${type.name})" }
            return
        }
        player.mes(type.examine, ChatType.NpcExamine)
    }
}
