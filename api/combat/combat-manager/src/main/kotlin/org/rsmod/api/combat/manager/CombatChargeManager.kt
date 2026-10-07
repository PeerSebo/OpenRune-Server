package org.rsmod.api.combat.manager

import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import org.rsmod.api.obj.charges.ObjChargeManager
import org.rsmod.api.player.righthand
import org.rsmod.game.entity.Player

public class CombatChargeManager @Inject constructor(private val manager: ObjChargeManager) {
    public fun getWeaponCharges(player: Player, varobj: String): Int =
        manager.getCharges(player.righthand, varobj)

    public fun attemptDetractWeapon(
        player: Player,
        varobj: String,
        decrement: Int = 1,
    ): ObjChargeManager.Uncharge =
        manager.reduceWornCharges(player, Wearpos.RightHand, varobj, decrement)
}
