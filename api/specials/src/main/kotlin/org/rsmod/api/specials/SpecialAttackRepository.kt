package org.rsmod.api.specials

import jakarta.inject.Inject
import org.rsmod.api.specials.combat.MagicSpecialAttack
import org.rsmod.api.specials.combat.MeleeSpecialAttack
import org.rsmod.api.specials.combat.RangedSpecialAttack
import org.rsmod.api.specials.instant.InstantSpecialAttack
import org.rsmod.api.specials.weapon.SpecialAttackWeapons

public class SpecialAttackRepository
@Inject
constructor(private val registry: SpecialAttackRegistry) {
    /**
     * Deduct energy only after activate returns true; the special executes with its
     * pre-deduction energy.
     */
    public fun registerInstant(specWeapon: String, special: InstantSpecialAttack) {
        val result = registry.add(specWeapon, special)
        assertValidResult(specWeapon, result)
    }

    /**
     * Energy is deducted only after a successful attack. Nonstandard requirements, such as
     * Soulreaper stacks, are handled by the special itself.
     */
    public fun registerMelee(specWeapon: String, special: MeleeSpecialAttack) {
        val result = registry.add(specWeapon, special)
        assertValidResult(specWeapon, result)
    }

    /**
     * Energy is deducted only after a successful attack. Nonstandard requirements are handled
     * by the special itself.
     */
    public fun registerRanged(specWeapon: String, special: RangedSpecialAttack) {
        val result = registry.add(specWeapon, special)
        assertValidResult(specWeapon, result)
    }

    /**
     * Applies to powered-staff attacks, not ordinary spells. Deduct energy only after success;
     * nonstandard requirements are handled by the special itself.
     */
    public fun registerMagic(specWeapon: String, special: MagicSpecialAttack) {
        val result = registry.add(specWeapon, special)
        assertValidResult(specWeapon, result)
    }

    private fun assertValidResult(
        specWeapon: String,
        result: SpecialAttackRegistry.Result.Add,
    ) {
        when (result) {
            SpecialAttackRegistry.Result.Add.AlreadyAdded -> {
                error("Weapon already has a special attack mapped: $specWeapon")
            }
            SpecialAttackRegistry.Result.Add.SpecialEnergyNotMapped -> {
                error(
                    "Weapon `$specWeapon` was not found in the required enums. " +
                        "Use [${SpecialAttackWeapons::class}] " +
                        "and AttackEnergyEnums " +
                        "as reference for which enums are required."
                )
            }
            SpecialAttackRegistry.Result.Add.Success -> {
            }
        }
    }
}
