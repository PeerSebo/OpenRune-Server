package org.rsmod.api.combat.commons

import org.rsmod.api.utils.vars.VarEnumDelegate

/**
 * Stances identify combat-tab selections; several selections can share an AttackStyle or
 * AttackType while using different animations.
 */
public enum class CombatStance(override val varValue: Int) : VarEnumDelegate {
    Stance1(varValue = 0),
    Stance2(varValue = 1),
    Stance3(varValue = 2),
    Stance4(varValue = 3);

    public companion object {
        public operator fun get(varValue: Int): CombatStance? =
            when (varValue) {
                Stance1.varValue -> Stance1
                Stance2.varValue -> Stance2
                Stance3.varValue -> Stance3
                Stance4.varValue -> Stance4
                else -> null
            }
    }
}
