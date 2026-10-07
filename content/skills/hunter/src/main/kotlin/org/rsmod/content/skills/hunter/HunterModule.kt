package org.rsmod.content.skills.hunter

import jakarta.inject.Inject
import org.rsmod.api.player.hook.PlayerRestrictionHook
import org.rsmod.api.player.hook.RestrictedAction
import org.rsmod.game.entity.Player
import org.rsmod.plugin.module.PluginModule

class HunterModule : PluginModule() {
    override fun bind() {
        addSetBinding<PlayerRestrictionHook>(FalconryEquipmentRestriction::class.java)
    }
}

internal class FalconryEquipmentRestriction @Inject constructor(private val falconry: FalconryService) : PlayerRestrictionHook {
    override fun restriction(player: Player, action: RestrictedAction): String? =
        if (action is RestrictedAction.Equip && falconry.active(player) &&
            action.obj.wearpos1 in listOf(3, 5, 9))
            "Return your falcon to Matthias before equipping a weapon, shield or gloves."
        else null
}
