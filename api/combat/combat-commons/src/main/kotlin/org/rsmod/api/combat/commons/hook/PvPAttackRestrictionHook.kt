package org.rsmod.api.combat.commons.hook

import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.game.entity.Player

/**
 * Runs after attack style/special resolution. A non-null result rejects that attack;
 * PvPAttackValidateHook governs whether the players may fight at all.
 */
public fun interface PvPAttackRestrictionHook {
    public fun restriction(
        attacker: Player,
        target: Player,
        attack: CombatAttack.PlayerAttack,
        special: Boolean,
    ): String?
}
