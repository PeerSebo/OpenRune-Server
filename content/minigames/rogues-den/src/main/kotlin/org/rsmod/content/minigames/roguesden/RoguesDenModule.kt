package org.rsmod.content.minigames.roguesden

import jakarta.inject.Inject
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.content.minigames.roguesden.configs.RoguesDenState
import org.rsmod.game.entity.Player
import org.rsmod.plugin.module.PluginModule

class RoguesDenModule : PluginModule() {
    override fun bind() {
        addSetBinding<PlayerDeathCleanupHook>(RoguesDenDeathCleanup::class.java)
    }
}

class RoguesDenDeathCleanup @Inject constructor() : PlayerDeathCleanupHook {
    override fun cleanup(player: Player) = RoguesDenState.clearSession(player)
}
