package org.rsmod.content.skills.magic.utility.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

internal fun jewelleryEnchantTiers() = dbTable("dbtable.jewellery_enchant_tiers", serverOnly = true) {
    column("tier_order", 0, VarType.INT)
    column("tier", 1, VarType.STRING)
    column("spell", 2, VarType.OBJ)
    column("amulet_seq", 3, VarType.SEQ)
    column("amulet_spotanim", 4, VarType.SPOTANIM)
    column("amulet_sound", 5, VarType.SYNTH)
    column("ring_sound", 6, VarType.SYNTH)
    for ((order, tier) in EnchantTier.entries.withIndex()) {
        row("dbrow.jewellery_enchant_tier_${order + 1}") {
            column(0, order)
            column(1, tier.name)
            columnRSCM(2, tier.spellObj)
            columnRSCM(3, tier.amuletSeq)
            columnRSCM(4, tier.amuletSpotanim)
            columnRSCM(5, tier.amuletSound)
            columnRSCM(6, tier.ringSound)
        }
    }
}

internal fun jewelleryEnchantProducts() = dbTable("dbtable.jewellery_enchant_products", serverOnly = true) {
    column("tier", 0, VarType.STRING)
    column("product_order", 1, VarType.INT)
    column("input", 2, VarType.OBJ)
    column("product", 3, VarType.OBJ)
    column("shape", 4, VarType.STRING)
    for (tier in EnchantTier.entries) {
        for ((order, product) in tier.rows.withIndex()) {
            row("dbrow.jewellery_enchant_${product.input.removePrefix("obj.")}") {
                column(0, tier.name)
                column(1, order)
                columnRSCM(2, product.input)
                columnRSCM(3, product.product)
                column(4, product.shape.name)
            }
        }
    }
}

// TODO(quest): implement silvthrill rod, pre-nature amulet and sickle enchants with their quests.
private enum class EnchantJewelleryShape {
    Ring,
    Necklace,
    Amulet,
    Bracelet,
}

private data class EnchantJewelleryRow(
    val input: String,
    val product: String,
    val shape: EnchantJewelleryShape,
)

// TODO: verify fallback high-tier enchanting presentation where dedicated assets are absent.
private enum class EnchantTier(
    val spellObj: String,
    val amuletSeq: String,
    val amuletSpotanim: String,
    val amuletSound: String,
    val ringSound: String,
    val rows: List<EnchantJewelleryRow>,
) {
    Lvl1(
        spellObj = "obj.07_enchant_amulet_lvl1",
        amuletSeq = "seq.human_enchantamuletlvl1",
        amuletSpotanim = "spotanim.enchant_amulet_lvl1",
        amuletSound = "synth.ENCHANT_SAPPHIRE_AMULET",
        ringSound = "synth.ENCHANT_SAPPHIRE_RING",
        rows =
            listOf(
                row("obj.sapphire_ring", "obj.ring_of_recoil", EnchantJewelleryShape.Ring),
                row(
                    "obj.sapphire_necklace",
                    "obj.necklace_of_minigames_8",
                    EnchantJewelleryShape.Necklace,
                ),
                row(
                    "obj.strung_sapphire_amulet",
                    "obj.amulet_of_magic",
                    EnchantJewelleryShape.Amulet,
                ),
                row(
                    "obj.jewl_sapphire_bracelet",
                    "obj.jewl_bracelet_of_clay",
                    EnchantJewelleryShape.Bracelet,
                ),
                row("obj.opal_ring", "obj.ring_of_pursuit", EnchantJewelleryShape.Ring),
                row("obj.opal_necklace", "obj.dodgy_necklace", EnchantJewelleryShape.Necklace),
                row("obj.strung_opal_amulet", "obj.amulet_of_bounty", EnchantJewelleryShape.Amulet),
                row(
                    "obj.opal_bracelet",
                    "obj.expeditious_bracelet",
                    EnchantJewelleryShape.Bracelet,
                ),
            ),
    ),
    Lvl2(
        spellObj = "obj.27_enchant_amulet_lvl2",
        amuletSeq = "seq.human_enchantamuletlvl2",
        amuletSpotanim = "spotanim.enchant_amulet_lvl2",
        amuletSound = "synth.ENCHANT_EMERALD_AMULET",
        ringSound = "synth.ENCHANT_EMERALD_RING",
        rows =
            listOf(
                row("obj.emerald_ring", "obj.ring_of_dueling_8", EnchantJewelleryShape.Ring),
                row(
                    "obj.emerald_necklace",
                    "obj.magic_emerald_necklace",
                    EnchantJewelleryShape.Necklace,
                ),
                row(
                    "obj.strung_emerald_amulet",
                    "obj.amulet_of_defence",
                    EnchantJewelleryShape.Amulet,
                ),
                row(
                    "obj.jewl_emerald_bracelet",
                    "obj.jewl_castlewars_bracelet3",
                    EnchantJewelleryShape.Bracelet,
                ),
                row("obj.jade_ring", "obj.ring_of_returning_5", EnchantJewelleryShape.Ring),
                row(
                    "obj.jade_necklace",
                    "obj.necklace_of_passage_5",
                    EnchantJewelleryShape.Necklace,
                ),
                row(
                    "obj.strung_jade_amulet",
                    "obj.amulet_of_chemistry",
                    EnchantJewelleryShape.Amulet,
                ),
                row("obj.jade_bracelet", "obj.flamtaer_bracelet", EnchantJewelleryShape.Bracelet),
            ),
    ),
    Lvl3(
        spellObj = "obj.49_enchant_amulet_lvl3",
        amuletSeq = "seq.human_enchantamuletlvl3",
        amuletSpotanim = "spotanim.enchant_amulet_lvl3",
        amuletSound = "synth.ENCHANT_RUBY_AMULET",
        ringSound = "synth.ENCHANT_RUBY_RING",
        rows =
            listOf(
                row("obj.ruby_ring", "obj.ring_of_forging", EnchantJewelleryShape.Ring),
                // TODO(unlock): require The Dig Site and the Museum clean-necklace event for ruby necklaces.
                row(
                    "obj.ruby_necklace",
                    "obj.necklace_of_digsite_5",
                    EnchantJewelleryShape.Necklace,
                ),
                row(
                    "obj.strung_ruby_amulet",
                    "obj.amulet_of_strength",
                    EnchantJewelleryShape.Amulet,
                ),
                row(
                    "obj.jewl_ruby_bracelet",
                    "obj.jewl_bracelet_of_innoculation",
                    EnchantJewelleryShape.Bracelet,
                ),
                row("obj.topaz_ring", "obj.vampyre_ring", EnchantJewelleryShape.Ring),
                row(
                    "obj.topaz_necklace",
                    "obj.necklace_of_faith",
                    EnchantJewelleryShape.Necklace,
                ),
                row(
                    "obj.strung_topaz_amulet",
                    "obj.burning_amulet_5",
                    EnchantJewelleryShape.Amulet,
                ),
                row(
                    "obj.topaz_bracelet",
                    "obj.bracelet_of_slaughter",
                    EnchantJewelleryShape.Bracelet,
                ),
            ),
    ),
    Lvl4(
        spellObj = "obj.57_enchant_amulet_lvl4",
        amuletSeq = "seq.human_enchantamuletlvl2",
        amuletSpotanim = "spotanim.enchant_amulet2_lvl4",
        amuletSound = "synth.ENCHANT_DIAMOND_AMULET",
        ringSound = "synth.ENCHANT_DIAMOND_RING",
        rows =
            listOf(
                row("obj.diamond_ring", "obj.ring_of_life", EnchantJewelleryShape.Ring),
                row(
                    "obj.diamond_necklace",
                    "obj.jewl_necklace_of_phoenix",
                    EnchantJewelleryShape.Necklace,
                ),
                row(
                    "obj.strung_diamond_amulet",
                    "obj.amulet_of_power",
                    EnchantJewelleryShape.Amulet,
                ),
                row(
                    "obj.jewl_diamond_bracelet",
                    "obj.jewl_runerunning_bracelet_5",
                    EnchantJewelleryShape.Bracelet,
                ),
            ),
    ),
    Lvl5(
        spellObj = "obj.68_enchant_amulet_lvl5",
        amuletSeq = "seq.human_enchantamuletlvl3",
        amuletSpotanim = "spotanim.enchant_amulet2_lvl5",
        amuletSound = "synth.ENCHANT_DRAGON_AMULET",
        ringSound = "synth.ENCHANT_DRAGON_RING",
        rows =
            listOf(
                row("obj.dragonstone_ring", "obj.ring_of_wealth", EnchantJewelleryShape.Ring),
                row(
                    "obj.dragonstone_necklace",
                    "obj.jewl_necklace_of_skills",
                    EnchantJewelleryShape.Necklace,
                ),
                row(
                    "obj.strung_dragonstone_amulet",
                    "obj.amulet_of_glory",
                    EnchantJewelleryShape.Amulet,
                ),
                row(
                    "obj.jewl_dragonstone_bracelet",
                    "obj.jewl_bracelet_of_combat",
                    EnchantJewelleryShape.Bracelet,
                ),
            ),
    ),
    Lvl6(
        spellObj = "obj.87_enchant_amulet_lvl6",
        amuletSeq = "seq.human_enchantamuletlvl3",
        amuletSpotanim = "spotanim.enchant_amulet2_lvl6",
        amuletSound = "synth.ENCHANT_ONYX_AMULET",
        ringSound = "synth.ENCHANT_ONYX_RING",
        rows =
            listOf(
                row("obj.onyx_ring", "obj.enchanted_onyx_ring", EnchantJewelleryShape.Ring),
                row(
                    "obj.onyx_necklace",
                    "obj.jewl_beserker_necklace",
                    EnchantJewelleryShape.Necklace,
                ),
                row(
                    "obj.strung_onyx_amulet",
                    "obj.enchanted_onyx_amulet",
                    EnchantJewelleryShape.Amulet,
                ),
                row(
                    "obj.jewl_onyx_bracelet",
                    "obj.jewl_bracelet_regen",
                    EnchantJewelleryShape.Bracelet,
                ),
            ),
    ),
    Lvl7(
        spellObj = "obj.93_enchant_amulet_lvl7",
        amuletSeq = "seq.human_enchantamuletlvl3",
        amuletSpotanim = "spotanim.enchant_amulet2_lvl6",
        amuletSound = "synth.ENCHANT_ONYX_AMULET",
        ringSound = "synth.ENCHANT_ONYX_RING",
        rows =
            listOf(
                row("obj.zenyte_ring", "obj.zenyte_ring_enchanted", EnchantJewelleryShape.Ring),
                row(
                    "obj.zenyte_necklace",
                    "obj.zenyte_necklace_enchanted",
                    EnchantJewelleryShape.Necklace,
                ),
                row(
                    "obj.zenyte_amulet",
                    "obj.zenyte_amulet_enchanted",
                    EnchantJewelleryShape.Amulet,
                ),
                row(
                    "obj.zenyte_bracelet",
                    "obj.zenyte_bracelet_enchanted",
                    EnchantJewelleryShape.Bracelet,
                ),
            ),
    )
}

private fun row(
    input: String,
    product: String,
    shape: EnchantJewelleryShape,
): EnchantJewelleryRow = EnchantJewelleryRow(input, product, shape)
