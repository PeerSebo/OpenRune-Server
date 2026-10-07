package org.rsmod.content.interfaces.trading

import jakarta.inject.Inject
import org.rsmod.api.player.events.PlayerMoveClick
import org.rsmod.api.player.events.PlayerTradeRequest
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.IfModalButton
import org.rsmod.api.script.*
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class TradingScript @Inject constructor(
    private val manager: TradeManager,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<PlayerTradeRequest> { manager.request(player, target) }
        onPlayerLogout { manager.cancel(player) }
        onEvent<PlayerMoveClick> { manager.cancel(player) }
        onIfModalButton("component.tradeside:side_layer") { dispatch(TradeScreenAction.Offer, it) }
        onIfModalButton("component.trademain:other_offer") {
            if (it.op == dev.openrune.types.aconverted.interf.IfButtonOp.Op10) {
                manager.examineOther(player, it.comsub, it.obj?.id)
            }
        }
    }

    private suspend fun ProtectedAccess.dispatch(action: TradeScreenAction, button: IfModalButton) {
        if (manager.session(player) != null) manager.button(this, action, button.comsub, button.op, button.obj?.id)
    }
}
