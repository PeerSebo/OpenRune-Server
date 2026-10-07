package org.rsmod.content.minigames.tearsofguthix

import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.player.hook.PlayerMovementSpeedHook
import org.rsmod.api.player.hook.PlayerRestrictionHook
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.api.player.hook.RestrictedAction
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.game.entity.Player
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.plugin.module.PluginModule

class TearsModule : PluginModule() {
    override fun bind() {
        addSetBinding<PlayerTeleportValidateHook>(TearsRestrictions::class.java)
        addSetBinding<PlayerRestrictionHook>(TearsRestrictions::class.java)
        addSetBinding<PlayerMovementSpeedHook>(TearsRestrictions::class.java)
    }
}

class TearsRestrictions @Inject constructor() : PlayerTeleportValidateHook, PlayerRestrictionHook,
    PlayerMovementSpeedHook {
    override fun overrideSpeed(player: Player): MoveSpeed? =
        if (TearsState.active(player)) MoveSpeed.Walk else null
    override fun validate(player: Player, type: TeleportType, areaChecker: AreaChecker): String? =
        if (TearsState.active(player) && type != TeleportType.Exempt)
            blockedMessage(player) else null

    override fun restriction(player: Player, action: RestrictedAction): String? = when {
        !TearsState.active(player) -> null
        action == RestrictedAction.Walk && player.vars[TearsState.Active] == 1 -> null
        else -> blockedMessage(player)
    }

    private fun blockedMessage(player: Player): String = when (player.vars[TearsState.Active]) {
        1 -> "You can't do that while holding Juna's bowl."
        4 -> "Please wait while Juna lets you into the cave."
        else -> "Please wait while you return to Juna and drink the tears."
    }
}
