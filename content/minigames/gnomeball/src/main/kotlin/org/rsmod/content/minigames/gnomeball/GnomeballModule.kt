package org.rsmod.content.minigames.gnomeball

import jakarta.inject.Inject
import org.rsmod.api.player.hook.PlayerRestrictionHook
import org.rsmod.api.player.hook.RestrictedAction
import org.rsmod.game.entity.Player
import org.rsmod.plugin.module.PluginModule

class GnomeballModule : PluginModule() {
    override fun bind() { addSetBinding<PlayerRestrictionHook>(GnomeballRestrictions::class.java) }
}

internal class GnomeballRestrictions @Inject constructor() : PlayerRestrictionHook {
    override fun restriction(player: Player, action: RestrictedAction): String? =
        if (GnomeballState.active(player) && action is RestrictedAction.Equip &&
            action.obj.wearpos1 in listOf(3, 5))
            "Leave the Gnomeball field before equipping a weapon or shield."
        else null
}
