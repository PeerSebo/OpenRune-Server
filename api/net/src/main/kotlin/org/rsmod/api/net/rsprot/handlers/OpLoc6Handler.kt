package org.rsmod.api.net.rsprot.handlers

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import jakarta.inject.Inject
import net.rsprot.protocol.game.incoming.locs.OpLoc6
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.mes
import org.rsmod.game.entity.Player

class OpLoc6Handler @Inject constructor() : MessageHandler<OpLoc6> {
    private val logger = InlineLogger()

    override fun handle(player: Player, message: OpLoc6) {
        val type = ServerCacheManager.getObject(message.id) ?: return
        if (type.desc.isEmpty()) {
            logger.debug { "OpLoc6 (examine) has no text: loc=${message.id} (${type.name})" }
            return
        }
        player.mes(type.desc, ChatType.LocExamine)
    }
}
