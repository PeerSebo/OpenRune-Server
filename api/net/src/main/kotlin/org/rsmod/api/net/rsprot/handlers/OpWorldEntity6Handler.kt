package org.rsmod.api.net.rsprot.handlers

import com.github.michaelbull.logging.InlineLogger
import net.rsprot.protocol.game.incoming.worldentities.OpWorldEntity6
import org.rsmod.game.entity.Player

/**
 * OpWorldEntity6 carries a cache config id, unlike ops 1-5 which carry an allocated entity
 * index.
 */
class OpWorldEntity6Handler : MessageHandler<OpWorldEntity6> {
    private val logger = InlineLogger()

    override fun handle(player: Player, message: OpWorldEntity6) {
        logger.debug { "OpWorldEntity6 (examine, no text source): configId=${message.id}" }
    }
}
