package org.rsmod.content.skills.magic.spell.attacks.standard

import org.rsmod.api.spells.attack.SpellAttackManager
import org.rsmod.api.spells.attack.SpellAttackMap
import org.rsmod.api.spells.attack.SpellAttackRepository
import org.rsmod.content.skills.magic.spell.attacks.EffectSpellAttack
import org.rsmod.content.skills.magic.spell.attacks.SpellEffects

class CurseSpells : SpellAttackMap {
    override fun SpellAttackRepository.register(manager: SpellAttackManager) {
        register(
            spell = "obj.03_confuse",
            attack =
                curse(
                    manager,
                    anim = "seq.human_castconfuse",
                    staffAnim = "seq.human_castconfuse_staff",
                    name = "confuse",
                    castSound = "synth.CONFUSE_CAST_AND_FIRE",
                    hitSound = "synth.CONFUSE_HIT",
                    stat = SpellEffects.ATTACK,
                    percent = 5,
                ),
        )
        register(
            spell = "obj.11_weaken",
            attack =
                curse(
                    manager,
                    anim = "seq.human_castweaken",
                    staffAnim = "seq.human_castweaken_staff",
                    name = "weaken",
                    castSound = "synth.WEAKEN_ALL",
                    hitSound = null,
                    stat = SpellEffects.STRENGTH,
                    percent = 5,
                ),
        )
        register(
            spell = "obj.19_curse",
            attack =
                curse(
                    manager,
                    anim = "seq.human_castcurse",
                    staffAnim = "seq.human_castcurse_staff",
                    name = "curse",
                    castSound = "synth.CURSE_CAST_AND_FIRE",
                    hitSound = "synth.CURSE_HIT",
                    stat = SpellEffects.DEFENCE,
                    percent = 5,
                ),
        )
        register(
            spell = "obj.66_vulnerability",
            attack =
                curse(
                    manager,
                    anim = "seq.human_castcurse",
                    staffAnim = "seq.human_castcurse_staff",
                    name = "vulnerability",
                    castSound = "synth.VULNERABILITY_ALL",
                    hitSound = null,
                    stat = SpellEffects.DEFENCE,
                    percent = 10,
                    projanim = "projanim.vulnerability",
                ),
        )
        register(
            spell = "obj.73_enfeeble",
            attack =
                curse(
                    manager,
                    anim = "seq.human_castenfeeble",
                    staffAnim = "seq.human_castenfeeble_staff",
                    name = "enfeeble",
                    castSound = "synth.ENFEEBLE_CAST_AND_FIRE",
                    hitSound = "synth.ENFEEBLE_HIT",
                    stat = SpellEffects.STRENGTH,
                    percent = 10,
                    projanim = "projanim.enfeeble",
                ),
        )
        register(
            spell = "obj.80_stun",
            attack =
                curse(
                    manager,
                    anim = "seq.human_caststun",
                    staffAnim = "seq.human_caststun_staff",
                    name = "stun",
                    castSound = "synth.STUN_ALL",
                    hitSound = null,
                    stat = SpellEffects.ATTACK,
                    percent = 10,
                    projanim = "projanim.stun",
                ),
        )

        register(
            spell = "obj.20_bind",
            attack =
                bind(
                    manager,
                    impact = "spotanim.bind_impact",
                    castSound = "synth.BIND_CAST",
                    hitSound = "synth.BIND_IMPACT",
                    ticks = BIND_TICKS,
                ),
        )
        register(
            spell = "obj.50_snare",
            attack =
                bind(
                    manager,
                    impact = "spotanim.snare_impact",
                    castSound = "synth.SNARE_ALL",
                    hitSound = null,
                    ticks = SNARE_TICKS,
                ),
        )
        register(
            spell = "obj.79_entangle",
            attack =
                bind(
                    manager,
                    impact = "spotanim.entangle_impact",
                    castSound = "synth.ENTANGLE_CAST_AND_FIRE",
                    hitSound = "synth.ENTANGLE_HIT",
                    ticks = ENTANGLE_TICKS,
                ),
        )
    }

    private fun curse(
        manager: SpellAttackManager,
        anim: String,
        staffAnim: String,
        name: String,
        castSound: String?,
        hitSound: String?,
        stat: String,
        percent: Int,
        projanim: String = "projanim.confuse",
    ): EffectSpellAttack =
        EffectSpellAttack(
            manager = manager,
            staffAnim = staffAnim,
            unarmedAnim = anim,
            launch = "spotanim.${name}_casting",
            travel = "spotanim.${name}_travel",
            projanim = projanim,
            impact = "spotanim.${name}_impact",
            castSound = castSound,
            hitSound = hitSound,
            onLand = { target, _ -> SpellEffects.drainPercent(target, stat, percent) },
        )

    private fun bind(
        manager: SpellAttackManager,
        impact: String,
        castSound: String?,
        hitSound: String?,
        ticks: Int,
    ): EffectSpellAttack =
        EffectSpellAttack(
            manager = manager,
            staffAnim = "seq.human_castentangle_staff",
            unarmedAnim = "seq.human_castentangle",
            launch = "spotanim.entangle_casting",
            travel = "spotanim.entangle_travel",
            projanim = "projanim.bind",
            impact = impact,
            impactHeight = 0,
            castSound = castSound,
            hitSound = hitSound,
            onLand = { target, _ -> SpellEffects.freeze(target, ticks) },
        )

    private companion object {
        private const val BIND_TICKS = 8
        private const val SNARE_TICKS = 16
        private const val ENTANGLE_TICKS = 24
    }
}
