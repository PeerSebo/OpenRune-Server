package org.rsmod.content.other.special.attacks.melee

import jakarta.inject.Inject
import kotlin.math.min
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.config.constants
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.specials.combat.MeleeSpecialAttack
import org.rsmod.content.other.special.attacks.TargetStats
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player

class MiscMeleeSpecialAttacks @Inject constructor(private val worldRepo: WorldRepository) :
    SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        registerMelee("obj.darklight", Weaken(manager, demonPercent = DARKLIGHT_DEMON_PERCENT))
        registerMelee("obj.arclight", Weaken(manager, demonPercent = ARCLIGHT_DEMON_PERCENT))
        registerMelee(
            "obj.arclight_inactive",
            Weaken(manager, demonPercent = ARCLIGHT_DEMON_PERCENT),
        )

        registerInstant("obj.excalibur") { sanctuary(worldRepo) }

        val backstab = Backstab(manager)
        registerMelee("obj.dttd_bone_dagger", backstab)
        registerMelee("obj.dttd_bone_dagger_p", backstab)
        registerMelee("obj.dttd_bone_dagger_p+", backstab)
        registerMelee("obj.dttd_bone_dagger_p++", backstab)

        registerMelee("obj.olaf2_brine_sabre", Liquify(manager))
        registerMelee("obj.voidwaker", Disrupt(manager))

        val eviscerate = Eviscerate(manager)
        registerMelee("obj.osmumtens_fang", eviscerate)
        registerMelee("obj.osmumtens_fang_ornament", eviscerate)
    }

    private class Weaken(private val manager: SpecialAttackManager, private val demonPercent: Int) :
        MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Melee): Boolean {
            val percent = if (target.isDemon()) demonPercent else WEAKEN_PERCENT
            return weaken(target, attack, percent)
        }

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Melee): Boolean =
            weaken(target, attack, WEAKEN_PERCENT)

        private fun ProtectedAccess.weaken(
            target: PathingEntity,
            attack: CombatAttack.Melee,
            percent: Int,
        ): Boolean {
            anim("seq.dark_spec_player")
            spotanim("spotanim.dark_spec_spot", height = 96, slot = COMBAT_SLOT)
            val damage = manager.rollMeleeDamage(this, target, attack, 1.0, 1.0)
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMeleeHit(this, target, damage)
            if (damage > 0) {
                for (stat in listOf(TargetStats.ATTACK, TargetStats.STRENGTH, TargetStats.DEFENCE)) {
                    TargetStats.drainPercentOfCurrent(target, stat, percent)
                }
            }
            manager.continueCombat(this, target)
            return true
        }

        private fun Npc.isDemon(): Boolean = type.name.contains("demon", ignoreCase = true)
    }

    private class Backstab(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Melee): Boolean {
            val unaware = !target.isFighting(player)
            return backstab(target, attack, unaware)
        }

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Melee): Boolean =
            backstab(target, attack, unaware = false)

        private fun ProtectedAccess.backstab(
            target: PathingEntity,
            attack: CombatAttack.Melee,
            unaware: Boolean,
        ): Boolean {
            anim("seq.dttd_player_stab_bone_dagger")
            spotanim("spotanim.dttd_dagger_sp_attack_spotanim", height = 96, slot = COMBAT_SLOT)
            val damage =
                if (unaware) {
                    manager.rollMeleeMaxHit(this, target, attack.type, attack.style, 1.0)
                } else {
                    manager.rollMeleeDamage(this, target, attack, 1.0, 1.0)
                }
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMeleeHit(this, target, damage)
            if (damage > 0) {
                TargetStats.drain(target, TargetStats.DEFENCE, damage)
            }
            manager.continueCombat(this, target)
            return true
        }
    }

    private class Liquify(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Melee) =
            liquify(target, attack)

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Melee) =
            liquify(target, attack)

        private fun ProtectedAccess.liquify(target: PathingEntity, attack: CombatAttack.Melee): Boolean {
            anim("seq.olaf2_brine_sabre_special")
            spotanim("spotanim.olaf2_brine_sabre_special_spot", height = 96, slot = COMBAT_SLOT)
            val damage = manager.rollMeleeDamage(this, target, attack, 2.0, 1.0)
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMeleeHit(this, target, damage)
            if (damage > 0) {
                for (stat in listOf(TargetStats.ATTACK, TargetStats.STRENGTH, TargetStats.DEFENCE)) {
                    val cap = LIQUIFY_BASE_CAP + TargetStats.base(player, stat) / 10
                    val boost = min(cap, damage / 4)
                    statBoost(stat, constant = boost, percent = 0)
                }
            }
            manager.continueCombat(this, target)
            return true
        }
    }

    private class Disrupt(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Melee) =
            disrupt(target, attack)

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Melee) =
            disrupt(target, attack)

        private fun ProtectedAccess.disrupt(target: PathingEntity, attack: CombatAttack.Melee): Boolean {
            anim("seq.human_special02_voidwaker")
            spotanim("spotanim.fx_voidwaker02_special", height = 96, slot = COMBAT_SLOT)
            val maxHit = manager.calculateMeleeMaxHit(this, target, attack.type, attack.style, 1.0)
            val damage = random.of(maxHit / 2..(maxHit * 3) / 2)
            target.spotanim("spotanim.fx_voidwaker_impact", height = 96)
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMagicHit(this, target, damage, clientDelay = 0, hitDelay = 1)
            manager.continueCombat(this, target)
            return true
        }
    }

    private class Eviscerate(private val manager: SpecialAttackManager) : MeleeSpecialAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Melee) =
            eviscerate(target, attack)

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Melee) =
            eviscerate(target, attack)

        private fun ProtectedAccess.eviscerate(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            anim("seq.human_osmumtens_fang")
            val landed =
                manager.rollMeleeAccuracy(this, target, attack.type, attack.style, attack.type, 1.5)
            val damage =
                if (landed) {
                    val maxHit = manager.calculateMeleeMaxHit(this, target, attack.type, attack.style, 1.0)
                    random.of((maxHit * 15) / 100..(maxHit * 85) / 100)
                } else {
                    0
                }
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMeleeHit(this, target, damage)
            manager.continueCombat(this, target)
            return true
        }
    }

    private companion object {
        private const val COMBAT_SLOT = constants.spotanim_slot_combat
        private const val SOUND_RADIUS = 10
        private const val WEAKEN_PERCENT = 5
        private const val DARKLIGHT_DEMON_PERCENT = 10
        private const val ARCLIGHT_DEMON_PERCENT = 15
        private const val SANCTUARY_DEFENCE_BOOST = 8
        private const val LIQUIFY_BASE_CAP = 3

        private fun ProtectedAccess.sanctuary(worldRepo: WorldRepository): Boolean {
            statBoost(TargetStats.DEFENCE, constant = SANCTUARY_DEFENCE_BOOST, percent = 0)
            say("For Camelot!")
            anim("seq.sanctuary")
            spotanim("spotanim.sp_attackglow_blue", height = 96, slot = COMBAT_SLOT)
            soundArea(worldRepo, coords, "synth.RAMPAGE", radius = SOUND_RADIUS)
            return true
        }

        private fun Npc.isFighting(player: Player): Boolean =
            vars["varn.attacking_player"] == player.uid.packed
    }
}
