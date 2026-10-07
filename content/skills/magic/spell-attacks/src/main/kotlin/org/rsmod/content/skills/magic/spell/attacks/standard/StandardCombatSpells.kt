package org.rsmod.content.skills.magic.spell.attacks.standard

import org.rsmod.api.spells.attack.SpellAttackManager
import org.rsmod.api.spells.attack.SpellAttackMap
import org.rsmod.api.spells.attack.SpellAttackRepository
import org.rsmod.content.skills.magic.spell.attacks.EffectSpellAttack
import org.rsmod.content.skills.magic.spell.attacks.SpellEffects
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

class StandardCombatSpells : SpellAttackMap {
    override fun SpellAttackRepository.register(manager: SpellAttackManager) {
        register(
            spell = "obj.39_crumble_undead",
            attack =
                EffectSpellAttack(
                    manager = manager,
                    staffAnim = "seq.human_castcrumbleundead_staff",
                    unarmedAnim = "seq.human_castcrumbleundead",
                    launch = "spotanim.crumbleundead_casting",
                    travel = "spotanim.crumbleundead_travel",
                    projanim = "projanim.crumble_undead",
                    impact = "spotanim.crumbleundead_impact",
                    castSound = "synth.CRUMBLE_CAST_AND_FIRE",
                    hitSound = "synth.CRUMBLE_HIT",
                    baseMaxHit = { CRUMBLE_UNDEAD_MAX_HIT },
                    targetCheck = { target ->
                        if (target is Npc && !SpellEffects.isUndead(target)) {
                            "This spell only affects skeletons, zombies, ghosts and shades."
                        } else if (target is Player) {
                            "This spell only affects skeletons, zombies, ghosts and shades."
                        } else {
                            null
                        }
                    },
                ),
        )

        register(
            spell = "obj.50_iban_blast",
            attack =
                EffectSpellAttack(
                    manager = manager,
                    staffAnim = "seq.human_castibanblast",
                    launch = "spotanim.ibanblast_casting",
                    travel = "spotanim.ibanblast_travel",
                    projanim = "projanim.iban_blast",
                    impact = "spotanim.ibanblast_impact",
                    castSound = null,
                    hitSound = null,
                    baseMaxHit = { IBAN_BLAST_MAX_HIT },
                ),
        )

        val magicDart =
            EffectSpellAttack(
                manager = manager,
                staffAnim = "seq.slayer_magicdart_cast",
                launch = null,
                travel = "spotanim.slayer_magicdart_travel",
                impact = "spotanim.slayer_magicdart_impact",
                castSound = null,
                hitSound = "synth.MAGIC_DART_HIT",
                baseMaxHit = { magicLvl -> MAGIC_DART_BASE + magicLvl / 10 },
            )
        register(spell = "obj.50_magic_dart", attack = magicDart)
        register(spell = "obj.50_slayer_dart", attack = magicDart)

        register(
            spell = "obj.60_saradomin_strike",
            attack =
                godSpell(manager, impact = "spotanim.saradomin_lightning", sound = "synth.SARADOMIN_STRIKE") { target ->
                    if (target is Player) {
                        SpellEffects.drainFlat(target, SpellEffects.PRAYER, SARADOMIN_PRAYER_DRAIN)
                    }
                },
        )
        register(
            spell = "obj.60_claws_of_guthix",
            attack =
                godSpell(manager, impact = "spotanim.guthix_claw_green", sound = "synth.CLAWS_OF_GUTHIX") { target ->
                    SpellEffects.drainPercent(target, SpellEffects.DEFENCE, GOD_SPELL_DRAIN_PERCENT)
                },
        )
        register(
            spell = "obj.60_flames_of_zamorak",
            attack =
                godSpell(manager, impact = "spotanim.zamorak_flame", sound = "synth.FLAMES_OF_ZAMORAK") { target ->
                    SpellEffects.drainPercent(target, SpellEffects.MAGIC, GOD_SPELL_DRAIN_PERCENT)
                },
        )
    }

    private fun godSpell(
        manager: SpellAttackManager,
        impact: String,
        sound: String,
        effect: (target: org.rsmod.game.entity.PathingEntity) -> Unit,
    ): EffectSpellAttack =
        EffectSpellAttack(
            manager = manager,
            staffAnim = "seq.human_casting",
            launch = null,
            travel = null,
            impact = impact,
            impactHeight = 0,
            castSound = sound,
            hitSound = null,
            baseMaxHit = { GOD_SPELL_MAX_HIT },
            onLand = { target, damage -> if (damage > 0) effect(target) },
        )

    private companion object {
        private const val CRUMBLE_UNDEAD_MAX_HIT = 15
        private const val IBAN_BLAST_MAX_HIT = 25
        private const val MAGIC_DART_BASE = 10
        private const val GOD_SPELL_MAX_HIT = 20
        private const val GOD_SPELL_DRAIN_PERCENT = 5
        private const val SARADOMIN_PRAYER_DRAIN = 1
    }
}
