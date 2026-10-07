package org.rsmod.content.minigames.tempoross.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

internal fun temporossFishBands() = dbTable("dbtable.tempoross_fish_bands", serverOnly = true) {
    column("level", 0, VarType.INT)
    column("fish", 1, VarType.OBJ)
    val fish = listOf(
        "herring", "mackerel", "pike", "salmon", "tuna", "lobster", "bass",
        "swordfish", "shark", "seaturtle", "mantaray",
    )
    for ((start, level) in listOf(35, 40, 46, 50, 76, 79, 81).withIndex()) {
        row("dbrow.tempoross_fish_band_$level") {
            column(0, level)
            columnRSCM(1, *fish.subList(start, start + 5).map { "obj.raw_$it" }.toTypedArray())
        }
    }
}

internal fun temporossFishSlots() = dbTable("dbtable.tempoross_fish_slots", serverOnly = true) {
    column("roll_order", 0, VarType.INT)
    column("minimum", 1, VarType.INT)
    column("maximum", 2, VarType.INT)
    column("weight", 3, VarType.INT)
    val slots = listOf(30..40 to 20, 20..30 to 18, 10..20 to 16, 7..12 to 14, 5..10 to 12)
    for ((order, slot) in slots.withIndex()) {
        row("dbrow.tempoross_fish_slot_$order") {
            column(0, order)
            column(1, slot.first.first)
            column(2, slot.first.last)
            column(3, slot.second)
        }
    }
}

internal fun temporossCasketRewards() = dbTable("dbtable.tempoross_casket_rewards", serverOnly = true) {
    column("roll_order", 0, VarType.INT)
    column("reward", 1, VarType.OBJ)
    column("minimum", 2, VarType.INT)
    column("maximum", 3, VarType.INT)
    column("weight", 4, VarType.INT)
    column("noted", 5, VarType.BOOLEAN)
    val rewards = listOf(
        CasketEntry("rune_med_helm", 1..1, 9),
        CasketEntry("rune_full_helm", 1..1, 9),
        CasketEntry("rune_chainbody", 1..1, 9),
        CasketEntry("rune_platebody", 2..3, 1),
        CasketEntry("rune_platelegs", 2..3, 1),
        CasketEntry("rune_plateskirt", 2..3, 1),
        CasketEntry("rune_sq_shield", 3..5, 1),
        CasketEntry("rune_kiteshield", 3..5, 1),
        CasketEntry("sapphire_ring", 10..14, 9),
        CasketEntry("sapphire_ring", 20..28, 1),
        CasketEntry("emerald_ring", 7..11, 9),
        CasketEntry("emerald_ring", 14..22, 1),
        CasketEntry("ruby_ring", 6..8, 9),
        CasketEntry("ruby_ring", 12..16, 1),
        CasketEntry("diamond_ring", 3..4, 9),
        CasketEntry("diamond_ring", 6..8, 1),
        CasketEntry("sapphire_necklace", 10..14, 9),
        CasketEntry("sapphire_necklace", 20..28, 1),
        CasketEntry("emerald_necklace", 7..11, 9),
        CasketEntry("emerald_necklace", 14..22, 1),
        CasketEntry("ruby_necklace", 6..8, 9),
        CasketEntry("ruby_necklace", 12..16, 1),
        CasketEntry("diamond_necklace", 3..4, 9),
        CasketEntry("diamond_necklace", 4..8, 1),
        CasketEntry("jewl_sapphire_bracelet", 10..14, 9),
        CasketEntry("jewl_sapphire_bracelet", 20..28, 1),
        CasketEntry("jewl_emerald_bracelet", 7..11, 9),
        CasketEntry("jewl_emerald_bracelet", 14..22, 1),
        CasketEntry("jewl_ruby_bracelet", 6..8, 9),
        CasketEntry("jewl_ruby_bracelet", 12..16, 1),
        CasketEntry("jewl_diamond_bracelet", 3..4, 9),
        CasketEntry("jewl_diamond_bracelet", 3..8, 1),
        CasketEntry("silver_bar", 50..100, 9),
        CasketEntry("gold_bar", 50..100, 9),
        CasketEntry("uncut_opal", 25..50, 9),
        CasketEntry("uncut_jade", 25..50, 9),
        CasketEntry("uncut_red_topaz", 10..15, 9),
        CasketEntry("uncut_sapphire", 20..30, 1),
        CasketEntry("uncut_emerald", 15..25, 1),
        CasketEntry("uncut_ruby", 10..20, 1),
        CasketEntry("uncut_diamond", 8..13, 1),
        CasketEntry("uncut_dragonstone", 2..3, 1),
        CasketEntry("coins", 4000..6000, 9, noted = false),
        CasketEntry("coins", 25000..50000, 1, noted = false),
        CasketEntry("keyhalf1", 1..1, 9),
        CasketEntry("keyhalf2", 1..1, 9),
    )
    for ((order, reward) in rewards.withIndex()) {
        row("dbrow.tempoross_casket_$order") {
            column(0, order)
            columnRSCM(1, "obj.${reward.obj}")
            column(2, reward.quantity.first)
            column(3, reward.quantity.last)
            column(4, reward.weight)
            column(5, reward.noted)
        }
    }
}

private data class CasketEntry(
    val obj: String,
    val quantity: IntRange,
    val weight: Int,
    val noted: Boolean = true,
)
