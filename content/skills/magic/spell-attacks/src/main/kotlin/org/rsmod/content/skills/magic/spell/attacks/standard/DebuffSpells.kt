package org.rsmod.content.skills.magic.spell.attacks.standard

import dev.openrune.types.ItemServerType
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.spells.attack.SpellAttack
import org.rsmod.api.spells.attack.SpellAttackManager
import org.rsmod.api.spells.attack.SpellAttackMap
import org.rsmod.api.spells.attack.SpellAttackRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.type.getOrNull

class DebuffSpells : SpellAttackMap {
    override fun SpellAttackRepository.register(manager: SpellAttackManager) {
        register(
            spell = "obj.03_confuse",
            attack =
                DebuffSpellAttack(
                    manager = manager,
                    staffAnim = "seq.human_caststrike_staff",
                    unarmedAnim = "seq.human_caststrike",
                    launch = "spotanim.confuse_casting",
                    travel = "spotanim.confuse_travel",
                    impact = "spotanim.confuse_impact",
                    castSound = "synth.CONFUSE_CAST_AND_FIRE",
                    hitSound = "synth.CONFUSE_HIT",
                    drainStat = "stat.attack",
                    drainConstant = 1,
                    drainPercent = 5,
                ),
        )

        register(
            spell = "obj.11_weaken",
            attack =
                DebuffSpellAttack(
                    manager = manager,
                    staffAnim = "seq.human_caststrike_staff",
                    unarmedAnim = "seq.human_caststrike",
                    launch = "spotanim.weaken_casting",
                    travel = "spotanim.weaken_travel",
                    impact = "spotanim.weaken_impact",
                    castSound = "synth.WEAKEN_CAST_AND_FIRE",
                    hitSound = "synth.WEAKEN_HIT",
                    drainStat = "stat.strength",
                    drainConstant = 1,
                    drainPercent = 5,
                ),
        )

        register(
            spell = "obj.19_curse",
            attack =
                DebuffSpellAttack(
                    manager = manager,
                    staffAnim = "seq.human_caststrike_staff",
                    unarmedAnim = "seq.human_caststrike",
                    launch = "spotanim.curse_casting",
                    travel = "spotanim.curse_travel",
                    impact = "spotanim.curse_impact",
                    castSound = "synth.CURSE_CAST_AND_FIRE",
                    hitSound = "synth.CURSE_HIT",
                    drainStat = "stat.defence",
                    drainConstant = 1,
                    drainPercent = 5,
                ),
        )

        register(
            spell = "obj.66_vulnerability",
            attack =
                DebuffSpellAttack(
                    manager = manager,
                    staffAnim = "seq.human_caststrike_staff",
                    unarmedAnim = "seq.human_caststrike",
                    launch = "spotanim.vulnerability_casting",
                    travel = "spotanim.vulnerability_travel",
                    impact = "spotanim.vulnerability_impact",
                    castSound = "synth.VULNERABILITY_CAST_AND_FIRE",
                    hitSound = "synth.VULNERABILITY_IMPACT",
                    drainStat = "stat.defence",
                    drainConstant = 3,
                    drainPercent = 10,
                ),
        )

        register(
            spell = "obj.73_enfeeble",
            attack =
                DebuffSpellAttack(
                    manager = manager,
                    staffAnim = "seq.human_caststrike_staff",
                    unarmedAnim = "seq.human_caststrike",
                    launch = "spotanim.enfeeble_casting",
                    travel = "spotanim.enfeeble_travel",
                    impact = "spotanim.enfeeble_impact",
                    castSound = "synth.ENFEEBLE_CAST_AND_FIRE",
                    hitSound = "synth.ENFEEBLE_HIT",
                    drainStat = "stat.strength",
                    drainConstant = 3,
                    drainPercent = 10,
                ),
        )

        register(
            spell = "obj.80_stun",
            attack =
                DebuffSpellAttack(
                    manager = manager,
                    staffAnim = "seq.human_caststrike_staff",
                    unarmedAnim = "seq.human_caststrike",
                    launch = "spotanim.stun_casting",
                    travel = "spotanim.stun_travel",
                    impact = "spotanim.stun_impact",
                    castSound = "synth.STUN_CAST_AND_FIRE",
                    hitSound = "synth.STUN_IMPACT",
                    drainStat = "stat.attack",
                    drainConstant = 3,
                    drainPercent = 10,
                ),
        )
    }

    private class DebuffSpellAttack(
        private val manager: SpellAttackManager,
        private val staffAnim: String,
        private val unarmedAnim: String,
        private val launch: String,
        private val travel: String,
        private val impact: String,
        private val castSound: String,
        private val hitSound: String,
        private val drainStat: String,
        private val drainConstant: Int,
        private val drainPercent: Int,
    ) : SpellAttack {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Spell) {
            cast(target, attack)
        }

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Spell) {
            cast(target, attack)
        }

        private fun ProtectedAccess.cast(target: PathingEntity, attack: CombatAttack.Spell) {
            val castResult = manager.attemptCast(this, attack)
            if (castResult.isFailure()) {
                return
            }
            val weaponType = getOrNull(attack.weapon)
            val castAnim = weaponType.castStrikeAnim()

            player.anim(castAnim, priority = 6)
            spotanim(launch, height = 92)

            val proj = manager.spawnProjectile(this, target, travel, "projanim.magic_spell")
            val (serverDelay, clientDelay) = proj.durations
            val spell = attack.spell.obj

            val splash = manager.rollSplash(this, target, attack, castResult)
            if (splash) {
                manager.playSplashFx(this, target, clientDelay, castSound, soundRadius = 8)
                manager.queueSplashHit(this, target, spell, clientDelay, serverDelay)
                manager.continueCombatIfAutocast(this, target)
                return
            }

            if (target is Player) {
                target.statSub(drainStat, constant = drainConstant, percent = drainPercent)
            }

            manager.playHitFx(
                source = this,
                target = target,
                clientDelay = clientDelay,
                castSound = castSound,
                soundRadius = 8,
                hitSpot = impact,
                hitSpotHeight = 124,
                hitSound = hitSound,
            )
            manager.giveCombatXp(this, target, attack, damage = 0)
            manager.queueMagicHit(this, target, spell, damage = 0, clientDelay = clientDelay)
            manager.continueCombatIfAutocast(this, target)
        }

        private fun ItemServerType?.castStrikeAnim(): String =
            if (this != null && isCategoryType("category.staff")) {
                staffAnim
            } else {
                unarmedAnim
            }
    }
}
