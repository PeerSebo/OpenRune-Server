package org.rsmod.api.player.hook

import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.game.entity.Player

public sealed class RestrictedAction {
    public data object Walk : RestrictedAction()

    public data object Prayer : RestrictedAction()

    public data object Food : RestrictedAction()

    public data object Drink : RestrictedAction()

    public data class Equip(val obj: ItemServerType) : RestrictedAction()
}

public fun interface PlayerRestrictionHook {
    public fun restriction(player: Player, action: RestrictedAction): String?
}

@Singleton
public class PlayerRestrictions @Inject constructor(private val hooks: Set<PlayerRestrictionHook>) {
    public fun check(player: Player, action: RestrictedAction): String? {
        for (hook in hooks) {
            val message = hook.restriction(player, action)
            if (message != null) {
                return message
            }
        }
        return null
    }
}
