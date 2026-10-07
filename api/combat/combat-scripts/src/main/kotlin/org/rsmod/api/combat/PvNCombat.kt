package org.rsmod.api.combat

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.manager.PlayerAttackManager
import org.rsmod.api.combat.manager.RangedAmmoManager
import org.rsmod.api.combat.player.activateMagicSpecial
import org.rsmod.api.combat.player.activateMeleeSpecial
import org.rsmod.api.combat.player.activateRangedSpecial
import org.rsmod.api.combat.player.activateShieldSpecial
import org.rsmod.api.combat.player.specialAttackType
import org.rsmod.api.combat.weapon.WeaponSpeeds
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.params
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.npc.isValidTarget
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.player.lefthand
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.quiver
import org.rsmod.api.player.righthand
import org.rsmod.api.specials.SpecialAttackRegistry
import org.rsmod.api.specials.SpecialAttackType
import org.rsmod.api.specials.energy.SpecialAttackEnergy
import org.rsmod.api.spells.attack.SpellAttackRegistry
import org.rsmod.api.spells.attack.attack
import org.rsmod.api.weapons.WeaponRegistry
import org.rsmod.api.weapons.attack
import org.rsmod.game.entity.Npc
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.type.getInvObj
import org.rsmod.game.type.getOrNull

internal class PvNCombat
@Inject
constructor(
    private val speeds: WeaponSpeeds,
    private val specialsReg: SpecialAttackRegistry,
    private val specialEnergy: SpecialAttackEnergy,
    private val weaponsReg: WeaponRegistry,
    private val manager: PlayerAttackManager,
    private val ammunition: RangedAmmoManager,
    private val spellsReg: SpellAttackRegistry,
    private val attackValidateHooks: Set<NpcAttackValidateHook>,
    private val npcInteractions: NpcInteractions,
) {
    suspend fun attack(access: ProtectedAccess, target: Npc, attack: CombatAttack.PlayerAttack) {
        when (attack) {
            is CombatAttack.Melee -> access.attackMelee(target, attack)
            is CombatAttack.Ranged -> access.attackRanged(target, attack)
            is CombatAttack.Spell -> access.attackMagicSpell(target, attack)
            is CombatAttack.Staff -> access.attackMagicStaff(target, attack)
        }
    }

    private suspend fun ProtectedAccess.attackMelee(npc: Npc, attack: CombatAttack.Melee) {
        if (!canAttack(npc)) {
            return
        }

        if (manager.isAttackDelayed(player)) {
            manager.continueCombat(player, npc)
            return
        }

        val attackRate = speeds.actual(player)
        manager.setNextAttackDelay(player, attackRate)

        // Special handlers must resume combat explicitly via `opnpc2` (or a helper).
        if (specialAttackType == SpecialAttackType.Weapon) {
            specialAttackType = SpecialAttackType.None
            val activatedSpec = activateMeleeSpecial(npc, attack, specialsReg, specialEnergy)
            if (activatedSpec) {
                return
            }
        }

        if (specialAttackType == SpecialAttackType.Shield) {
            specialAttackType = SpecialAttackType.None
            val activatedSpec = activateShieldSpecial(npc, player.lefthand, specialsReg)
            if (activatedSpec) {
                return
            }
        }

        // Weapon handlers must resume combat explicitly via `opnpc2` (or a helper).
        val specializedWeapon = weaponsReg.getMelee(attack.weapon)
        if (specializedWeapon != null) {
            val attackHandled = specializedWeapon.attack(this, npc, attack)
            if (attackHandled) {
                return
            }
        }

        val damage = manager.rollMeleeDamage(player, npc, attack)
        manager.giveCombatXp(player, npc, attack, damage)
        manager.playWeaponFx(player, attack)
        manager.queueMeleeHit(player, npc, damage)
        manager.continueCombat(player, npc)
    }

    private suspend fun ProtectedAccess.attackRanged(npc: Npc, attack: CombatAttack.Ranged) {
        if (!canAttack(npc)) {
            return
        }

        if (manager.isAttackDelayed(player)) {
            manager.continueCombat(player, npc)
            return
        }

        val attackRate = speeds.actual(player)
        manager.setNextAttackDelay(player, attackRate)

        // Special handlers must resume combat explicitly via `opnpc2` (or a helper).
        if (specialAttackType == SpecialAttackType.Weapon) {
            specialAttackType = SpecialAttackType.None
            val activatedSpec = activateRangedSpecial(npc, attack, specialsReg, specialEnergy)
            if (activatedSpec) {
                return
            }
        }

        if (specialAttackType == SpecialAttackType.Shield) {
            specialAttackType = SpecialAttackType.None
            val activatedSpec = activateShieldSpecial(npc, player.lefthand, specialsReg)
            if (activatedSpec) {
                return
            }
        }

        val righthandType = getInvObj(attack.weapon)

        // Weapon handlers must resume combat explicitly via `opnpc2` (or a helper).
        val specializedWeapon = weaponsReg.getRanged(attack.weapon)
        if (specializedWeapon != null) {
            val attackHandled = specializedWeapon.attack(this, npc, attack)
            if (attackHandled) {
                return
            }
        }

        // `chargebows` are specialized and not worth trying to have as a generic system. As such,
        // they are required to be registered in `WeaponRegistry` and will return early if they
        // have reached this point (not handled by the previous `specializedWeapon` block).
        val usingChargeBow = righthandType.isCategoryType("category.chargebow")
        if (usingChargeBow) {
            manager.stopCombat(player)
            mes("The bow refuses to fire.")
            return
        }

        val quiver = player.quiver
        val quiverType = getOrNull(quiver)

        val canUseAmmo = ammunition.attemptAmmoUsage(player, righthandType, quiverType)
        if (!canUseAmmo) {
            manager.stopCombat(player)
            return
        }

        // Note: Some weapons are categorized as `throwing_weapon` but do not behave like standard
        // throwing weapons. For example, the Toxic blowpipe falls under this category but requires
        // special handling. Such weapons should be managed via the `Weapon` system to ensure
        // correct behavior and avoid unintended side effects.
        val usingThrown = righthandType.isCategoryType("category.throwing_weapon")

        val weaponType = if (usingThrown) righthandType else quiverType
        checkNotNull(weaponType) {
            "Unexpected null weapon type: righthand=$righthandType, quiver=$quiverType"
        }

        val projectileID = righthandType.paramOrNull(params.proj_type)?.id
        val travelSpotanim = weaponType.paramOrNull(params.proj_travel)
        if (projectileID == null || travelSpotanim == null) {
            manager.stopCombat(player)
            mes("You are unable to fire your ammunition.")
            return
        }

        val projanimType = RSCM.getReverseMapping(RSCMType.PROJANIM, projectileID)

        val playedAnim = manager.playWeaponFx(player, attack)
        if (!playedAnim) {
            manager.stopCombat(player)
            mes("The bow fails to fire.")
            return
        }

        // Official behavior: If the weapon (quiver or righthand, based on the thrown weapon flag)
        // has no `proj_launch` param, a "null" (-1) spotanim will still be sent in the same slot
        // and height as usual.
        val launchSpotanim = weaponType.paramOrNull(params.proj_launch)?.id ?: NULL_SPOTANIM_ID
        val launchSpotanimName =
            launchSpotanim.takeUnless { it == NULL_SPOTANIM_ID }?.let {
                RSCM.getReverseMapping(RSCMType.SPOTANIM, it)
            }
        spotanim(launchSpotanimName, height = 96, slot = constants.spotanim_slot_combat)

        val projanim = manager.spawnProjectile(player, npc, travelSpotanim, projanimType)
        val (serverDelay, clientDelay) = projanim.durations

        if (usingThrown) {
            ammunition.useThrownWeapon(player, righthandType, npc.coords, dropDelay = serverDelay)
        } else if (quiverType != null) {
            ammunition.useQuiverAmmo(player, quiverType, npc.coords, dropDelay = serverDelay)
        }

        val damage = manager.rollRangedDamage(player, npc, attack)
        manager.giveCombatXp(player, npc, attack, damage)

        val hitAmmoObj = if (usingThrown) null else quiverType
        manager.queueRangedHit(player, npc, hitAmmoObj, damage, clientDelay, serverDelay)

        if (usingThrown && player.righthand == null) {
            mes("That was your last one!")
            return
        }

        manager.continueCombat(player, npc)
    }

    private suspend fun ProtectedAccess.attackMagicSpell(npc: Npc, attack: CombatAttack.Spell) {
        if (!canAttack(npc)) {
            return
        }

        if (manager.isAttackDelayed(player)) {
            manager.continueCombat(player, npc, attack.spell)
            return
        }

        val attackRate = MAGIC_SPELL_ATTACK_RATE
        manager.setNextAttackDelay(player, attackRate)

        val spell = spellsReg[RSCM.getReverseMapping(RSCMType.OBJ, attack.spell.obj.id)]
        if (spell != null) {
            spell.attack(this, npc, attack)
            return
        }

        manager.stopCombat(player)
        mes("You attempt to cast the spell, but nothing happens.")
    }

    private suspend fun ProtectedAccess.attackMagicStaff(npc: Npc, attack: CombatAttack.Staff) {
        if (!canAttack(npc)) {
            return
        }

        if (manager.isAttackDelayed(player)) {
            manager.continueCombat(player, npc)
            return
        }

        val attackRate = MAGIC_STAFF_ATTACK_RATE
        manager.setNextAttackDelay(player, attackRate)

        // Special handlers must resume combat explicitly via `opnpc2` (or a helper).
        if (specialAttackType == SpecialAttackType.Weapon) {
            specialAttackType = SpecialAttackType.None
            val activatedSpec = activateMagicSpecial(npc, attack, specialsReg, specialEnergy)
            if (activatedSpec) {
                return
            }
        }

        if (specialAttackType == SpecialAttackType.Shield) {
            specialAttackType = SpecialAttackType.None
            val activatedSpec = activateShieldSpecial(npc, player.lefthand, specialsReg)
            if (activatedSpec) {
                return
            }
        }

        // Weapon handlers must resume combat explicitly via `opnpc2` (or a helper).
        val specializedWeapon = weaponsReg.getMagic(attack.weapon)
        if (specializedWeapon != null) {
            val attackHandled = specializedWeapon.attack(this, npc, attack)
            if (attackHandled) {
                return
            }
        }

        manager.stopCombat(player)
        mes("Your staff fails to respond.")
    }

    private fun ProtectedAccess.canAttack(npc: Npc): Boolean {
        for (hook in attackValidateHooks) {
            when (val result = hook.validate(player, npc)) {
                is NpcAttackValidateResult.Deny -> {
                    mes(result.message)
                    return false
                }
                NpcAttackValidateResult.BypassSingleWayPvnRestriction,
                NpcAttackValidateResult.Pass -> Unit
            }
        }

        if (!npc.isValidTarget()) {
            return false
        }

        // Multinpc parents (e.g. varp/varbit-keyed npcs such as Elvarg) often carry no ops
        // themselves; the client resolves and displays the leaf's ops. Mirror that here by
        // checking the player-relative resolved type - `hasOp` falls back to `npc.visType`
        // when the npc is not a multinpc (or its leaf is unresolvable).
        val hasAttackOp = npcInteractions.hasOp(npc, player.vars, InteractionOp.Op2)
        if (!hasAttackOp) {
            mes("You can't attack this npc.")
            return false
        }

        return true
    }
}
