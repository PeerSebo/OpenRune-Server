package org.rsmod.content.skills.magic.spell.attacks.standard

import dev.openrune.types.ItemServerType
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.spells.attack.SpellAttack
import org.rsmod.api.spells.attack.SpellAttackManager
import org.rsmod.api.spells.attack.SpellAttackMap
import org.rsmod.api.spells.attack.SpellAttackRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.type.getOrNull

class ClassicCombatSpells : SpellAttackMap {
    override fun SpellAttackRepository.register(manager: SpellAttackManager) {
        register(
            spell = "obj.50_magic_dart",
            attack =
                DamageSpellAttack(
                    manager = manager,
                    staffAnim = "seq.human_caststrike_staff",
                    unarmedAnim = "seq.human_caststrike",
                    launch = null,
                    travel = "spotanim.slayer_magicdart_travel",
                    impact = "spotanim.slayer_magicdart_impact",
                    castSound = "synth.MAGIC_DART_FIRE",
                    hitSound = "synth.MAGIC_DART_HIT",
                    baseMaxHit = 0,
                ),
        )

        register(
            spell = "obj.50_iban_blast",
            attack =
                DamageSpellAttack(
                    manager = manager,
                    staffAnim = "seq.human_caststrike_staff",
                    unarmedAnim = "seq.human_caststrike",
                    launch = "spotanim.ibanblast_casting",
                    travel = "spotanim.ibanblast_travel",
                    impact = "spotanim.ibanblast_impact",
                    castSound = "synth.IBAN_LIGHTNING",
                    hitSound = null,
                    baseMaxHit = 25,
                ),
        )

        register(
            spell = "obj.39_crumble_undead",
            attack =
                DamageSpellAttack(
                    manager = manager,
                    staffAnim = "seq.human_caststrike_staff",
                    unarmedAnim = "seq.human_caststrike",
                    launch = "spotanim.crumbleundead_casting",
                    travel = "spotanim.crumbleundead_travel",
                    impact = "spotanim.crumbleundead_impact",
                    castSound = "synth.CRUMBLE_CAST_AND_FIRE",
                    hitSound = "synth.CRUMBLE_HIT",
                    baseMaxHit = 15,
                    doubleVsUndead = true,
                ),
        )

        register(
            spell = "obj.60_saradomin_strike",
            attack =
                DamageSpellAttack(
                    manager = manager,
                    staffAnim = "seq.human_caststrike_staff",
                    unarmedAnim = "seq.human_caststrike",
                    launch = null,
                    travel = "spotanim.saradomin_lightning",
                    impact = "spotanim.saradomin_lightning",
                    castSound = "synth.SARADOMIN_STRIKE",
                    hitSound = null,
                    baseMaxHit = 20,
                ),
        )

        register(
            spell = "obj.60_claws_of_guthix",
            attack =
                DamageSpellAttack(
                    manager = manager,
                    staffAnim = "seq.human_caststrike_staff",
                    unarmedAnim = "seq.human_caststrike",
                    launch = null,
                    travel = "spotanim.gunthix_claw",
                    impact = "spotanim.gunthix_claw",
                    castSound = "synth.CLAWS_OF_GUTHIX",
                    hitSound = null,
                    baseMaxHit = 20,
                ),
        )

        register(
            spell = "obj.60_flames_of_zamorak",
            attack =
                DamageSpellAttack(
                    manager = manager,
                    staffAnim = "seq.human_caststrike_staff",
                    unarmedAnim = "seq.human_caststrike",
                    launch = null,
                    travel = "spotanim.zamorak_flame",
                    impact = "spotanim.zamorak_flame",
                    castSound = "synth.FLAMES_OF_ZAMORAK",
                    hitSound = null,
                    baseMaxHit = 20,
                ),
        )
    }

    private class DamageSpellAttack(
        private val manager: SpellAttackManager,
        private val staffAnim: String,
        private val unarmedAnim: String,
        private val launch: String?,
        private val travel: String,
        private val impact: String?,
        private val castSound: String?,
        private val hitSound: String?,
        private val baseMaxHit: Int,
        private val doubleVsUndead: Boolean = false,
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
            if (launch != null) {
                spotanim(launch, height = 92)
            }

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

            var effectiveMaxHit = baseMaxHit
            if (doubleVsUndead && target is Npc && target.visType.param(params.undead) != 0) {
                effectiveMaxHit *= 2
            }

            val damage = manager.rollMaxHit(this, target, attack, castResult, effectiveMaxHit)
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
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMagicHit(this, target, spell, damage, clientDelay, serverDelay)
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
