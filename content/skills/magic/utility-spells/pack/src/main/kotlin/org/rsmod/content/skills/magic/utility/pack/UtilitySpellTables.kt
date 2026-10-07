package org.rsmod.content.skills.magic.utility.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

internal fun utilitySpellRules() = dbTable("dbtable.utility_spell_rules", serverOnly = true) {
    column("spell", 0, VarType.OBJ)
    column("method", 1, VarType.STRING)
    column("batch", 2, VarType.INT)
    column("cooldown", 3, VarType.INT)
    column("sequence", 4, VarType.SEQ)
    column("spotanim", 5, VarType.SPOTANIM)
    column("sound", 6, VarType.SYNTH)
    column("repeat_delay", 7, VarType.INT)
    column("missing_message", 8, VarType.STRING)
    column("obelisk", 9, VarType.LOC)
    column("orb", 10, VarType.OBJ)
    for (rule in rules) {
        row("dbrow.utility_spell_${rule.key}") {
            columnRSCM(0, rule.spell)
            column(1, rule.method)
            column(2, rule.batch)
            column(3, rule.cooldown)
            columnRSCM(4, rule.sequence)
            columnRSCM(5, rule.spotanim)
            rule.sound?.let { columnRSCM(6, it) }
            column(7, rule.repeatDelay)
            column(8, rule.missingMessage)
            rule.obelisk?.let { columnRSCM(9, it) }
            rule.orb?.let { columnRSCM(10, it) }
        }
    }
}

internal fun utilitySpellConversions() = dbTable("dbtable.utility_spell_conversions", serverOnly = true) {
    column("method", 0, VarType.STRING)
    column("input", 1, VarType.OBJ)
    column("output", 2, VarType.OBJ)
    column("crafting_xp_tenths", 3, VarType.INT)
    for ((method, input, output, xp) in conversions) {
        row("dbrow.utility_conversion_${input.removePrefix("obj.")}") {
            column(0, method)
            columnRSCM(1, input)
            columnRSCM(2, output)
            column(3, xp)
        }
    }
}

internal fun utilityOfferingBonuses() = dbTable("dbtable.utility_offering_bonuses", serverOnly = true) {
    column("item", 0, VarType.OBJ)
    column("prayer_level", 1, VarType.INT)
    column("prayer_restore", 2, VarType.INT)
    for (item in listOf("zogre_ancestral_bones_ourg", "dagannoth_king_bones", "hydra_bones", "dragon_bones_superior", "infernal_ashes")) {
        row("dbrow.utility_offering_$item") {
            columnRSCM(0, "obj.$item")
            column(1, if (item == "dragon_bones_superior") 70 else 1)
            column(2, 2)
        }
    }
}

private data class Rule(
    val key: String,
    val spell: String,
    val method: String,
    val batch: Int,
    val cooldown: Int,
    val sequence: String,
    val spotanim: String,
    val sound: String? = null,
    val repeatDelay: Int = 0,
    val missingMessage: String = "You aren't carrying anything suitable for this spell.",
    val obelisk: String? = null,
    val orb: String? = null,
)

private val rules = listOf(
    Rule("humidify", "obj.68_humidify", "Humidify", 28, 3,
        "seq.dream_player_humidify_spell", "spotanim.dream_humidify_spell_spotanim",
        "synth.LUNAR_HUMIDIFY"),
    Rule("spin_flax", "obj.76_spin_flax", "SpinFlax", 5, 5,
        "seq.lunar_human_magic_summon1", "spotanim.quest_lunar_magic_flax_strim",
        "synth.LUNAR_FLAX_TO_BOWSTRING", missingMessage = "You don't have any flax to spin."),
    Rule("string_jewellery", "obj.80_string_jewelry", "StringJewellery", 1, 3,
        "seq.lunar_human_magic_summon1", "spotanim.quest_lunar_spellbook_string_jewelery_spot_anim",
        repeatDelay = 3),
    Rule("bake_pie", "obj.65_bake_pie", "BakePie", 1, 5,
        "seq.lunar_human_magic_summon1", "spotanim.quest_lunar_spellbook_bake_pie_spot_anim",
        repeatDelay = 5),
    Rule("tan_leather", "obj.leather", "TanLeather", 5, 3,
        "seq.tan_leather", "spotanim.lunar_tan_leather"),
    Rule("plank_make", "obj.86_plank_make", "PlankMake", 1, 3,
        "seq.dream_player_make_plank_spell", "spotanim.dream_plank_make_spell_spotanim",
        "synth.LUNAR_MAKE_PLANK", repeatDelay = 6),
    Rule("degrime", "obj.placeholder_skillpethunter_black", "Degrime", 28, 3,
        "seq.human_spellcast_degrime", "spotanim.degrime_cast_spotanim", "synth.DEGRIME_CAST"),
    Rule("sinister_offering", "obj.poh_temp_limestonebrick", "SinisterOffering", 3, 9,
        "seq.human_cast_offering", "spotanim.sinister_offering_cast_spotanim", "synth.SINISTER_OFFERING_CAST"),
    Rule("demonic_offering", "obj.placeholder_skillpethunter_gold", "DemonicOffering", 3, 9,
        "seq.human_cast_offering", "spotanim.demonic_offering_cast_spotanim", "synth.DEMONIC_OFFERING_CAST"),
    Rule("cure_me", "obj.71_cure_me", "CureMe", 0, 1,
        "seq.lunar_human_magic_summon1", "spotanim.quest_lunar_spellbook_cure_poison_spot_anim"),
    Rule("vile_vigour", "obj.placeholder_snakepet_blue", "VileVigour", 0, 17,
        "seq.human_cast_vilevigour", "spotanim.vile_vigour_cast_spotanim", "synth.VILE_VIGOUR_CAST"),
    Rule("shadow_veil", "obj.placeholder_skillpethunter_grey", "ShadowVeil", 0, 50,
        "seq.human_spellcast_shadowveil", "spotanim.shadow_veil_cast_spotanim", "synth.SHADOW_VEIL_CAST"),
    Rule("mark_of_darkness", "obj.br_bass", "MarkOfDarkness", 0, 10,
        "seq.mark_of_darkness", "spotanim.mark_of_darkness_cast_spotanim", "synth.MARK_OF_DARKNESS_CAST"),
    Rule("charge", "obj.80_charge", "Charge", 0, 100,
        "seq.human_casting", "spotanim.acb_specialattack", "synth.CHARGE"),
    Rule("charge_water_orb", "obj.56_charge_water_orb", "ChargeOrb", 1, 3,
        "seq.human_castchargeorb", "spotanim.chargewaterorb_casting", "synth.CHARGE_WATER_ORB",
        obelisk = "loc.obelisk_water", orb = "obj.water_orb"),
    Rule("charge_earth_orb", "obj.60_charge_earth_orb", "ChargeOrb", 1, 3,
        "seq.human_castchargeorb", "spotanim.chargeearthorb_casting", "synth.CHARGE_EARTH_ORB",
        obelisk = "loc.obelisk_earth", orb = "obj.earth_orb"),
    Rule("charge_fire_orb", "obj.63_charge_fire_orb", "ChargeOrb", 1, 3,
        "seq.human_castchargeorb", "spotanim.chargefireorb_casting", "synth.CHARGE_FIRE_ORB",
        obelisk = "loc.obelisk_fire", orb = "obj.fire_orb"),
    Rule("charge_air_orb", "obj.66_charge_air_orb", "ChargeOrb", 1, 3,
        "seq.human_castchargeorb", "spotanim.chargewindorb_casting", "synth.CHARGE_AIR_ORB",
        obelisk = "loc.obelisk_air", orb = "obj.air_orb"),
)

private data class Conversion(val method: String, val input: String, val output: String, val xp: Int = 0)

private val conversions = buildList {
    add(Conversion("SpinFlax", "obj.flax", "obj.bow_string", 150))
    for (vessel in listOf("jug", "bucket", "vial", "bowl", "cup", "fishbowl")) {
        add(Conversion("Humidify", "obj.${vessel}_empty", "obj.${vessel}_water"))
    }
    for (dose in 0..7) add(Conversion("Humidify", "obj.watering_can_$dose", "obj.watering_can_8"))
    for (dose in 0..3) add(Conversion("Humidify", "obj.water_skin$dose", "obj.water_skin4"))
    add(Conversion("StringJewellery", "obj.unstrung_zenyte_amulet", "obj.zenyte_amulet", 40))
    for (gem in listOf("gold", "sapphire", "emerald", "ruby", "diamond", "dragonstone", "onyx", "opal", "jade", "topaz")) {
        add(Conversion("StringJewellery", "obj.unstrung_${gem}_amulet", "obj.strung_${gem}_amulet", 40))
    }
}
